// ============================================================
// Tests — Ride Service Unit Tests
// Tests business logic with mocked dependencies
// ============================================================

import { RideStatus } from '@prisma/client';
import { ApiError, ErrorCode } from '../../utils/apiError';

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

import prisma from '../../config/database';
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
      const invalidPickup = { lat: undefined as any, lng: 77.3178, address: 'Test' };

      await expect(
        rideService.createRide(customerId, invalidPickup, validDrop, 'CASH')
      ).rejects.toThrow(ApiError);

      try {
        await rideService.createRide(customerId, invalidPickup, validDrop, 'CASH');
      } catch (err) {
        expect((err as ApiError).code).toBe(ErrorCode.VALIDATION_ERROR);
      }
    });

    it('throws RIDE_ALREADY_ACTIVE when customer has active ride', async () => {
      (prisma.ride.findFirst as jest.Mock).mockResolvedValue({
        id: 'existing_ride',
        status: RideStatus.IN_PROGRESS,
      });

      await expect(
        rideService.createRide(customerId, validPickup, validDrop, 'CASH')
      ).rejects.toThrow('You already have an active ride');

      try {
        await rideService.createRide(customerId, validPickup, validDrop, 'CASH');
      } catch (err) {
        expect((err as ApiError).code).toBe(ErrorCode.RIDE_ALREADY_ACTIVE);
      }
    });
  });

  describe('getRideDetails', () => {
    it('throws RIDE_NOT_FOUND when ride does not exist', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue(null);

      await expect(
        rideService.getRideDetails('nonexistent', 'customer_001')
      ).rejects.toThrow('Ride not found');

      try {
        await rideService.getRideDetails('nonexistent', 'customer_001');
      } catch (err) {
        expect((err as ApiError).code).toBe(ErrorCode.RIDE_NOT_FOUND);
      }
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

      try {
        await rideService.getRideDetails('ride_001', 'wrong_customer');
      } catch (err) {
        expect((err as ApiError).code).toBe(ErrorCode.FORBIDDEN);
      }
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
      ).rejects.toThrow('Cannot cancel ride');

      try {
        await rideService.cancelRide('ride_001', 'customer_001');
      } catch (err) {
        expect((err as ApiError).code).toBe(ErrorCode.RIDE_CANNOT_CANCEL);
      }
    });

    it('throws RIDE_NOT_FOUND for nonexistent ride', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue(null);

      try {
        await rideService.cancelRide('nonexistent', 'customer_001');
      } catch (err) {
        expect((err as ApiError).code).toBe(ErrorCode.RIDE_NOT_FOUND);
      }
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

      try {
        await rideService.rateRide('ride_001', 'customer_001', 5);
      } catch (err) {
        expect((err as ApiError).code).toBe(ErrorCode.RIDE_NOT_COMPLETED);
      }
    });

    it('throws RIDE_ALREADY_RATED when ride was already rated', async () => {
      (prisma.ride.findUnique as jest.Mock).mockResolvedValue({
        id: 'ride_001',
        customerId: 'customer_001',
        status: RideStatus.COMPLETED,
        customerRating: 4, // Already rated!
      });

      try {
        await rideService.rateRide('ride_001', 'customer_001', 5);
      } catch (err) {
        expect((err as ApiError).code).toBe(ErrorCode.RIDE_ALREADY_RATED);
      }
    });

    it('throws VALIDATION_ERROR for rating outside 1-5', async () => {
      try {
        await rideService.rateRide('ride_001', 'customer_001', 6);
      } catch (err) {
        expect((err as ApiError).code).toBe(ErrorCode.VALIDATION_ERROR);
      }
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
});
