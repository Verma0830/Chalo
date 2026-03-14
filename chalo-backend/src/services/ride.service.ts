// ============================================================
// Chalo Backend — Ride Service
// Core ride lifecycle: request → match → pickup → ride → complete
// ============================================================

import prisma from '../config/database';
import { createHash, randomBytes } from 'crypto';
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
import { Prisma, RideStatus, CancellationBy, CancellationReasonCode } from '@prisma/client';

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

const ACTIVE_TRACKING_STATUSES: RideStatus[] = [
  RideStatus.DRIVER_ASSIGNED,
  RideStatus.DRIVER_ARRIVED,
  RideStatus.IN_PROGRESS,
];

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
          gstAmount: fareEstimate.gstAmount,
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
        gstAmount: fareEstimate.gstAmount,
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

    const rideStartOtp =
      (ride.status === RideStatus.DRIVER_ASSIGNED || ride.status === RideStatus.DRIVER_ARRIVED)
        ? ride.rideStartOtp
        : null;

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
      rideStartOtp,
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
    if (!ACTIVE_TRACKING_STATUSES.includes(ride.status)) {
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
   * Create a public tracking token for an active ride the customer owns.
   */
  async shareRide(rideId: string, customerId: string) {
    const ride = await prisma.ride.findUnique({
      where: { id: rideId },
      select: {
        id: true,
        customerId: true,
        status: true,
      },
    });

    if (!ride) {
      throw ApiError.notFound('Ride not found', ErrorCode.RIDE_NOT_FOUND);
    }

    if (ride.customerId !== customerId) {
      throw ApiError.forbidden('You can only share your own rides', ErrorCode.FORBIDDEN);
    }

    if (!ACTIVE_TRACKING_STATUSES.includes(ride.status)) {
      throw ApiError.badRequest(
        'Tracking link can only be created for an active ride',
        'RIDE_NOT_SHAREABLE'
      );
    }

    const now = new Date();
    const expiresAt = new Date(now.getTime() + CONSTANTS.RIDE_SHARE_TTL_HOURS * 60 * 60 * 1000);
    const token = randomBytes(CONSTANTS.RIDE_SHARE_TOKEN_BYTES).toString('base64url');
    const tokenHash = createHash('sha256').update(token).digest('hex');

    await prisma.$transaction(async (tx) => {
      await tx.rideShareLink.updateMany({
        where: {
          rideId,
          revokedAt: null,
          expiresAt: { gt: now },
        },
        data: { revokedAt: now },
      });

      await tx.rideShareLink.create({
        data: {
          rideId,
          tokenHash,
          expiresAt,
          createdById: customerId,
        },
      });
    });

    logger.info('Ride tracking link created', { rideId, customerId, expiresAt });

    return { rideId, token, expiresAt };
  }

  /**
   * Resolve a public tracking token into the current ride status and live location.
   */
  async getSharedTracking(token: string) {
    const tokenHash = createHash('sha256').update(token).digest('hex');
    const now = new Date();

    const link = await prisma.rideShareLink.findFirst({
      where: {
        tokenHash,
        revokedAt: null,
        expiresAt: { gt: now },
      },
      select: {
        expiresAt: true,
        ride: {
          select: {
            id: true,
            status: true,
            pickupLat: true,
            pickupLng: true,
            pickupAddress: true,
            dropLat: true,
            dropLng: true,
            dropAddress: true,
            requestedAt: true,
            driverAssignedAt: true,
            startedAt: true,
            completedAt: true,
            cancelledAt: true,
            driver: {
              select: {
                name: true,
                driverProfile: {
                  select: {
                    vehicleNumber: true,
                    vehicleModel: true,
                    currentLat: true,
                    currentLng: true,
                    lastLocationUpdate: true,
                  },
                },
              },
            },
          },
        },
      },
    });

    if (!link) {
      throw ApiError.notFound('Tracking link expired or invalid', 'TRACK_LINK_INVALID');
    }

    const driverProfile = link.ride.driver?.driverProfile;
    const isTrackingActive = ACTIVE_TRACKING_STATUSES.includes(link.ride.status);
    const hasLiveLocation =
      driverProfile?.currentLat !== null &&
      driverProfile?.currentLat !== undefined &&
      driverProfile?.currentLng !== null &&
      driverProfile?.currentLng !== undefined;

    return {
      rideId: link.ride.id,
      status: link.ride.status,
      isTrackingActive,
      expiresAt: link.expiresAt,
      pickup: {
        lat: link.ride.pickupLat,
        lng: link.ride.pickupLng,
        address: link.ride.pickupAddress,
      },
      drop: {
        lat: link.ride.dropLat,
        lng: link.ride.dropLng,
        address: link.ride.dropAddress,
      },
      driver: link.ride.driver
        ? {
            name: link.ride.driver.name,
            vehicleNumber: driverProfile?.vehicleNumber ?? null,
            vehicleModel: driverProfile?.vehicleModel ?? null,
          }
        : null,
      location:
        isTrackingActive && hasLiveLocation
          ? {
              lat: driverProfile.currentLat,
              lng: driverProfile.currentLng,
              updatedAt: driverProfile.lastLocationUpdate,
            }
          : null,
      timestamps: {
        requested: link.ride.requestedAt,
        driverAssigned: link.ride.driverAssignedAt,
        started: link.ride.startedAt,
        completed: link.ride.completedAt,
        cancelled: link.ride.cancelledAt,
      },
    };
  }

  /**
   * Cancel a ride (by customer)
   * 3-tier fee logic:
   *   1. Driver-fault reason code  → fee = ₹0 (waived). DRIVER_ASKED_TO_CANCEL also penalises driver.
   *   2. Status = DRIVER_ARRIVED   → fee = cancel_fee_arrived_amount (₹40, driver wasted a trip)
   *   3. Time > free window        → fee = cancel_fee_amount (₹20, deterrent)
   *   4. Within free window        → fee = ₹0
   * @param rideId - The ride ID to cancel
   * @param customerId - The customer's user ID
   * @param reasonCode - Structured reason from CancellationReasonCode enum
   * @param note - Optional free-text note (typically used with OTHER)
   */
  async cancelRide(
    rideId: string,
    customerId: string,
    reasonCode: CancellationReasonCode,
    note?: string
  ) {
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

    // Serial canceller policy: check if customer is in a cooldown period
    const redis = getRedisClient();
    const cooldownKey = `cancel:cooldown:${customerId}`;
    if (redis && isRedisReady()) {
      const cooldownTtl = await redis.ttl(cooldownKey).catch(() => -1);
      if (cooldownTtl > 0) {
        const minutesLeft = Math.ceil(cooldownTtl / 60);
        throw ApiError.badRequest(
          `You have cancelled too many rides recently. Please wait ${minutesLeft} minute${minutesLeft !== 1 ? 's' : ''} before cancelling again.`,
          ErrorCode.VALIDATION_ERROR
        );
      }
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

    // --- 3-tier cancellation fee logic ---
    const isDriverFault = (CONSTANTS.DRIVER_FAULT_REASON_CODES as readonly string[]).includes(reasonCode);
    const isDriverForced = reasonCode === CONSTANTS.DRIVER_FORCED_CANCEL_CODE;
    let cancellationFee = 0;
    let feeWaivedReason: string | undefined;

    if (isDriverFault) {
      // Tier 0: Driver-fault — always free for the customer
      feeWaivedReason = reasonCode;
    } else if (
      ride.driverId &&
      (ride.status === RideStatus.DRIVER_ARRIVED)
    ) {
      // Tier 1: Driver physically at pickup — highest fee (driver burned fuel + time)
      const arrivedFeeConfig = await prisma.platformConfig.findUnique({
        where: { key: CONSTANTS.CONFIG_KEYS.CANCEL_FEE_ARRIVED_AMOUNT },
        select: { value: true },
      });
      cancellationFee = arrivedFeeConfig
        ? Number(arrivedFeeConfig.value)
        : CONSTANTS.DEFAULT_CANCEL_FEE_ARRIVED;
    } else if (
      ride.driverId &&
      ride.driverAssignedAt &&
      ride.status === RideStatus.DRIVER_ASSIGNED
    ) {
      // Tier 2: After free window (driver assigned, en route but not arrived)
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
      // Tier 3: Within free window — fee stays ₹0
    }

    // Use transaction for atomic updates
    const cancelled = await prisma.$transaction(async (tx) => {
      // Update ride status
      const updatedRide = await tx.ride.update({
        where: { id: rideId },
        data: {
          status: RideStatus.CANCELLED,
          cancelledBy: CancellationBy.CUSTOMER,
          cancellationReasonCode: reasonCode,
          cancellationReason: note,
          cancelledAt: new Date(),
          rideEvents: {
            create: {
              eventType: 'CANCELLED',
              metadata: { cancelledBy: 'CUSTOMER', reasonCode, note, cancellationFee, feeWaivedReason },
            },
          },
        },
      });

      // Increment lifetime cancellation count
      await tx.customerProfile.update({
        where: { userId: customerId },
        data: { cancellationCount: { increment: 1 } },
      });

      return updatedRide;
    });

    // Serial canceller policy: track hourly count in Redis and apply cooldown if needed
    let cancellationWarning: string | undefined;
    if (redis && isRedisReady()) {
      const hourlyKey = `cancel:hourly:${customerId}`;
      try {
        const hourlyCount = await redis.incr(hourlyKey);
        // Set/refresh 1-hour expiry on first increment
        if (hourlyCount === 1) {
          await redis.expire(hourlyKey, 3600);
        }
        if (hourlyCount >= CONSTANTS.SERIAL_CANCEL_THRESHOLD_HOURLY) {
          // Apply cooldown — block cancellations for SERIAL_CANCEL_COOLDOWN_MINS
          await redis.setEx(cooldownKey, CONSTANTS.SERIAL_CANCEL_COOLDOWN_MINS * 60, '1');
          logger.warn('Serial canceller cooldown applied', { customerId, hourlyCount });
        }
      } catch (err) {
        logger.warn('Failed to update serial cancel Redis counters', { customerId, error: err });
      }
    }

    // Fetch updated lifetime count to decide whether to show a warning
    const updatedProfile = await prisma.customerProfile.findUnique({
      where: { userId: customerId },
      select: { cancellationCount: true },
    });
    if (updatedProfile && updatedProfile.cancellationCount >= CONSTANTS.SERIAL_CANCEL_WARNING_COUNT) {
      cancellationWarning = 'You have a high cancellation rate. Frequent cancellations may limit your ability to book rides.';
    }

    // If driver forced the customer to cancel, penalise driver's cancellation count
    if (isDriverForced && ride.driverId) {
      try {
        const driverProfile = await prisma.driverProfile.findFirst({
          where: { userId: ride.driverId },
          select: { id: true, driverCancellationCount: true, driverCancellationCountDaily: true, driverCancellationLastAt: true },
        });
        if (driverProfile) {
          const now = new Date();
          const isSameDay = driverProfile.driverCancellationLastAt !== null &&
            (() => {
              const last = new Date(driverProfile.driverCancellationLastAt!);
              const toIST = (d: Date) => new Date(d.getTime() + 5.5 * 60 * 60 * 1000);
              const lastIST = toIST(last), nowIST = toIST(now);
              return lastIST.getFullYear() === nowIST.getFullYear() &&
                lastIST.getMonth() === nowIST.getMonth() &&
                lastIST.getDate() === nowIST.getDate();
            })();
          const dailyCount = isSameDay ? driverProfile.driverCancellationCountDaily + 1 : 1;
          await prisma.driverProfile.update({
            where: { id: driverProfile.id },
            data: {
              driverCancellationCount: { increment: 1 },
              driverCancellationCountDaily: dailyCount,
              driverCancellationLastAt: now,
            },
          });
          // Log event for admin audit
          await prisma.rideEvent.create({
            data: {
              rideId,
              eventType: 'DRIVER_FORCED_CANCEL',
              metadata: { driverUserId: ride.driverId, driverDailyCount: dailyCount },
            },
          });
          logger.warn('Driver forced customer to cancel', { rideId, driverUserId: ride.driverId, dailyCount });
        }
      } catch (err) {
        // Non-fatal — log and continue. Ride is already cancelled.
        logger.error('Failed to update driver cancellation count after forced cancel', { rideId, error: err });
      }
    }

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

    logger.info('Ride cancelled by customer', { rideId, customerId, reasonCode, cancellationFee, isDriverFault });

    return { rideId: cancelled.id, status: cancelled.status, cancellationFee, feeWaivedReason, cancellationWarning };
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
   * Skip rating for a completed ride — customer pressed "Skip", never ask again.
   * Sets ratingSkippedAt on the ride. Android app uses this to clear SharedPreferences.
   * @param rideId - The ride ID
   * @param customerId - The customer's user ID
   */
  async skipRating(rideId: string, customerId: string) {
    const ride = await prisma.ride.findUnique({ where: { id: rideId } });

    if (!ride) {
      throw ApiError.notFound('Ride not found', ErrorCode.RIDE_NOT_FOUND);
    }

    if (ride.customerId !== customerId) {
      throw ApiError.forbidden('You can only skip rating for your own rides', ErrorCode.FORBIDDEN);
    }

    if (ride.status !== RideStatus.COMPLETED) {
      throw ApiError.badRequest('Can only skip rating for completed rides', ErrorCode.RIDE_NOT_COMPLETED);
    }

    if (ride.customerRating) {
      // Already rated — no-op, return success so client can clean up
      return { rideId, skipped: false, reason: 'already_rated' };
    }

    await prisma.ride.update({
      where: { id: rideId },
      data: { ratingSkippedAt: new Date() },
    });

    logger.info('Rating skipped', { rideId, customerId });

    return { rideId, skipped: true };
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
   * Raw PostGIS query: returns nearby online+verified drivers sorted by score.
   * Excludes any driver IDs in excludeIds (already tried in a previous pass).
   */
  private async queryNearbyDrivers(
    pickupLat: number,
    pickupLng: number,
    radiusKm: number,
    excludeIds: string[]
  ): Promise<
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
  > {
    const radiusMeters = radiusKm * 1000;

    const rows = await prisma.$queryRaw<
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
        AND NOT (dp."userId" = ANY(${excludeIds}))
        AND ST_DWithin(
          ST_SetSRID(ST_Point(dp."currentLng", dp."currentLat"), 4326)::geography,
          ST_SetSRID(ST_Point(${pickupLng}, ${pickupLat}), 4326)::geography,
          ${radiusMeters}
        )
      ORDER BY "distanceMeters" ASC
      LIMIT ${CONSTANTS.DRIVER_SEARCH_MAX_CANDIDATES}
    `;

    return rows;
  }

  /** Score drivers and return sorted userId[] */
  private scoreAndSort(
    drivers: Array<{ userId: string; distanceMeters: number; ratingAvg: number; ratingCount: number }>,
    radiusKm: number
  ): string[] {
    return drivers
      .map((d) => {
        const distanceKm = d.distanceMeters / 1000;
        const distanceScore = Math.max(0, 1 - distanceKm / radiusKm);
        // New drivers (< 10 ratings) get a neutral 3.5/5 score instead of 0 — prevents
        // them from always ranking last and never getting rides to build their rating.
        const effectiveRating = d.ratingCount < CONSTANTS.NEW_DRIVER_RATING_THRESHOLD
          ? CONSTANTS.NEW_DRIVER_NEUTRAL_RATING
          : d.ratingAvg;
        const ratingScore = effectiveRating / CONSTANTS.MAX_RATING;
        const idleTimeScore = 0.5; // placeholder for V2
        return { userId: d.userId, score: distanceScore * 0.5 + ratingScore * 0.3 + idleTimeScore * 0.2 };
      })
      .sort((a, b) => b.score - a.score)
      .map((d) => d.userId);
  }

  /**
   * Search for nearby drivers and send ride requests.
   * Two-pass: 5km first → if none, expand to 12km before giving up.
   */
  /** Public alias used by the scheduled-ride-dispatch BullMQ job */
  async searchAndNotifyDriversPublic(rideId: string, pickupLat: number, pickupLng: number, fare: number): Promise<void> {
    return this.searchAndNotifyDrivers(rideId, pickupLat, pickupLng, fare);
  }

  private async searchAndNotifyDrivers(
    rideId: string,
    pickupLat: number,
    pickupLng: number,
    fare: number
  ): Promise<void> {
    if (!rideId || pickupLat === undefined || pickupLng === undefined) {
      logger.error('Invalid params for driver search', { rideId, pickupLat, pickupLng });
      return;
    }

    const searchStart = Date.now();
    const initialRadius = CONSTANTS.DRIVER_SEARCH_RADIUS_KM;

    let nearbyDrivers = await this.queryNearbyDrivers(pickupLat, pickupLng, initialRadius, []);
    const searchDurationSecs = (Date.now() - searchStart) / 1000;
    driverSearchDuration.observe(searchDurationSecs);

    // Pass 1 empty → try expanded radius immediately
    if (nearbyDrivers.length === 0) {
      const expandedRadius = CONSTANTS.DRIVER_SEARCH_RADIUS_KM_EXPANDED;
      logger.info('Pass 1 empty — expanding radius', { rideId, initialRadius, expandedRadius });

      nearbyDrivers = await this.queryNearbyDrivers(pickupLat, pickupLng, expandedRadius, []);

      if (nearbyDrivers.length === 0) {
        await this.markNoDriver(rideId, expandedRadius, searchDurationSecs, true);
        return;
      }

      // Mark expanded pass used so BullMQ knows not to expand again
      const redis = getRedisClient();
      if (redis && isRedisReady()) {
        void redis.setEx(`ride:expanded:${rideId}`, CONSTANTS.RIDE_CANDIDATES_TTL_SECS, '1').catch(() => null);
      }

      await prisma.ride.update({
        where: { id: rideId },
        data: {
          rideEvents: { create: { eventType: 'DRIVER_SEARCH_EXPANDED', metadata: { expandedRadius } } },
        },
      });

      const sortedIds = this.scoreAndSort(nearbyDrivers, expandedRadius);
      await this.storeAndDispatch(rideId, sortedIds, fare, pickupLat, pickupLng);
      logger.info('Pass 2 dispatch started', { rideId, candidates: sortedIds.length, expandedRadius });
      return;
    }

    const sortedIds = this.scoreAndSort(nearbyDrivers, initialRadius);
    await this.storeAndDispatch(rideId, sortedIds, fare, pickupLat, pickupLng);

    logger.info('Broadcast driver search initiated', {
      rideId,
      totalCandidates: sortedIds.length,
      firstBatchSize: Math.min(sortedIds.length, CONSTANTS.DRIVER_BROADCAST_SIZE),
      searchDurationSecs,
    });
  }

  /** Store candidate list in Redis and dispatch first batch */
  private async storeAndDispatch(
    rideId: string,
    candidateIds: string[],
    fare: number,
    pickupLat: number,
    pickupLng: number
  ): Promise<void> {
    const redis = getRedisClient();
    if (redis && isRedisReady()) {
      void redis
        .setEx(`ride:candidates:${rideId}`, CONSTANTS.RIDE_CANDIDATES_TTL_SECS, JSON.stringify(candidateIds))
        .catch((err) => logger.warn('Redis candidate list write failed', { rideId, error: String(err) }));
    }
    const firstBatch = candidateIds.slice(0, CONSTANTS.DRIVER_BROADCAST_SIZE);
    await this.dispatchBatch(rideId, firstBatch, fare, pickupLat, pickupLng, CONSTANTS.DRIVER_BROADCAST_SIZE);
  }

  /** Mark ride as NO_DRIVER, notify customer, sync RTDB */
  private async markNoDriver(
    rideId: string,
    searchRadiusKm: number,
    searchDurationSecs: number,
    wasExpanded: boolean
  ): Promise<void> {
    await prisma.ride.update({
      where: { id: rideId },
      data: {
        status: RideStatus.NO_DRIVER,
        rideEvents: {
          create: {
            eventType: 'NO_DRIVER_FOUND',
            metadata: { searchRadiusKm, searchDurationSecs, wasExpanded },
          },
        },
      },
    });

    const ride = await prisma.ride.findUnique({ where: { id: rideId }, select: { customerId: true } });
    if (ride) {
      await notificationService.sendPushNotification({
        userId: ride.customerId,
        title: 'No riders available',
        body: 'No riders are available nearby. Please try again in a few minutes.',
        data: { rideId, type: 'NO_DRIVER' },
      });
    }

    this.syncRideToRTDB(rideId, { status: RideStatus.NO_DRIVER });
  }

  /**
   * Expanded radius search — called by BullMQ when all 5km candidates are exhausted.
   * Searches at 12km (config-driven), excluding already-tried driver IDs.
   * Returns true if new candidates were found and dispatched; false if still empty.
   */
  async expandDriverSearch(
    rideId: string,
    pickupLat: number,
    pickupLng: number,
    fare: number,
    alreadyTriedIds: string[]
  ): Promise<boolean> {
    const expandedRadius = CONSTANTS.DRIVER_SEARCH_RADIUS_KM_EXPANDED;

    const newDrivers = await this.queryNearbyDrivers(pickupLat, pickupLng, expandedRadius, alreadyTriedIds);

    if (newDrivers.length === 0) {
      logger.info('Expanded search returned 0 — final NO_DRIVER', { rideId, expandedRadius, alreadyTriedIds: alreadyTriedIds.length });
      return false;
    }

    const sortedIds = this.scoreAndSort(newDrivers, expandedRadius);

    // Append new candidates to Redis (replace, since old ones already tried)
    const redis = getRedisClient();
    if (redis && isRedisReady()) {
      void redis.setEx(`ride:candidates:${rideId}`, CONSTANTS.RIDE_CANDIDATES_TTL_SECS, JSON.stringify(sortedIds)).catch(() => null);
      void redis.setEx(`ride:expanded:${rideId}`, CONSTANTS.RIDE_CANDIDATES_TTL_SECS, '1').catch(() => null);
    }

    await prisma.ride.update({
      where: { id: rideId },
      data: {
        rideEvents: {
          create: {
            eventType: 'DRIVER_SEARCH_EXPANDED',
            metadata: { expandedRadius, newCandidates: sortedIds.length, alreadyTriedCount: alreadyTriedIds.length },
          },
        },
      },
    });

    await this.storeAndDispatch(rideId, sortedIds, fare, pickupLat, pickupLng);

    logger.info('Expanded search dispatched', { rideId, expandedRadius, newCandidates: sortedIds.length });
    return true;
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
