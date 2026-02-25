// ============================================================
// Chalo Backend — Ride Service
// Core ride lifecycle: request → match → pickup → ride → complete
// ============================================================

import prisma from '../config/database';
import { ApiError } from '../utils/apiError';
import { fareService } from './fare.service';
import { notificationService } from './notification.service';
import { buildPaginationMeta } from '../utils/apiResponse';
import { PaginationMeta } from '../types';
import CONSTANTS from '../utils/constants';
import logger from '../config/logger';
import { RideStatus, CancellationBy } from '@prisma/client';

export class RideService {
  /**
   * Create an on-demand ride request
   * Steps: calculate fare → save ride → search for drivers
   */
  async createRide(
    customerId: string,
    pickup: { lat: number; lng: number; address: string },
    drop: { lat: number; lng: number; address: string },
    paymentMethod: 'CASH' | 'UPI'
  ) {
    // 1. Check if customer has an active ride
    const activeRide = await prisma.ride.findFirst({
      where: {
        customerId,
        status: {
          in: [RideStatus.REQUESTED, RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVED, RideStatus.IN_PROGRESS],
        },
      },
    });

    if (activeRide) {
      throw ApiError.conflict('You already have an active ride. Complete or cancel it first.');
    }

    // 2. Calculate fare estimate
    const fareEstimate = await fareService.estimateFare(pickup, drop);

    // 3. Create ride record
    const ride = await prisma.ride.create({
      data: {
        customerId,
        pickupLat: pickup.lat,
        pickupLng: pickup.lng,
        pickupAddress: pickup.address,
        dropLat: drop.lat,
        dropLng: drop.lng,
        dropAddress: drop.address,
        distanceKm: fareEstimate.distanceKm,
        durationMins: fareEstimate.durationMins,
        baseFare: fareEstimate.baseFare,
        surgeMultiplier: fareEstimate.surgeMultiplier,
        finalFare: fareEstimate.totalFare,
        paymentMethod,
        status: RideStatus.REQUESTED,
        rideEvents: {
          create: {
            eventType: 'REQUESTED',
            lat: pickup.lat,
            lng: pickup.lng,
            metadata: { fare: fareEstimate.totalFare, surge: fareEstimate.surgeMultiplier },
          },
        },
      },
    });

    logger.info('Ride created', { rideId: ride.id, customerId, fare: fareEstimate.totalFare });

    // 4. Begin driver search (async — notification service handles this)
    this.searchAndNotifyDrivers(ride.id, pickup.lat, pickup.lng).catch((err) => {
      logger.error('Driver search failed', { rideId: ride.id, error: err });
    });

    return {
      rideId: ride.id,
      status: ride.status,
      fare: {
        ...fareEstimate,
      },
      pickup: { lat: pickup.lat, lng: pickup.lng, address: pickup.address },
      drop: { lat: drop.lat, lng: drop.lng, address: drop.address },
      paymentMethod,
    };
  }

  /**
   * Create a scheduled ride
   */
  async createScheduledRide(
    customerId: string,
    pickup: { lat: number; lng: number; address: string },
    drop: { lat: number; lng: number; address: string },
    paymentMethod: 'CASH' | 'UPI',
    scheduledAt: Date
  ) {
    // Calculate fare (surge will be recalculated at actual ride time)
    const fareEstimate = await fareService.estimateFare(pickup, drop);

    const ride = await prisma.ride.create({
      data: {
        customerId,
        pickupLat: pickup.lat,
        pickupLng: pickup.lng,
        pickupAddress: pickup.address,
        dropLat: drop.lat,
        dropLng: drop.lng,
        dropAddress: drop.address,
        distanceKm: fareEstimate.distanceKm,
        durationMins: fareEstimate.durationMins,
        baseFare: fareEstimate.baseFare,
        surgeMultiplier: 1.0, // No surge for scheduled — recalculated at ride time
        finalFare: fareEstimate.baseFare,
        paymentMethod,
        status: RideStatus.SCHEDULED,
        isScheduled: true,
        scheduledAt,
        rideEvents: {
          create: {
            eventType: 'SCHEDULED',
            metadata: { scheduledAt: scheduledAt.toISOString(), estimatedFare: fareEstimate.baseFare },
          },
        },
      },
    });

    logger.info('Scheduled ride created', { rideId: ride.id, scheduledAt });

    return {
      rideId: ride.id,
      status: ride.status,
      scheduledAt,
      estimatedFare: fareEstimate,
    };
  }

  /**
   * Get ride details by ID (for customer)
   */
  async getRideDetails(rideId: string, customerId: string) {
    const ride = await prisma.ride.findUnique({
      where: { id: rideId },
      include: {
        driver: {
          select: {
            id: true,
            name: true,
            phone: true,
            driverProfile: {
              select: {
                vehicleNumber: true,
                vehicleModel: true,
                ratingAvg: true,
                bikePhotoUrl: true,
              },
            },
          },
        },
      },
    });

    if (!ride) {
      throw ApiError.notFound('Ride not found');
    }

    if (ride.customerId !== customerId) {
      throw ApiError.forbidden('You can only view your own rides');
    }

    return {
      id: ride.id,
      status: ride.status,
      pickup: { lat: ride.pickupLat, lng: ride.pickupLng, address: ride.pickupAddress },
      drop: { lat: ride.dropLat, lng: ride.dropLng, address: ride.dropAddress },
      fare: {
        baseFare: ride.baseFare,
        surgeMultiplier: ride.surgeMultiplier,
        finalFare: ride.finalFare,
      },
      paymentMethod: ride.paymentMethod,
      paymentStatus: ride.paymentStatus,
      distanceKm: ride.distanceKm,
      durationMins: ride.durationMins,
      isScheduled: ride.isScheduled,
      scheduledAt: ride.scheduledAt,
      driver: ride.driver
        ? {
            id: ride.driver.id,
            name: ride.driver.name,
            phone: ride.driver.phone,
            vehicleNumber: ride.driver.driverProfile?.vehicleNumber,
            vehicleModel: ride.driver.driverProfile?.vehicleModel,
            rating: ride.driver.driverProfile?.ratingAvg,
          }
        : null,
      rating: ride.customerRating,
      timestamps: {
        requested: ride.requestedAt,
        driverAssigned: ride.driverAssignedAt,
        driverArrived: ride.driverArrivedAt,
        started: ride.startedAt,
        completed: ride.completedAt,
        cancelled: ride.cancelledAt,
      },
    };
  }

  /**
   * Get customer's ride history with pagination
   */
  async getRideHistory(
    customerId: string,
    page: number,
    limit: number,
    statusFilter: string
  ): Promise<{ rides: unknown[]; meta: PaginationMeta }> {
    const where: Record<string, unknown> = { customerId };

    if (statusFilter === 'COMPLETED') {
      where.status = RideStatus.COMPLETED;
    } else if (statusFilter === 'CANCELLED') {
      where.status = RideStatus.CANCELLED;
    }
    // 'ALL' → no status filter

    const [rides, total] = await Promise.all([
      prisma.ride.findMany({
        where,
        orderBy: { createdAt: 'desc' },
        skip: (page - 1) * limit,
        take: limit,
        select: {
          id: true,
          pickupAddress: true,
          dropAddress: true,
          finalFare: true,
          status: true,
          paymentMethod: true,
          distanceKm: true,
          customerRating: true,
          createdAt: true,
          completedAt: true,
          driver: {
            select: {
              name: true,
              driverProfile: {
                select: { vehicleNumber: true },
              },
            },
          },
        },
      }),
      prisma.ride.count({ where }),
    ]);

    const meta = buildPaginationMeta(page, limit, total);

    return { rides, meta };
  }

  /**
   * Get customer's scheduled rides
   */
  async getScheduledRides(customerId: string) {
    return prisma.ride.findMany({
      where: {
        customerId,
        isScheduled: true,
        status: RideStatus.SCHEDULED,
        scheduledAt: { gte: new Date() },
      },
      orderBy: { scheduledAt: 'asc' },
      select: {
        id: true,
        pickupAddress: true,
        dropAddress: true,
        finalFare: true,
        scheduledAt: true,
        paymentMethod: true,
      },
    });
  }

  /**
   * Cancel a ride (by customer)
   */
  async cancelRide(rideId: string, customerId: string, reason?: string) {
    const ride = await prisma.ride.findUnique({ where: { id: rideId } });

    if (!ride) {
      throw ApiError.notFound('Ride not found');
    }

    if (ride.customerId !== customerId) {
      throw ApiError.forbidden('You can only cancel your own rides');
    }

    const cancellableStatuses: RideStatus[] = [
      RideStatus.REQUESTED,
      RideStatus.DRIVER_ASSIGNED,
      RideStatus.DRIVER_ARRIVED,
      RideStatus.SCHEDULED,
    ];

    if (!cancellableStatuses.includes(ride.status)) {
      throw ApiError.badRequest(`Cannot cancel ride in ${ride.status} status`);
    }

    // Update ride
    const cancelled = await prisma.ride.update({
      where: { id: rideId },
      data: {
        status: RideStatus.CANCELLED,
        cancelledBy: CancellationBy.CUSTOMER,
        cancellationReason: reason,
        cancelledAt: new Date(),
        rideEvents: {
          create: {
            eventType: 'CANCELLED',
            metadata: { cancelledBy: 'CUSTOMER', reason },
          },
        },
      },
    });

    // Increment cancellation count (for V2 penalty logic + driver visibility)
    await prisma.customerProfile.update({
      where: { userId: customerId },
      data: { cancellationCount: { increment: 1 } },
    });

    // Notify driver if one was assigned
    if (ride.driverId) {
      await notificationService.sendPushNotification({
        userId: ride.driverId,
        title: 'Ride Cancelled',
        body: 'The customer has cancelled the ride.',
        data: { rideId, type: 'RIDE_CANCELLED' },
      });
    }

    logger.info('Ride cancelled by customer', { rideId, customerId, reason });

    return { rideId: cancelled.id, status: cancelled.status };
  }

  /**
   * Rate a completed ride
   */
  async rateRide(rideId: string, customerId: string, rating: number, comment?: string) {
    const ride = await prisma.ride.findUnique({ where: { id: rideId } });

    if (!ride) {
      throw ApiError.notFound('Ride not found');
    }

    if (ride.customerId !== customerId) {
      throw ApiError.forbidden('You can only rate your own rides');
    }

    if (ride.status !== RideStatus.COMPLETED) {
      throw ApiError.badRequest('Can only rate completed rides');
    }

    if (ride.customerRating) {
      throw ApiError.conflict('Ride already rated');
    }

    // Update ride with rating
    await prisma.ride.update({
      where: { id: rideId },
      data: {
        customerRating: rating,
        customerComment: comment,
      },
    });

    // Update driver's average rating
    if (ride.driverId) {
      const driverProfile = await prisma.driverProfile.findFirst({
        where: { userId: ride.driverId },
      });

      if (driverProfile) {
        const newCount = driverProfile.ratingCount + 1;
        const newAvg =
          (driverProfile.ratingAvg * driverProfile.ratingCount + rating) / newCount;

        await prisma.driverProfile.update({
          where: { id: driverProfile.id },
          data: {
            ratingAvg: Number(newAvg.toFixed(2)),
            ratingCount: newCount,
          },
        });
      }
    }

    logger.info('Ride rated', { rideId, rating });

    return { rideId, rating, comment };
  }

  /**
   * Search for nearby drivers and send ride requests
   * This is the matching engine (V1: nearest driver first)
   */
  private async searchAndNotifyDrivers(
    rideId: string,
    pickupLat: number,
    pickupLng: number
  ): Promise<void> {
    // Find online, verified drivers within radius
    // V1: Simple nearest-first matching using stored lat/lng
    // V2: Use PostGIS spatial queries for proper geofencing
    const radiusKm = CONSTANTS.DRIVER_SEARCH_RADIUS_KM;

    // Approximate bounding box for initial filter (1 degree ≈ 111 km)
    const latDelta = radiusKm / 111;
    const lngDelta = radiusKm / (111 * Math.cos((pickupLat * Math.PI) / 180));

    const nearbyDrivers = await prisma.driverProfile.findMany({
      where: {
        isOnline: true,
        verificationStatus: 'VERIFIED',
        currentLat: {
          gte: pickupLat - latDelta,
          lte: pickupLat + latDelta,
        },
        currentLng: {
          gte: pickupLng - lngDelta,
          lte: pickupLng + lngDelta,
        },
      },
      include: {
        user: { select: { id: true, name: true } },
      },
      take: 10, // Top 10 nearest
    });

    if (nearbyDrivers.length === 0) {
      // No drivers available — update ride status
      await prisma.ride.update({
        where: { id: rideId },
        data: {
          status: RideStatus.NO_DRIVER,
          rideEvents: {
            create: {
              eventType: 'NO_DRIVER_FOUND',
              metadata: { searchRadiusKm: radiusKm },
            },
          },
        },
      });

      // Notify customer
      const ride = await prisma.ride.findUnique({ where: { id: rideId } });
      if (ride) {
        await notificationService.sendPushNotification({
          userId: ride.customerId,
          title: 'No riders available',
          body: 'No riders are available nearby. Please try again in a few minutes.',
          data: { rideId, type: 'NO_DRIVER' },
        });
      }

      return;
    }

    // V1: Send request to nearest driver first
    // TODO V2: Broadcast to multiple drivers, first-accept wins
    const nearestDriver = nearbyDrivers[0];

    await notificationService.sendPushNotification({
      userId: nearestDriver.user.id,
      title: 'New Ride Request!',
      body: `New ride request nearby. ₹${(await prisma.ride.findUnique({ where: { id: rideId } }))?.finalFare}`,
      data: { rideId, type: 'RIDE_REQUEST' },
    });

    logger.info('Ride request sent to driver', {
      rideId,
      driverId: nearestDriver.user.id,
      driversFound: nearbyDrivers.length,
    });
  }
}

export const rideService = new RideService();
export default rideService;
