// ============================================================
// Chalo Backend — Ride Service
// Core ride lifecycle: request → match → pickup → ride → complete
// ============================================================

import prisma from '../config/database';
import { ApiError, ErrorCode } from '../utils/apiError';
import { fareService } from './fare.service';
import { notificationService } from './notification.service';
import { buildPaginationMeta } from '../utils/apiResponse';
import { PaginationMeta } from '../types';
import CONSTANTS from '../utils/constants';
import { sanitizeLocation } from '../utils/sanitize';
import { ridesCreatedTotal, ridesCancelledTotal, driverSearchDuration } from '../utils/metrics';
import logger from '../config/logger';
import { getDatabase } from '../config/firebase';
import { getRedisClient, isRedisReady } from '../config/redis';
import { Prisma, RideStatus, CancellationBy } from '@prisma/client';

// ---------------------------------------------------------------------------
// Typed select shape for ride history — using Prisma.RideGetPayload ensures
// the return type stays in sync with schema changes automatically.
// ---------------------------------------------------------------------------
const RIDE_HISTORY_SELECT = {
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
} satisfies Prisma.RideSelect;

type RideHistoryItem = Prisma.RideGetPayload<{ select: typeof RIDE_HISTORY_SELECT }>;

export class RideService {
  /**
   * Create an on-demand ride request
   * Steps: calculate fare → save ride → search for drivers
   * @param customerId - The customer's user ID
   * @param pickup - Pickup location with lat, lng, address
   * @param drop - Drop-off location with lat, lng, address
   * @param paymentMethod - CASH or UPI
   * @throws {ApiError} If customer already has an active ride
   */
  async createRide(
    customerId: string,
    pickup: { lat: number; lng: number; address: string },
    drop: { lat: number; lng: number; address: string },
    paymentMethod: 'CASH' | 'UPI'
  ) {
    // Validate inputs
    if (!customerId || !pickup?.lat || !pickup?.lng || !drop?.lat || !drop?.lng) {
      throw ApiError.badRequest('Invalid ride request parameters', ErrorCode.VALIDATION_ERROR);
    }

    // Sanitize address inputs
    const sanitizedPickup = sanitizeLocation(pickup);
    const sanitizedDrop = sanitizeLocation(drop);

    // 1. Calculate fare estimate BEFORE the transaction.
    //    Never call external services or long-running async work inside a Prisma
    //    $transaction — it holds the DB connection open for the full duration,
    //    which exhausts the connection pool under load.
    const fareEstimate = await fareService.estimateFare(sanitizedPickup, sanitizedDrop);

    // 2. Check for active ride + create in ONE transaction (prevents race condition)
    const createdRide = await prisma.$transaction(async (tx) => {
      const activeRide = await tx.ride.findFirst({
        where: {
          customerId,
          status: {
            in: [RideStatus.REQUESTED, RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVED, RideStatus.IN_PROGRESS],
          },
        },
      });

      if (activeRide) {
        throw ApiError.conflict(
          'You already have an active ride. Complete or cancel it first.',
          ErrorCode.RIDE_ALREADY_ACTIVE
        );
      }

      // 3. Create ride record inside transaction
      return tx.ride.create({
        data: {
          customerId,
          pickupLat: sanitizedPickup.lat,
          pickupLng: sanitizedPickup.lng,
          pickupAddress: sanitizedPickup.address,
          dropLat: sanitizedDrop.lat,
          dropLng: sanitizedDrop.lng,
          dropAddress: sanitizedDrop.address,
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
              lat: sanitizedPickup.lat,
              lng: sanitizedPickup.lng,
              metadata: { fare: fareEstimate.totalFare, surge: fareEstimate.surgeMultiplier },
            },
          },
        },
      });
    });

    // Record metric
    ridesCreatedTotal.inc({ type: 'on_demand' });

    logger.info('Ride created', { rideId: createdRide.id, customerId, fare: fareEstimate.totalFare });

    // Sync initial status to Firebase RTDB for real-time client updates
    this.syncRideToRTDB(createdRide.id, { status: RideStatus.REQUESTED });

    // 4. Begin driver search (async — notification service handles this)
    // On failure, mark ride as NO_DRIVER to prevent stuck rides (M8)
    this.searchAndNotifyDrivers(createdRide.id, sanitizedPickup.lat, sanitizedPickup.lng, fareEstimate.totalFare).catch(async (err) => {
      logger.error('Driver search failed — marking ride as NO_DRIVER', { rideId: createdRide.id, error: err });
      try {
        await prisma.ride.update({
          where: { id: createdRide.id },
          data: {
            status: RideStatus.NO_DRIVER,
            rideEvents: {
              create: {
                eventType: 'DRIVER_SEARCH_FAILED',
                metadata: { error: String(err) },
              },
            },
          },
        });
        await notificationService.sendPushNotification({
          userId: customerId,
          title: 'No riders available',
          body: 'We could not find a rider. Please try again.',
          data: { rideId: createdRide.id, type: 'NO_DRIVER' },
        });
      } catch (updateErr) {
        logger.error('Failed to update ride after search failure', { rideId: createdRide.id, error: updateErr });
      }
    });

    return {
      rideId: createdRide.id,
      status: createdRide.status,
      fare: {
        ...fareEstimate,
      },
      pickup: sanitizedPickup,
      drop: sanitizedDrop,
      paymentMethod,
    };
  }

  /**
   * Create a scheduled ride
   * @param customerId - The customer's user ID
   * @param pickup - Pickup location
   * @param drop - Drop-off location
   * @param paymentMethod - CASH or UPI
   * @param scheduledAt - When the ride should start
   */
  async createScheduledRide(
    customerId: string,
    pickup: { lat: number; lng: number; address: string },
    drop: { lat: number; lng: number; address: string },
    paymentMethod: 'CASH' | 'UPI',
    scheduledAt: Date
  ) {
    // Sanitize inputs
    const sanitizedPickup = sanitizeLocation(pickup);
    const sanitizedDrop = sanitizeLocation(drop);

    // Calculate fare (surge will be recalculated at actual ride time)
    const fareEstimate = await fareService.estimateFare(sanitizedPickup, sanitizedDrop);

    const ride = await prisma.ride.create({
      data: {
        customerId,
        pickupLat: sanitizedPickup.lat,
        pickupLng: sanitizedPickup.lng,
        pickupAddress: sanitizedPickup.address,
        dropLat: sanitizedDrop.lat,
        dropLng: sanitizedDrop.lng,
        dropAddress: sanitizedDrop.address,
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

    // Record metric
    ridesCreatedTotal.inc({ type: 'scheduled' });

    logger.info('Scheduled ride created', { rideId: ride.id, scheduledAt });

    // Sync scheduled status to Firebase RTDB
    this.syncRideToRTDB(ride.id, { status: RideStatus.SCHEDULED });

    return {
      rideId: ride.id,
      status: ride.status,
      scheduledAt,
      estimatedFare: fareEstimate,
    };
  }

  /**
   * Get ride details by ID (for customer)
   * @param rideId - The ride ID
   * @param customerId - The customer's user ID (for authorization)
   */
  async getRideDetails(rideId: string, customerId: string) {
    // Validate inputs
    if (!rideId || !customerId) {
      throw ApiError.badRequest('Ride ID and Customer ID are required', ErrorCode.VALIDATION_ERROR);
    }

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
      throw ApiError.notFound('Ride not found', ErrorCode.RIDE_NOT_FOUND);
    }

    if (ride.customerId !== customerId) {
      throw ApiError.forbidden('You can only view your own rides', ErrorCode.FORBIDDEN);
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
  ): Promise<{ rides: RideHistoryItem[]; meta: PaginationMeta }> {
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
        select: RIDE_HISTORY_SELECT,
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
   * Get real-time ride location (driver's current position)
   * @param rideId - The ride ID
   * @param customerId - The customer's user ID (for authorization)
   */
  async getRideLocation(rideId: string, customerId: string) {
    // Validate inputs
    if (!rideId || !customerId) {
      throw ApiError.badRequest('Ride ID and Customer ID are required', ErrorCode.VALIDATION_ERROR);
    }

    const ride = await prisma.ride.findUnique({
      where: { id: rideId },
      include: {
        driver: {
          select: {
            driverProfile: {
              select: {
                currentLat: true,
                currentLng: true,
                lastLocationUpdate: true,
              },
            },
          },
        },
      },
    });

    if (!ride) {
      throw ApiError.notFound('Ride not found', ErrorCode.RIDE_NOT_FOUND);
    }

    if (ride.customerId !== customerId) {
      throw ApiError.forbidden('You can only track your own rides', ErrorCode.FORBIDDEN);
    }

    // Only return location for active rides
    const trackableStatuses: RideStatus[] = [
      RideStatus.DRIVER_ASSIGNED,
      RideStatus.DRIVER_ARRIVED,
      RideStatus.IN_PROGRESS,
    ];

    if (!trackableStatuses.includes(ride.status)) {
      throw ApiError.badRequest(
        `Cannot track ride in ${ride.status} status`,
        'RIDE_NOT_TRACKABLE'
      );
    }

    const driverProfile = ride.driver?.driverProfile;

    if (!driverProfile?.currentLat || !driverProfile?.currentLng) {
      return {
        rideId,
        status: ride.status,
        location: null,
        message: 'Driver location not available',
      };
    }

    return {
      rideId,
      status: ride.status,
      location: {
        lat: driverProfile.currentLat,
        lng: driverProfile.currentLng,
        updatedAt: driverProfile.lastLocationUpdate,
      },
      // Pickup/drop for map rendering
      pickup: {
        lat: ride.pickupLat,
        lng: ride.pickupLng,
      },
      drop: {
        lat: ride.dropLat,
        lng: ride.dropLng,
      },
    };
  }

  /**
   * Cancel a ride (by customer)
   * Uses transaction to ensure atomic updates across ride and customer profile
   * @param rideId - The ride ID to cancel
   * @param customerId - The customer's user ID
   * @param reason - Optional cancellation reason
   */
  async cancelRide(rideId: string, customerId: string, reason?: string) {
    // Validate inputs
    if (!rideId || !customerId) {
      throw ApiError.badRequest('Ride ID and Customer ID are required', ErrorCode.VALIDATION_ERROR);
    }

    const ride = await prisma.ride.findUnique({ where: { id: rideId } });

    if (!ride) {
      throw ApiError.notFound('Ride not found', ErrorCode.RIDE_NOT_FOUND);
    }

    if (ride.customerId !== customerId) {
      throw ApiError.forbidden('You can only cancel your own rides', ErrorCode.FORBIDDEN);
    }

    const cancellableStatuses: RideStatus[] = [
      RideStatus.REQUESTED,
      RideStatus.DRIVER_ASSIGNED,
      RideStatus.DRIVER_ARRIVED,
      RideStatus.SCHEDULED,
    ];

    if (!cancellableStatuses.includes(ride.status)) {
      throw ApiError.badRequest(
        `Cannot cancel ride in ${ride.status} status`,
        ErrorCode.RIDE_CANNOT_CANCEL
      );
    }

    // --- Cancellation fee logic ---
    // Fee applies if customer cancels AFTER the free window AND a driver is assigned.
    let cancellationFee = 0;
    if (
      ride.driverId &&
      ride.driverAssignedAt &&
      (ride.status === RideStatus.DRIVER_ASSIGNED || ride.status === RideStatus.DRIVER_ARRIVED)
    ) {
      const [windowConfig, feeConfig] = await Promise.all([
        prisma.platformConfig.findUnique({
          where: { key: CONSTANTS.CONFIG_KEYS.FREE_CANCEL_WINDOW_SECS },
          select: { value: true },
        }),
        prisma.platformConfig.findUnique({
          where: { key: CONSTANTS.CONFIG_KEYS.CANCEL_FEE_AMOUNT },
          select: { value: true },
        }),
      ]);

      const windowSecs = windowConfig ? Number(windowConfig.value) : CONSTANTS.FREE_CANCEL_WINDOW_SECS;
      const feeAmount  = feeConfig   ? Number(feeConfig.value)   : CONSTANTS.DEFAULT_CANCEL_FEE;

      const secsSinceAssigned = (Date.now() - ride.driverAssignedAt.getTime()) / 1000;
      if (secsSinceAssigned > windowSecs) {
        cancellationFee = feeAmount;
      }
    }

    // Use transaction for atomic updates
    const cancelled = await prisma.$transaction(async (tx) => {
      // Update ride status
      const updatedRide = await tx.ride.update({
        where: { id: rideId },
        data: {
          status: RideStatus.CANCELLED,
          cancelledBy: CancellationBy.CUSTOMER,
          cancellationReason: reason,
          cancelledAt: new Date(),
          rideEvents: {
            create: {
              eventType: 'CANCELLED',
              metadata: { cancelledBy: 'CUSTOMER', reason, cancellationFee },
            },
          },
        },
      });

      // Increment cancellation count (for penalty logic + driver visibility)
      await tx.customerProfile.update({
        where: { userId: customerId },
        data: { cancellationCount: { increment: 1 } },
      });

      return updatedRide;
    });

    // Record metric
    ridesCancelledTotal.inc({ cancelled_by: 'customer' });

    // Sync cancellation to RTDB
    this.syncRideToRTDB(rideId, { status: RideStatus.CANCELLED });

    // Notify driver if one was assigned (outside transaction)
    if (ride.driverId) {
      await notificationService.sendPushNotification({
        userId: ride.driverId,
        title: 'Ride Cancelled',
        body: 'The customer has cancelled the ride.',
        data: { rideId, type: 'RIDE_CANCELLED' },
      });
    }

    logger.info('Ride cancelled by customer', { rideId, customerId, reason, cancellationFee });

    return { rideId: cancelled.id, status: cancelled.status, cancellationFee };
  }

  /**
   * Rate a completed ride
   * Uses transaction to update both ride rating and driver's average
   * @param rideId - The ride ID to rate
   * @param customerId - The customer's user ID
   * @param rating - Rating 1-5
   * @param comment - Optional comment
   */
  async rateRide(rideId: string, customerId: string, rating: number, comment?: string) {
    // Validate inputs
    if (!rideId || !customerId) {
      throw ApiError.badRequest('Ride ID and Customer ID are required', ErrorCode.VALIDATION_ERROR);
    }

    if (rating < CONSTANTS.MIN_RATING || rating > CONSTANTS.MAX_RATING) {
      throw ApiError.badRequest(
        `Rating must be between ${CONSTANTS.MIN_RATING} and ${CONSTANTS.MAX_RATING}`,
        ErrorCode.VALIDATION_ERROR
      );
    }

    const ride = await prisma.ride.findUnique({ where: { id: rideId } });

    if (!ride) {
      throw ApiError.notFound('Ride not found', ErrorCode.RIDE_NOT_FOUND);
    }

    if (ride.customerId !== customerId) {
      throw ApiError.forbidden('You can only rate your own rides', ErrorCode.FORBIDDEN);
    }

    if (ride.status !== RideStatus.COMPLETED) {
      throw ApiError.badRequest('Can only rate completed rides', ErrorCode.RIDE_NOT_COMPLETED);
    }

    if (ride.customerRating) {
      throw ApiError.conflict('Ride already rated', ErrorCode.RIDE_ALREADY_RATED);
    }

    // Use transaction for atomic updates
    await prisma.$transaction(async (tx) => {
      // Update ride with rating
      await tx.ride.update({
        where: { id: rideId },
        data: {
          customerRating: rating,
          customerComment: comment,
        },
      });

      // Update driver's average rating
      if (ride.driverId) {
        const driverProfile = await tx.driverProfile.findFirst({
          where: { userId: ride.driverId },
        });

        if (driverProfile) {
          const newCount = driverProfile.ratingCount + 1;
          const newAvg =
            (driverProfile.ratingAvg * driverProfile.ratingCount + rating) / newCount;

          await tx.driverProfile.update({
            where: { id: driverProfile.id },
            data: {
              ratingAvg: Number(newAvg.toFixed(2)),
              ratingCount: newCount,
            },
          });
        }
      }
    });

    logger.info('Ride rated', { rideId, rating });

    return { rideId, rating, comment };
  }

  /**
   * Write ride status to Firebase Realtime Database for instant client updates.
   * Fire-and-forget — failures are logged but never block the main flow.
   */
  private syncRideToRTDB(
    rideId: string,
    data: { status: RideStatus; driverId?: string | null; updatedAt?: string }
  ): void {
    try {
      const ref = getDatabase().ref(`rides/${rideId}`);
      ref
        .update({
          status: data.status,
          ...(data.driverId !== undefined ? { driverId: data.driverId } : {}),
          updatedAt: data.updatedAt ?? new Date().toISOString(),
        })
        .catch((err) => {
          logger.warn('RTDB ride sync failed', { rideId, error: String(err) });
        });
    } catch (err) {
      // getDatabase() may throw in dev when Firebase is not configured
      logger.warn('RTDB unavailable — skipping ride sync', { rideId, error: String(err) });
    }
  }

  /**
   * Search for nearby drivers and send ride requests
   * This is the matching engine with scoring-based selection
   * @param rideId - The ride to match
   * @param pickupLat - Pickup latitude
   * @param pickupLng - Pickup longitude
   */
  private async searchAndNotifyDrivers(
    rideId: string,
    pickupLat: number,
    pickupLng: number,
    fare: number
  ): Promise<void> {
    // Validate inputs
    if (!rideId || pickupLat === undefined || pickupLng === undefined) {
      logger.error('Invalid params for driver search', { rideId, pickupLat, pickupLng });
      return;
    }

    const searchStart = Date.now();

    // Find online, verified drivers within radius using PostGIS ST_DWithin
    const radiusKm = CONSTANTS.DRIVER_SEARCH_RADIUS_KM;
    const radiusMeters = radiusKm * 1000;

    // PostGIS spatial query — delegates distance computation to the DB engine
    // instead of fetching all drivers and filtering in JS.
    // ST_DWithin operates on geography type for metre-accurate results.
    const nearbyDrivers = await prisma.$queryRaw<
      Array<{
        id: string;
        userId: string;
        userName: string;
        currentLat: number;
        currentLng: number;
        ratingAvg: number;
        ratingCount: number;
        distanceMeters: number;
      }>
    >`
      SELECT
        dp."id",
        dp."userId",
        u."name" AS "userName",
        dp."currentLat",
        dp."currentLng",
        dp."ratingAvg",
        dp."ratingCount",
        ST_Distance(
          ST_SetSRID(ST_Point(dp."currentLng", dp."currentLat"), 4326)::geography,
          ST_SetSRID(ST_Point(${pickupLng}, ${pickupLat}), 4326)::geography
        ) AS "distanceMeters"
      FROM "driver_profiles" dp
      JOIN "users" u ON u."id" = dp."userId"
      WHERE dp."isOnline" = true
        AND dp."verificationStatus" = 'VERIFIED'
        AND dp."currentLat" IS NOT NULL
        AND dp."currentLng" IS NOT NULL
        AND ST_DWithin(
          ST_SetSRID(ST_Point(dp."currentLng", dp."currentLat"), 4326)::geography,
          ST_SetSRID(ST_Point(${pickupLng}, ${pickupLat}), 4326)::geography,
          ${radiusMeters}
        )
      ORDER BY "distanceMeters" ASC
      LIMIT ${CONSTANTS.DRIVER_SEARCH_MAX_CANDIDATES}
    `;

    // Record search duration metric
    const searchDurationSecs = (Date.now() - searchStart) / 1000;
    driverSearchDuration.observe(searchDurationSecs);

    if (nearbyDrivers.length === 0) {
      // No drivers available — update ride status
      await prisma.ride.update({
        where: { id: rideId },
        data: {
          status: RideStatus.NO_DRIVER,
          rideEvents: {
            create: {
              eventType: 'NO_DRIVER_FOUND',
              metadata: { searchRadiusKm: radiusKm, searchDurationSecs },
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

      // Sync no-driver status to RTDB
      this.syncRideToRTDB(rideId, { status: RideStatus.NO_DRIVER });

      return;
    }

    // Score-based driver selection (V2 improvement)
    // Score = (distance_score * 0.5) + (rating_score * 0.3) + (idle_time_score * 0.2)
    const scoredDrivers = nearbyDrivers.map((driver) => {
      const distanceKm = driver.distanceMeters / 1000;

      // Distance score: closer is better (1.0 at 0km, 0.0 at maxRadius)
      const distanceScore = Math.max(0, 1 - distanceKm / radiusKm);

      // Rating score: normalized to 0-1 (5 stars = 1.0)
      const ratingScore = driver.ratingAvg / CONSTANTS.MAX_RATING;

      // Idle time score: longer idle = higher priority (placeholder for V2)
      const idleTimeScore = 0.5;

      const totalScore = distanceScore * 0.5 + ratingScore * 0.3 + idleTimeScore * 0.2;

      return { driver, distanceKm, totalScore };
    });

    // Sort by score descending
    scoredDrivers.sort((a, b) => b.totalScore - a.totalScore);

    // Extract all candidate IDs sorted by score
    const allCandidateIds = scoredDrivers.map((s) => s.driver.userId);

    // Persist full candidate list in Redis for batch advance by BullMQ worker
    const redis = getRedisClient();
    if (redis && isRedisReady()) {
      void redis
        .setEx(
          `ride:candidates:${rideId}`,
          CONSTANTS.RIDE_CANDIDATES_TTL_SECS,
          JSON.stringify(allCandidateIds)
        )
        .catch((err) => {
          logger.warn('Redis candidate list write failed', { rideId, error: String(err) });
        });
    }

    // Dispatch first batch — top DRIVER_BROADCAST_SIZE drivers simultaneously
    const firstBatch = allCandidateIds.slice(0, CONSTANTS.DRIVER_BROADCAST_SIZE);
    await this.dispatchBatch(rideId, firstBatch, fare, pickupLat, pickupLng, CONSTANTS.DRIVER_BROADCAST_SIZE);

    logger.info('Broadcast driver search initiated', {
      rideId,
      totalCandidates: allCandidateIds.length,
      firstBatchSize: firstBatch.length,
      searchDurationSecs,
    });
  }

  /**
   * Dispatch a batch of drivers: write Redis offer keys, send FCM, schedule expiry job.
   * Public so the BullMQ ride-offer-expired worker can advance batches.
   *
   * @param rideId         - The ride being offered
   * @param driverUserIds  - userId[] for this batch
   * @param fare           - Fare amount (₹) for FCM message
   * @param pickupLat      - Pickup latitude
   * @param pickupLng      - Pickup longitude
   * @param nextBatchStart - Index into candidates[] for the next batch (passed into BullMQ job)
   */
  async dispatchBatch(
    rideId: string,
    driverUserIds: string[],
    fare: number,
    pickupLat: number,
    pickupLng: number,
    nextBatchStart: number
  ): Promise<void> {
    const redis = getRedisClient();

    // Store active batch so acceptRide() can clean up losing drivers
    if (redis && isRedisReady()) {
      void redis
        .setEx(
          `ride:active_batch:${rideId}`,
          CONSTANTS.RIDE_OFFER_BATCH_TTL_SECS,
          JSON.stringify(driverUserIds)
        )
        .catch(() => null);
    }

    // Write individual offer keys + send FCM for every driver in the batch
    await Promise.allSettled(
      driverUserIds.map(async (driverUserId) => {
        if (redis && isRedisReady()) {
          await redis
            .setEx(`ride:offer:${driverUserId}`, CONSTANTS.RIDE_OFFER_BATCH_TTL_SECS, rideId)
            .catch((err) => {
              logger.warn('Redis offer write failed', { rideId, driverUserId, error: String(err) });
            });
        }
        await notificationService.sendPushNotification({
          userId: driverUserId,
          title: 'New Ride Request!',
          body: `New ride nearby. ₹${fare}`,
          data: { rideId, type: 'RIDE_REQUEST' },
        });
      })
    );

    // Schedule the batch expiry job — BullMQ fires after RIDE_OFFER_BATCH_TTL_SECS
    const { scheduleRideOfferExpiry } = await import('../jobs/queue');
    await scheduleRideOfferExpiry({ rideId, nextBatchStart, pickupLat, pickupLng, fare });

    logger.info('Batch dispatched', { rideId, driverCount: driverUserIds.length, nextBatchStart });
  }

  /**
   * Get full ride receipt — only for completed rides the customer owns.
   */
  async getRideReceipt(rideId: string, customerId: string) {
    const ride = await prisma.ride.findUnique({
      where: { id: rideId },
      select: {
        id: true,
        customerId: true,
        status: true,
        pickupAddress: true,
        dropAddress: true,
        distanceKm: true,
        durationMins: true,
        baseFare: true,
        surgeMultiplier: true,
        finalFare: true,
        commissionAmount: true,
        driverEarning: true,
        paymentMethod: true,
        paymentStatus: true,
        customerRating: true,
        startedAt: true,
        completedAt: true,
        requestedAt: true,
        driver: {
          select: {
            name: true,
            driverProfile: {
              select: { vehicleNumber: true, vehicleModel: true, ratingAvg: true },
            },
          },
        },
      },
    });

    if (!ride) throw ApiError.notFound('Ride not found', ErrorCode.RIDE_NOT_FOUND);
    if (ride.customerId !== customerId) throw ApiError.forbidden('Not your ride', ErrorCode.FORBIDDEN);
    if (ride.status !== RideStatus.COMPLETED) {
      throw ApiError.badRequest('Receipt is only available for completed rides', 'RIDE_NOT_COMPLETED');
    }

    const surgeCharge = ride.surgeMultiplier > 1
      ? Math.round((ride.finalFare - ride.baseFare) * 100) / 100
      : 0;

    return {
      rideId: ride.id,
      requestedAt: ride.requestedAt,
      startedAt: ride.startedAt,
      completedAt: ride.completedAt,
      route: {
        pickup: ride.pickupAddress,
        drop: ride.dropAddress,
        distanceKm: ride.distanceKm,
        durationMins: ride.durationMins,
      },
      fare: {
        baseFare: ride.baseFare,
        surgeMultiplier: ride.surgeMultiplier,
        surgeCharge,
        totalFare: ride.finalFare,
      },
      payment: {
        method: ride.paymentMethod,
        status: ride.paymentStatus,
        amountPaid: ride.finalFare,
      },
      driver: ride.driver
        ? {
            name: ride.driver.name,
            vehicleNumber: ride.driver.driverProfile?.vehicleNumber ?? null,
            vehicleModel: ride.driver.driverProfile?.vehicleModel ?? null,
            rating: ride.driver.driverProfile?.ratingAvg ?? null,
          }
        : null,
      customerRating: ride.customerRating,
    };
  }
}


export const rideService = new RideService();
export default rideService;
