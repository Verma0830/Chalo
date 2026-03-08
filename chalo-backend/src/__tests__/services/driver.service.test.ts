// ============================================================
// Tests — Driver Service Unit Tests
// Tests business logic with mocked dependencies
// ============================================================

import { RideStatus } from '@prisma/client';
import { ErrorCode } from '../../utils/apiError';

// Mock dependencies
jest.mock('../../config/database', () => {
  const mockPrisma: Record<string, unknown> = {
    ride: {
      findFirst: jest.fn(),
      findUnique: jest.fn(),
      findMany: jest.fn(),
      update: jest.fn(),
      updateMany: jest.fn(),
      count: jest.fn(),
    },
    driverProfile: {
      findUnique: jest.fn(),
      findFirst: jest.fn(),
      findMany: jest.fn(),
      update: jest.fn(),
    },
    earning: {
      aggregate: jest.fn(),
    },
    rideEvent: {
      create: jest.fn(),
    },
    $transaction: jest.fn(),
  };
  (mockPrisma.$transaction as jest.Mock).mockImplementation(
    (fn: (tx: typeof mockPrisma) => Promise<unknown>) => fn(mockPrisma)
  );
  return {
    __esModule: true,
    default: mockPrisma,
  };
});

jest.mock('../../services/notification.service', () => ({
  notificationService: {
    sendPushNotification: jest.fn().mockResolvedValue(undefined),
  },
}));

jest.mock('../../config/logger', () => ({
  __esModule: true,
  default: {
    info: jest.fn(),
    warn: jest.fn(),
    error: jest.fn(),
    debug: jest.fn(),
  },
}));

jest.mock('../../config/firebase', () => ({
  getDatabase: jest.fn().mockReturnValue({
    ref: jest.fn().mockReturnValue({
      update: jest.fn().mockResolvedValue(undefined),
      remove: jest.fn().mockResolvedValue(undefined),
    }),
  }),
}));

jest.mock('../../config/redis', () => ({
  getRedisClient: jest.fn().mockReturnValue({
    get: jest.fn().mockResolvedValue(null),
    set: jest.fn().mockResolvedValue('OK'),
    del: jest.fn().mockResolvedValue(1),
  }),
  isRedisReady: jest.fn().mockReturnValue(true),
}));

jest.mock('../../utils/metrics', () => ({
  onlineDriversGauge: { inc: jest.fn(), dec: jest.fn(), set: jest.fn() },
  activeRidesGauge: { inc: jest.fn(), dec: jest.fn() },
  ridesCompletedTotal: { inc: jest.fn() },
  ridesCancelledTotal: { inc: jest.fn() },
}));

import prisma from '../../config/database';
import { DriverService } from '../../services/driver.service';

const driverService = new DriverService();

describe('DriverService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  // -------------------------------------------------------
  // rateCustomer
  // -------------------------------------------------------

  describe('rateCustomer', () => {
    it('throws RIDE_NOT_FOUND when ride does not exist', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue(null);

      await expect(
        driverService.rateCustomer('driver_001', 'ride_001', 4)
      ).rejects.toMatchObject({ code: ErrorCode.RIDE_NOT_FOUND });
    });

    it('throws FORBIDDEN when driver does not own the ride', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        driverId: 'other_driver',
        status: RideStatus.COMPLETED,
        driverRating: null,
      });

      await expect(
        driverService.rateCustomer('driver_001', 'ride_001', 5)
      ).rejects.toMatchObject({ code: ErrorCode.FORBIDDEN });
    });

    it('throws INVALID_RIDE_STATUS for a non-completed ride', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        driverId: 'driver_001',
        status: RideStatus.IN_PROGRESS,
        driverRating: null,
      });

      await expect(
        driverService.rateCustomer('driver_001', 'ride_001', 5)
      ).rejects.toMatchObject({ code: 'INVALID_RIDE_STATUS' });
    });

    it('throws ALREADY_RATED when driver already submitted a rating', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        driverId: 'driver_001',
        status: RideStatus.COMPLETED,
        driverRating: 4, // Already rated!
      });

      await expect(
        driverService.rateCustomer('driver_001', 'ride_001', 5)
      ).rejects.toMatchObject({ code: 'ALREADY_RATED' });
    });

    it('saves rating and returns result', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        driverId: 'driver_001',
        status: RideStatus.COMPLETED,
        driverRating: null,
      });
      (prisma.ride.update as jest.Mock).mockResolvedValue({});

      const result = await driverService.rateCustomer('driver_001', 'ride_001', 4, 'Good customer');

      expect(result.rideId).toBe('ride_001');
      expect(result.rating).toBe(4);
      expect(result.comment).toBe('Good customer');
      expect(prisma.ride.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({ driverRating: 4, driverComment: 'Good customer' }),
        })
      );
    });

    it('stores null comment when not provided', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        driverId: 'driver_001',
        status: RideStatus.COMPLETED,
        driverRating: null,
      });
      (prisma.ride.update as jest.Mock).mockResolvedValue({});

      const result = await driverService.rateCustomer('driver_001', 'ride_001', 3);

      expect(result.comment).toBeNull();
      expect(prisma.ride.update).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({ driverRating: 3, driverComment: null }),
        })
      );
    });
  });

  // -------------------------------------------------------
  // startRide — OTP validation
  // -------------------------------------------------------

  describe('startRide (OTP validation)', () => {
    it('throws RIDE_NOT_FOUND when ride does not exist', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue(null);

      await expect(
        driverService.startRide('driver_001', 'ride_001', '1234')
      ).rejects.toMatchObject({ code: ErrorCode.RIDE_NOT_FOUND });
    });

    it('throws FORBIDDEN when driver does not own the ride', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        rideStartOtp: '1234',
        driverId: 'other_driver',
        status: RideStatus.DRIVER_ARRIVED,
      });

      await expect(
        driverService.startRide('driver_001', 'ride_001', '1234')
      ).rejects.toMatchObject({ code: ErrorCode.FORBIDDEN });
    });

    it('throws INVALID_RIDE_STATUS when ride is not in DRIVER_ARRIVED state', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        rideStartOtp: '1234',
        driverId: 'driver_001',
        status: RideStatus.DRIVER_ASSIGNED,
      });

      await expect(
        driverService.startRide('driver_001', 'ride_001', '1234')
      ).rejects.toMatchObject({ code: 'INVALID_RIDE_STATUS' });
    });

    it('throws INVALID_OTP when OTP is wrong', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        rideStartOtp: '1234',
        driverId: 'driver_001',
        status: RideStatus.DRIVER_ARRIVED,
      });

      await expect(
        driverService.startRide('driver_001', 'ride_001', '9999')
      ).rejects.toMatchObject({ code: 'INVALID_OTP' });
    });

    it('throws INVALID_OTP when rideStartOtp is null', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        rideStartOtp: null,
        driverId: 'driver_001',
        status: RideStatus.DRIVER_ARRIVED,
      });

      await expect(
        driverService.startRide('driver_001', 'ride_001', '1234')
      ).rejects.toMatchObject({ code: 'INVALID_OTP' });
    });

    it('starts ride and clears OTP when correct OTP is provided', async () => {
      // First call: pre-check
      (prisma.ride.findUnique as jest.Mock)
        .mockResolvedValueOnce({
          rideStartOtp: '5678',
          driverId: 'driver_001',
          status: RideStatus.DRIVER_ARRIVED,
        })
        // Second call: fetch for notification
        .mockResolvedValueOnce({
          customerId: 'customer_001',
          dropAddress: 'Old Faridabad',
        });

      (prisma.ride.updateMany as jest.Mock).mockResolvedValue({ count: 1 });
      (prisma.rideEvent.create as jest.Mock).mockResolvedValue({});

      const result = await driverService.startRide('driver_001', 'ride_001', '5678');

      expect(result.rideId).toBe('ride_001');
      expect(result.status).toBe(RideStatus.IN_PROGRESS);

      // OTP should be cleared
      expect(prisma.ride.updateMany).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({ rideStartOtp: null }),
        })
      );
    });
  });

  // -------------------------------------------------------
  // getEarningsSummary
  // -------------------------------------------------------

  describe('getEarningsSummary', () => {
    it('throws DRIVER_NOT_FOUND when driver profile does not exist', async () => {
      (prisma.driverProfile.findUnique as jest.Mock).mockResolvedValue(null);

      await expect(
        driverService.getEarningsSummary('driver_001', 'week')
      ).rejects.toMatchObject({ code: ErrorCode.DRIVER_NOT_FOUND });
    });

    it('returns week summary with aggregate data', async () => {
      (prisma.driverProfile.findUnique as jest.Mock).mockResolvedValue({
        id: 'dp_001',
        totalEarnings: 5000,
        totalRides: 100,
        ratingAvg: 4.6,
      });

      (prisma.earning.aggregate as jest.Mock).mockResolvedValue({
        _sum: { grossAmount: 1200, commissionAmount: 180, netAmount: 1020 },
        _count: { id: 12 },
        _avg: { netAmount: 85 },
      });

      const result = await driverService.getEarningsSummary('driver_001', 'week');

      expect(result.period).toBe('week');
      expect(result.rides).toBe(12);
      expect(result.grossEarnings).toBe(1200);
      expect(result.netEarnings).toBe(1020);
      expect(result.commission).toBe(180);
      expect(result.avgPerRide).toBe(85);
      expect(result.allTimeRides).toBe(100);
      expect(result.allTimeEarnings).toBe(5000);
      expect(result.ratingAvg).toBe(4.6);
    });

    it('returns month summary', async () => {
      (prisma.driverProfile.findUnique as jest.Mock).mockResolvedValue({
        id: 'dp_001',
        totalEarnings: 5000,
        totalRides: 100,
        ratingAvg: 4.6,
      });

      (prisma.earning.aggregate as jest.Mock).mockResolvedValue({
        _sum: { grossAmount: 4800, commissionAmount: 720, netAmount: 4080 },
        _count: { id: 48 },
        _avg: { netAmount: 85 },
      });

      const result = await driverService.getEarningsSummary('driver_001', 'month');

      expect(result.period).toBe('month');
      expect(result.rides).toBe(48);
      expect(result.netEarnings).toBe(4080);
    });

    it('returns zeros when no earnings in the period', async () => {
      (prisma.driverProfile.findUnique as jest.Mock).mockResolvedValue({
        id: 'dp_001',
        totalEarnings: 0,
        totalRides: 0,
        ratingAvg: 0,
      });

      (prisma.earning.aggregate as jest.Mock).mockResolvedValue({
        _sum: { grossAmount: null, commissionAmount: null, netAmount: null },
        _count: { id: 0 },
        _avg: { netAmount: null },
      });

      const result = await driverService.getEarningsSummary('driver_001', 'week');

      expect(result.rides).toBe(0);
      expect(result.grossEarnings).toBe(0);
      expect(result.netEarnings).toBe(0);
      expect(result.avgPerRide).toBe(0);
    });
  });
});
