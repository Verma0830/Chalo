// ============================================================
// Tests — Ride Service Unit Tests
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
      create: jest.fn(),
      update: jest.fn(),
      count: jest.fn(),
    },
    driverProfile: {
      findMany: jest.fn(),
      findFirst: jest.fn(),
      update: jest.fn(),
    },
    customerProfile: {
      update: jest.fn(),
    },
    platformConfig: {
      findUnique: jest.fn().mockResolvedValue(null),
    },
    $transaction: jest.fn(),
  };
  // Transaction callback receives the same mock so per-test mockResolvedValue works inside tx
  (mockPrisma.$transaction as jest.Mock).mockImplementation((fn: (tx: typeof mockPrisma) => Promise<unknown>) => fn(mockPrisma));
  return {
    __esModule: true,
    default: mockPrisma,
    prisma: {
      $queryRaw: jest.fn(),
    },
  };
});

jest.mock('../../services/fare.service', () => ({
  fareService: {
    estimateFare: jest.fn().mockResolvedValue({
      baseFare: 50,
      surgeMultiplier: 1.0,
      totalFare: 55,
      distanceKm: 3.5,
      durationMins: 10,
    }),
  },
}));

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
  },
}));

jest.mock('../../utils/metrics', () => ({
  ridesCreatedTotal: { inc: jest.fn() },
  ridesCancelledTotal: { inc: jest.fn() },
  driverSearchDuration: { observe: jest.fn() },
}));

jest.mock('../../config/firebase', () => ({
  getDatabase: jest.fn().mockReturnValue({
    ref: jest.fn().mockReturnValue({
      update: jest.fn().mockResolvedValue(undefined),
    }),
  }),
}));

import prisma, { prisma as dbPrisma } from '../../config/database';
import { RideService } from '../../services/ride.service';

const rideService = new RideService();

describe('RideService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  describe('createRide', () => {
    const validPickup = { lat: 28.4089, lng: 77.3178, address: 'Sector 14, Faridabad' };
    const validDrop = { lat: 28.4506, lng: 77.3150, address: 'Old Faridabad' };
    const customerId = 'cuid_customer_001';

    it('throws VALIDATION_ERROR when pickup is missing lat', async () => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const invalidPickup = { lat: undefined as any, lng: 77.3178, address: 'Test' };

      await expect(
        rideService.createRide(customerId, invalidPickup, validDrop, 'CASH')
      ).rejects.toMatchObject({ code: ErrorCode.VALIDATION_ERROR });
    });

    it('throws RIDE_ALREADY_ACTIVE when customer has active ride', async () => {
      (prisma.ride.findFirst as jest.Mock).mockResolvedValue({
        id: 'existing_ride',
        status: RideStatus.IN_PROGRESS,
      });

      await expect(
        rideService.createRide(customerId, validPickup, validDrop, 'CASH')
      ).rejects.toMatchObject({
        code: ErrorCode.RIDE_ALREADY_ACTIVE,
        message: expect.stringContaining('already have an active ride'),
      });
    });
  });

  describe('getRideDetails', () => {
    it('throws RIDE_NOT_FOUND when ride does not exist', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue(null);

      await expect(
        rideService.getRideDetails('nonexistent', 'customer_001')
      ).rejects.toMatchObject({ code: ErrorCode.RIDE_NOT_FOUND });
    });

    it('throws FORBIDDEN when accessing another customers ride', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        id: 'ride_001',
        customerId: 'other_customer',
        pickupLat: 28.4,
        pickupLng: 77.3,
        pickupAddress: 'Test',
        dropLat: 28.5,
        dropLng: 77.4,
        dropAddress: 'Drop',
        status: RideStatus.COMPLETED,
      });

      await expect(
        rideService.getRideDetails('ride_001', 'wrong_customer')
      ).rejects.toMatchObject({ code: ErrorCode.FORBIDDEN });
    });
  });

  describe('cancelRide', () => {
    it('throws RIDE_CANNOT_CANCEL for completed rides', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        id: 'ride_001',
        customerId: 'customer_001',
        status: RideStatus.COMPLETED,
      });

      await expect(
        rideService.cancelRide('ride_001', 'customer_001', 'Changed my mind')
      ).rejects.toMatchObject({ code: ErrorCode.RIDE_CANNOT_CANCEL });
    });

    it('throws RIDE_NOT_FOUND for nonexistent ride', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue(null);

      await expect(
        rideService.cancelRide('nonexistent', 'customer_001')
      ).rejects.toMatchObject({ code: ErrorCode.RIDE_NOT_FOUND });
    });
  });

  describe('rateRide', () => {
    it('throws RIDE_NOT_COMPLETED for non-completed rides', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        id: 'ride_001',
        customerId: 'customer_001',
        status: RideStatus.IN_PROGRESS,
        customerRating: null,
      });

      await expect(
        rideService.rateRide('ride_001', 'customer_001', 5)
      ).rejects.toMatchObject({ code: ErrorCode.RIDE_NOT_COMPLETED });
    });

    it('throws RIDE_ALREADY_RATED when ride was already rated', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        id: 'ride_001',
        customerId: 'customer_001',
        status: RideStatus.COMPLETED,
        customerRating: 4, // Already rated!
      });

      await expect(
        rideService.rateRide('ride_001', 'customer_001', 5)
      ).rejects.toMatchObject({ code: ErrorCode.RIDE_ALREADY_RATED });
    });

    it('throws VALIDATION_ERROR for rating outside 1-5', async () => {
      await expect(
        rideService.rateRide('ride_001', 'customer_001', 6)
      ).rejects.toMatchObject({ code: ErrorCode.VALIDATION_ERROR });
    });
  });

  describe('getRideLocation', () => {
    it('returns null location when driver profile has no coordinates', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        id: 'ride_001',
        customerId: 'customer_001',
        status: RideStatus.IN_PROGRESS,
        pickupLat: 28.4,
        pickupLng: 77.3,
        dropLat: 28.5,
        dropLng: 77.4,
        driver: {
          driverProfile: {
            currentLat: null,
            currentLng: null,
            lastLocationUpdate: null,
          },
        },
      });

      const result = await rideService.getRideLocation('ride_001', 'customer_001');

      expect(result.location).toBeNull();
      expect(result.message).toBe('Driver location not available');
    });
  });

  // -------------------------------------------------------
  // Happy-path Tests (P2 — success flows)
  // -------------------------------------------------------

  describe('createRide (success)', () => {
    const validPickup = { lat: 28.4089, lng: 77.3178, address: 'Sector 14, Faridabad' };
    const validDrop = { lat: 28.4506, lng: 77.3150, address: 'Old Faridabad' };
    const customerId = 'cuid_customer_001';

    it('creates a ride successfully when no active ride exists', async () => {
      // No active ride
      (prisma.ride.findFirst as jest.Mock).mockResolvedValue(null);

      // ride.create returns the new ride
      (prisma.ride.create as jest.Mock).mockResolvedValue({
        id: 'ride_new_001',
        status: RideStatus.REQUESTED,
        customerId,
      });

      // Mock $queryRaw for driver search (fire-and-forget — returns empty)
      (dbPrisma.$queryRaw as jest.Mock).mockResolvedValue([]);

      const result = await rideService.createRide(customerId, validPickup, validDrop, 'CASH');

      expect(result.rideId).toBe('ride_new_001');
      expect(result.status).toBe(RideStatus.REQUESTED);
      expect(result.fare.totalFare).toBe(55);
      expect(result.pickup.address).toBe('Sector 14, Faridabad');
      expect(result.paymentMethod).toBe('CASH');
    });
  });

  describe('getRideDetails (success)', () => {
    it('returns ride details for the owning customer', async () => {
      const mockRide = {
        id: 'ride_001',
        customerId: 'customer_001',
        pickupLat: 28.4,
        pickupLng: 77.3,
        pickupAddress: 'Pickup',
        dropLat: 28.5,
        dropLng: 77.4,
        dropAddress: 'Drop',
        baseFare: 50,
        surgeMultiplier: 1.0,
        finalFare: 55,
        paymentMethod: 'CASH',
        paymentStatus: 'PENDING',
        distanceKm: 3.5,
        durationMins: 10,
        isScheduled: false,
        scheduledAt: null,
        customerRating: null,
        status: RideStatus.IN_PROGRESS,
        requestedAt: new Date(),
        driverAssignedAt: new Date(),
        driverArrivedAt: null,
        startedAt: new Date(),
        completedAt: null,
        cancelledAt: null,
        driver: {
          id: 'driver_001',
          name: 'Ravi',
          phone: '+919999999999',
          driverProfile: {
            vehicleNumber: 'HR26AB1234',
            vehicleModel: 'Honda Activa',
            ratingAvg: 4.5,
            bikePhotoUrl: null,
          },
        },
      };

      (prisma.ride.findUnique as jest.Mock).mockResolvedValue(mockRide);

      const result = await rideService.getRideDetails('ride_001', 'customer_001');

      expect(result.id).toBe('ride_001');
      expect(result.status).toBe(RideStatus.IN_PROGRESS);
      expect(result.driver?.name).toBe('Ravi');
      expect(result.fare.finalFare).toBe(55);
    });
  });

  describe('getRideHistory (success)', () => {
    it('returns paginated ride history', async () => {
      const mockRides = [
        {
          id: 'ride_001',
          pickupAddress: 'A',
          dropAddress: 'B',
          finalFare: 55,
          status: RideStatus.COMPLETED,
          paymentMethod: 'CASH',
          distanceKm: 3.5,
          customerRating: 5,
          createdAt: new Date(),
          completedAt: new Date(),
          driver: { name: 'Ravi', driverProfile: { vehicleNumber: 'HR26AB1234' } },
        },
      ];

      (prisma.ride.findMany as jest.Mock).mockResolvedValue(mockRides);
      (prisma.ride.count as jest.Mock).mockResolvedValue(1);

      const result = await rideService.getRideHistory('customer_001', 1, 20, 'ALL');

      expect(result.rides).toHaveLength(1);
      expect(result.meta.total).toBe(1);
      expect(result.meta.page).toBe(1);
    });
  });

  describe('cancelRide (success)', () => {
    it('cancels a requested ride', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        id: 'ride_001',
        customerId: 'customer_001',
        status: RideStatus.REQUESTED,
        driverId: null,
      });

      (prisma.ride.update as jest.Mock).mockResolvedValue({
        id: 'ride_001',
        status: RideStatus.CANCELLED,
      });

      (prisma.customerProfile.update as jest.Mock).mockResolvedValue({});

      const result = await rideService.cancelRide('ride_001', 'customer_001', 'Changed plans');

      expect(result.rideId).toBe('ride_001');
      expect(result.status).toBe(RideStatus.CANCELLED);
    });
  });

  describe('rateRide (success)', () => {
    it('rates a completed ride', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        id: 'ride_001',
        customerId: 'customer_001',
        driverId: 'driver_001',
        status: RideStatus.COMPLETED,
        customerRating: null,
      });

      (prisma.ride.update as jest.Mock).mockResolvedValue({});
      (prisma.driverProfile.findFirst as jest.Mock).mockResolvedValue({
        id: 'dp_001',
        ratingAvg: 4.0,
        ratingCount: 10,
      });
      (prisma.driverProfile.update as jest.Mock).mockResolvedValue({});

      const result = await rideService.rateRide('ride_001', 'customer_001', 5, 'Great ride!');

      expect(result.rideId).toBe('ride_001');
      expect(result.rating).toBe(5);
      expect(result.comment).toBe('Great ride!');
    });
  });

  // -------------------------------------------------------
  // getRideReceipt
  // -------------------------------------------------------

  describe('getRideReceipt', () => {
    const completedRide = {
      id: 'ride_001',
      customerId: 'customer_001',
      status: RideStatus.COMPLETED,
      pickupAddress: 'Sector 14, Faridabad',
      dropAddress: 'Old Faridabad',
      distanceKm: 4.2,
      durationMins: 18,
      baseFare: 86,
      surgeMultiplier: 1.0,
      finalFare: 91,
      commissionAmount: 13,
      driverEarning: 78,
      paymentMethod: 'CASH',
      paymentStatus: 'PENDING',
      customerRating: 5,
      requestedAt: new Date('2026-03-01T10:00:00Z'),
      startedAt: new Date('2026-03-01T10:10:00Z'),
      completedAt: new Date('2026-03-01T10:28:00Z'),
      driver: {
        name: 'Ravi Kumar',
        driverProfile: {
          vehicleNumber: 'HR26AB1234',
          vehicleModel: 'Honda Activa 6G',
          ratingAvg: 4.7,
        },
      },
    };

    it('throws RIDE_NOT_FOUND when ride does not exist', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue(null);

      await expect(
        rideService.getRideReceipt('nonexistent', 'customer_001')
      ).rejects.toMatchObject({ code: ErrorCode.RIDE_NOT_FOUND });
    });

    it('throws FORBIDDEN when customer does not own the ride', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        ...completedRide,
        customerId: 'other_customer',
      });

      await expect(
        rideService.getRideReceipt('ride_001', 'customer_001')
      ).rejects.toMatchObject({ code: ErrorCode.FORBIDDEN });
    });

    it('throws RIDE_NOT_COMPLETED for an in-progress ride', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        ...completedRide,
        status: RideStatus.IN_PROGRESS,
      });

      await expect(
        rideService.getRideReceipt('ride_001', 'customer_001')
      ).rejects.toMatchObject({ message: expect.stringContaining('completed rides') });
    });

    it('returns full receipt for a completed ride', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue(completedRide);

      const result = await rideService.getRideReceipt('ride_001', 'customer_001');

      expect(result.rideId).toBe('ride_001');
      expect(result.fare.baseFare).toBe(86);
      expect(result.fare.totalFare).toBe(91);
      expect(result.fare.surgeCharge).toBe(0); // surgeMultiplier = 1.0, no surcharge
      expect(result.route.distanceKm).toBe(4.2);
      expect(result.payment.method).toBe('CASH');
      expect(result.driver?.name).toBe('Ravi Kumar');
      expect(result.driver?.vehicleNumber).toBe('HR26AB1234');
    });

    it('calculates surge charge correctly when surgeMultiplier > 1', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        ...completedRide,
        baseFare: 86,
        surgeMultiplier: 1.5,
        finalFare: 129,
      });

      const result = await rideService.getRideReceipt('ride_001', 'customer_001');

      // surgeCharge = finalFare - baseFare = 129 - 86 = 43
      expect(result.fare.surgeCharge).toBe(43);
      expect(result.fare.surgeMultiplier).toBe(1.5);
    });
  });

  // -------------------------------------------------------
  // cancelRide — cancellation fee logic
  // -------------------------------------------------------

  describe('cancelRide (cancellation fee)', () => {
    it('applies cancellation fee when driver is assigned and free window expired', async () => {
      const assignedAt = new Date(Date.now() - 3 * 60 * 1000); // 3 min ago (> 2 min window)

      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        id: 'ride_001',
        customerId: 'customer_001',
        status: RideStatus.DRIVER_ASSIGNED,
        driverId: 'driver_001',
        driverAssignedAt: assignedAt,
      });

      // No platform_config overrides — uses defaults
      (prisma.platformConfig.findUnique as jest.Mock) = jest.fn().mockResolvedValue(null);

      (prisma.ride.update as jest.Mock).mockResolvedValue({
        id: 'ride_001',
        status: RideStatus.CANCELLED,
      });

      (prisma.customerProfile.update as jest.Mock).mockResolvedValue({});

      const result = await rideService.cancelRide('ride_001', 'customer_001', 'Changed my mind');

      expect(result.cancellationFee).toBe(20); // DEFAULT_CANCEL_FEE
    });

    it('waives cancellation fee within the free window', async () => {
      const assignedAt = new Date(Date.now() - 30 * 1000); // 30s ago (< 2 min window)

      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        id: 'ride_001',
        customerId: 'customer_001',
        status: RideStatus.DRIVER_ASSIGNED,
        driverId: 'driver_001',
        driverAssignedAt: assignedAt,
      });

      (prisma.platformConfig.findUnique as jest.Mock) = jest.fn().mockResolvedValue(null);

      (prisma.ride.update as jest.Mock).mockResolvedValue({
        id: 'ride_001',
        status: RideStatus.CANCELLED,
      });

      (prisma.customerProfile.update as jest.Mock).mockResolvedValue({});

      const result = await rideService.cancelRide('ride_001', 'customer_001');

      expect(result.cancellationFee).toBe(0);
    });

    it('waives fee when no driver is assigned (REQUESTED state)', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        id: 'ride_001',
        customerId: 'customer_001',
        status: RideStatus.REQUESTED,
        driverId: null,
        driverAssignedAt: null,
      });

      (prisma.ride.update as jest.Mock).mockResolvedValue({
        id: 'ride_001',
        status: RideStatus.CANCELLED,
      });

      (prisma.customerProfile.update as jest.Mock).mockResolvedValue({});

      const result = await rideService.cancelRide('ride_001', 'customer_001');

      expect(result.cancellationFee).toBe(0);
    });
  });
});
