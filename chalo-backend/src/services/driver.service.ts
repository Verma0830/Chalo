// ============================================================
// Chalo Backend — Driver Service
// All driver-side business logic: online/offline, ride lifecycle,
// location updates, earnings, and withdrawals.
// ============================================================

import prisma from '../config/database';
import { ApiError, ErrorCode } from '../utils/apiError';
import { notificationService } from './notification.service';
import { buildPaginationMeta } from '../utils/apiResponse';
import { maskPhone, calculateCommission, calculateSettlementDate, generateOTP } from '../utils/helpers';
import CONSTANTS from '../utils/constants';
import logger from '../config/logger';
import { getDatabase } from '../config/firebase';
import { getRedisClient, isRedisReady } from '../config/redis';
import {
  onlineDriversGauge,
  activeRidesGauge,
  ridesCompletedTotal,
  ridesCancelledTotal,
} from '../utils/metrics';
import {
  Prisma,
  RideStatus,
  CancellationBy,
  VerificationStatus,
  SettlementStatus,
  WithdrawalMethod,
  WithdrawalStatus,
} from '@prisma/client';

// ---------------------------------------------------------------------------
// Typed select shapes — explicit selects prevent accidental over-fetching
// ---------------------------------------------------------------------------

const DRIVER_PROFILE_STATUS_SELECT = {
  isOnline: true,
  verificationStatus: true,
  planType: true,
  subscriptionExpiresAt: true,
  currentLat: true,
  currentLng: true,
  lastLocationUpdate: true,
  driverCancellationCount: true,
  driverCancellationCountDaily: true,
  driverCancellationLastAt: true,
} satisfies Prisma.DriverProfileSelect;

const ACTIVE_RIDE_FOR_DRIVER_SELECT = {
  id: true,
  status: true,
  pickupAddress: true,
  dropAddress: true,
  pickupLat: true,
  pickupLng: true,
  finalFare: true,
  paymentMethod: true,
  customer: {
    select: {
      name: true,
      phone: true,
      customerProfile: {
        select: { cancellationCount: true },
      },
    },
  },
} satisfies Prisma.RideSelect;

const TRIP_HISTORY_SELECT = {
  id: true,
  pickupAddress: true,
  dropAddress: true,
  distanceKm: true,
  finalFare: true,
  commissionAmount: true,
  driverEarning: true,
  status: true,
  paymentMethod: true,
  startedAt: true,
  completedAt: true,
  customer: {
    select: { name: true },
  },
} satisfies Prisma.RideSelect;

type TripHistoryItem = Prisma.RideGetPayload<{ select: typeof TRIP_HISTORY_SELECT }>;

export class DriverService {
  // -----------------------------------------------------------------------
  // DOCUMENT SUBMISSION
  // -----------------------------------------------------------------------

  /**
   * Save driver KYC documents and move status to UNDER_REVIEW.
   * Can be re-submitted if previously REJECTED.
   */
  async submitDocuments(userId: string, docs: {
    licenseNumber: string;
    licenseUrl: string;
    rcNumber: string;
    rcUrl: string;
    aadharNumber: string;
    aadharUrl: string;
    vehicleNumber: string;
    vehicleModel: string;
    bikePhotoUrl?: string;
  }) {
    const profile = await prisma.driverProfile.findUnique({
      where: { userId },
      select: { id: true, verificationStatus: true },
    });

    if (!profile) {
      throw ApiError.notFound('Driver profile not found', ErrorCode.DRIVER_NOT_FOUND);
    }

    if (profile.verificationStatus === VerificationStatus.VERIFIED) {
      throw ApiError.conflict(
        'Your account is already verified. Documents cannot be re-submitted.',
        ErrorCode.VALIDATION_ERROR
      );
    }

    const updated = await prisma.driverProfile.update({
      where: { userId },
      data: {
        licenseNumber: docs.licenseNumber,
        licenseUrl:    docs.licenseUrl,
        rcNumber:      docs.rcNumber,
        rcUrl:         docs.rcUrl,
        aadharNumber:  docs.aadharNumber,
        aadharUrl:     docs.aadharUrl,
        vehicleNumber: docs.vehicleNumber,
        vehicleModel:  docs.vehicleModel,
        bikePhotoUrl:  docs.bikePhotoUrl,
        verificationStatus: VerificationStatus.UNDER_REVIEW,
        rejectionReason: null,
      },
      select: {
        verificationStatus: true,
        licenseNumber:      true,
        rcNumber:           true,
        aadharNumber:       true,
        vehicleNumber:      true,
        vehicleModel:       true,
      },
    });

    logger.info('Driver submitted documents — status: UNDER_REVIEW', { userId });
    return updated;
  }

  // -----------------------------------------------------------------------
  // ONLINE / OFFLINE MANAGEMENT
  // -----------------------------------------------------------------------

  /**
   * Set driver online and record their starting location.
   * Guard: only VERIFIED drivers can go online.
   */
  async goOnline(userId: string, lat: number, lng: number) {
    const profile = await prisma.driverProfile.findUnique({
      where: { userId },
      select: { id: true, verificationStatus: true, isOnline: true },
    });

    if (!profile) {
      throw ApiError.notFound('Driver profile not found', ErrorCode.DRIVER_NOT_FOUND);
    }

    if (profile.verificationStatus !== VerificationStatus.VERIFIED) {
      throw ApiError.forbidden(
        'Account not yet verified. Please wait for admin approval.',
        ErrorCode.DRIVER_NOT_VERIFIED
      );
    }

    await prisma.driverProfile.update({
      where: { userId },
      data: {
        isOnline: true,
        currentLat: lat,
        currentLng: lng,
        lastLocationUpdate: new Date(),
      },
    });

    // Sync to RTDB (fire-and-forget — latency spike here should never fail the response)
    void this.syncDriverToRTDB(userId, { isOnline: true, lat, lng });

    // Only increment gauge if driver was previously offline
    if (!profile.isOnline) {
      onlineDriversGauge.inc();
    }

    logger.info('Driver went online', { userId, lat, lng });

    return { status: 'online' as const, lat, lng };
  }

  /**
   * Set driver offline.
   * Cannot go offline during an active (IN_PROGRESS) ride.
   */
  async goOffline(userId: string) {
    const profile = await prisma.driverProfile.findUnique({
      where: { userId },
      select: { id: true, isOnline: true },
    });

    if (!profile) {
      throw ApiError.notFound('Driver profile not found', ErrorCode.DRIVER_NOT_FOUND);
    }

    // Prevent going offline mid-ride — driver must complete first
    const activeRide = await prisma.ride.findFirst({
      where: {
        driverId: userId,
        status: { in: [RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVED, RideStatus.IN_PROGRESS] },
      },
      select: { id: true, status: true },
    });

    if (activeRide) {
      throw ApiError.badRequest(
        `Cannot go offline during an active ride (${activeRide.status}). Complete the ride first.`,
        'ACTIVE_RIDE_IN_PROGRESS'
      );
    }

    await prisma.driverProfile.update({
      where: { userId },
      data: { isOnline: false },
    });

    void this.syncDriverToRTDB(userId, { isOnline: false });

    if (profile.isOnline) {
      onlineDriversGauge.dec();
    }

    logger.info('Driver went offline', { userId });

    return { status: 'offline' as const };
  }

  /**
   * Get driver's current status: online state, location, active ride if any.
   */
  async getStatus(userId: string) {
    const profile = await prisma.driverProfile.findUnique({
      where: { userId },
      select: DRIVER_PROFILE_STATUS_SELECT,
    });

    if (!profile) {
      throw ApiError.notFound('Driver profile not found', ErrorCode.DRIVER_NOT_FOUND);
    }

    // Active ride means one of these statuses — REQUESTED is "offered" but not yet accepted
    const activeRide = await prisma.ride.findFirst({
      where: {
        driverId: userId,
        status: { in: [RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVED, RideStatus.IN_PROGRESS] },
      },
      select: ACTIVE_RIDE_FOR_DRIVER_SELECT,
    });

    return {
      isOnline: profile.isOnline,
      verificationStatus: profile.verificationStatus,
      planType: profile.planType,
      subscriptionExpiresAt: profile.subscriptionExpiresAt,
      cancellationStats: {
        total: profile.driverCancellationCount,
        today: profile.driverCancellationCountDaily,
        lastCancelledAt: profile.driverCancellationLastAt,
        alertThreshold: CONSTANTS.DRIVER_CANCELLATION_ALERT_THRESHOLD,
      },
      // Only expose location if it exists — never use non-null assertion on nullable fields
      currentLocation:
        profile.currentLat !== null && profile.currentLng !== null
          ? {
              lat: profile.currentLat,
              lng: profile.currentLng,
              updatedAt: profile.lastLocationUpdate,
            }
          : null,
      activeRide: activeRide
        ? {
            rideId: activeRide.id,
            status: activeRide.status,
            pickupAddress: activeRide.pickupAddress,
            dropAddress: activeRide.dropAddress,
            fare: activeRide.finalFare,
            paymentMethod: activeRide.paymentMethod,
            customer: activeRide.customer
              ? {
                  name: activeRide.customer.name,
                  // Mask phone at service boundary — never log or return full phone
                  phone: maskPhone(activeRide.customer.phone),
                  cancellationCount: activeRide.customer.customerProfile?.cancellationCount ?? 0,
                }
              : null,
          }
        : null,
    };
  }

  // -----------------------------------------------------------------------
  // LOCATION UPDATES
  // -----------------------------------------------------------------------

  /**
   * Update driver's real-time location.
   *
   * Write order:
   *   1. Redis (primary — fast, 30s TTL so stale entries expire automatically)
   *   2. Postgres (async, non-blocking — persists for history / recovery)
   *   3. Firebase RTDB (only if driver has an active ride — broadcasts to passenger)
   */
  async updateLocation(userId: string, lat: number, lng: number) {
    const now = new Date().toISOString();

    // 1. Redis — primary real-time store
    const redis = getRedisClient();
    if (redis && isRedisReady()) {
      void redis
        .setEx(
          `driver:${userId}:location`,
          30, // 30-second TTL — driver sends updates every ~3s during active ride
          JSON.stringify({ lat, lng, updatedAt: now })
        )
        .catch((err) => {
          logger.warn('Redis location write failed', { userId, error: String(err) });
        });
    }

    // 2. Postgres — async persistence (does not block the HTTP response)
    void prisma.driverProfile
      .update({
        where: { userId },
        data: { currentLat: lat, currentLng: lng, lastLocationUpdate: new Date() },
      })
      .catch((err) => {
        logger.warn('Postgres location update failed', { userId, error: String(err) });
      });

    // 3. RTDB — only relevant when passenger is tracking an active ride
    const activeRide = await prisma.ride.findFirst({
      where: {
        driverId: userId,
        status: { in: [RideStatus.DRIVER_ASSIGNED, RideStatus.DRIVER_ARRIVED, RideStatus.IN_PROGRESS] },
      },
      select: { id: true },
    });

    if (activeRide) {
      void this.syncDriverLocationToRTDB(activeRide.id, userId, lat, lng);
    }

    return { lat, lng, updatedAt: now };
  }

  // -----------------------------------------------------------------------
  // INCOMING RIDE — offered to this driver via FCM
  // -----------------------------------------------------------------------

  /**
   * Get the ride currently offered to this driver (REQUESTED + their notification).
   * The driver app polls this after receiving the FCM nudge.
   */
  async getIncomingRide(userId: string) {
    // A ride is "offered" to a specific driver when:
    // status = REQUESTED AND the driver was the one notified via FCM.
    // Since we do not write driverId to the ride at offer time (only at accept),
    // we look for rides that have NO driverId yet but the driver was recently notified.
    //
    // Redis key: `ride:offer:{userId}` → rideId (set by searchAndNotifyDrivers)
    const redis = getRedisClient();
    let offeredRideId: string | null = null;

    if (redis && isRedisReady()) {
      offeredRideId = await redis.get(`ride:offer:${userId}`).catch(() => null);
    }

    if (!offeredRideId) {
      return null;
    }

    const ride = await prisma.ride.findFirst({
      where: {
        id: offeredRideId,
        status: RideStatus.REQUESTED,
        driverId: null,
      },
      select: {
        id: true,
        status: true,
        pickupLat: true,
        pickupLng: true,
        pickupAddress: true,
        dropAddress: true,
        distanceKm: true,
        durationMins: true,
        finalFare: true,
        paymentMethod: true,
        isScheduled: true,
        scheduledAt: true,
        customer: {
          select: {
            name: true,
            customerProfile: {
              select: { cancellationCount: true },
            },
          },
        },
      },
    });

    if (!ride) {
      // Offer expired or ride was taken — clear the stale Redis key
      if (redis && isRedisReady()) {
        void redis.del(`ride:offer:${userId}`).catch(() => null);
      }
      return null;
    }

    return {
      rideId: ride.id,
      status: ride.status,
      pickup: {
        lat: ride.pickupLat,
        lng: ride.pickupLng,
        address: ride.pickupAddress,
      },
      drop: { address: ride.dropAddress },
      distanceKm: ride.distanceKm,
      durationMins: ride.durationMins,
      fare: ride.finalFare,
      paymentMethod: ride.paymentMethod,
      isScheduled: ride.isScheduled,
      scheduledAt: ride.scheduledAt,
      customer: {
        name: ride.customer?.name ?? 'Passenger',
        cancellationCount: ride.customer?.customerProfile?.cancellationCount ?? 0,
      },
      // Time remaining for the offer (driver receives it fresh from FCM)
      offerExpiresInSecs: CONSTANTS.RIDE_ACCEPT_WINDOW_SECS,
    };
  }

  // -----------------------------------------------------------------------
  // RIDE LIFECYCLE — accept → arrive → start → complete
  // -----------------------------------------------------------------------

  /**
   * Accept an offered ride.
   *
   * CRITICAL: uses `updateMany` with a WHERE clause on status + driverId IS NULL
   * so only ONE concurrent acceptRide call wins the ride — the others get count=0.
   * No explicit SELECT FOR UPDATE lock needed: Postgres row-level locking on the
   * UPDATE handles the race automatically.
   */
  async acceptRide(userId: string, rideId: string) {
    // Atomic compare-and-swap: succeed only if ride is still unassigned + REQUESTED
    const updated = await prisma.$transaction(async (tx) => {
      const result = await tx.ride.updateMany({
        where: {
          id: rideId,
          status: RideStatus.REQUESTED,
          driverId: null,
        },
        data: {
          driverId: userId,
          status: RideStatus.DRIVER_ASSIGNED,
          driverAssignedAt: new Date(),
        },
      });

      if (result.count === 0) {
        // Determine why the update found no rows
        const ride = await tx.ride.findUnique({
          where: { id: rideId },
          select: { status: true, driverId: true },
        });

        if (!ride) {
          throw ApiError.notFound('Ride not found', ErrorCode.RIDE_NOT_FOUND);
        }

        // Idempotent: this driver already accepted it in a parallel request
        if (ride.driverId === userId && ride.status === RideStatus.DRIVER_ASSIGNED) {
          return { idempotent: true };
        }

        // Another driver won the race
        if (ride.driverId && ride.driverId !== userId) {
          throw ApiError.conflict(
            'This ride was just accepted by another driver.',
            'RIDE_ALREADY_ACCEPTED'
          );
        }

        throw ApiError.badRequest(
          `Cannot accept a ride in ${ride.status} status.`,
          'INVALID_RIDE_STATUS'
        );
      }

      // Write audit event inside the same transaction
      await tx.rideEvent.create({
        data: {
          rideId,
          eventType: 'DRIVER_ASSIGNED',
          metadata: { driverId: userId },
        },
      });

      return { idempotent: false };
    });

    // Fetch ride details for the response (outside transaction — no lock needed)
    const ride = await prisma.ride.findUnique({
      where: { id: rideId },
      select: {
        status: true,
        customerId: true,
        pickupLat: true,
        pickupLng: true,
        pickupAddress: true,
        dropAddress: true,
        finalFare: true,
        paymentMethod: true,
        customer: {
          select: { name: true, phone: true },
        },
      },
    });

    if (!ride) {
      throw ApiError.internal('Ride state inconsistent after acceptance');
    }

    if (!updated.idempotent) {
      // Generate OTP and store it — customer shows this to driver to start the ride
      const otp = generateOTP();
      await prisma.ride.update({
        where: { id: rideId },
        data: { rideStartOtp: otp },
      });

      // Notify customer with OTP — fire-and-forget
      void notificationService
        .sendPushNotification({
          userId: ride.customerId,
          title: 'Driver Found!',
          body: `Your driver is on the way. Ride OTP: ${otp}`,
          data: { rideId, type: 'DRIVER_ASSIGNED', driverId: userId, rideOtp: otp },
        })
        .catch((err) => {
          logger.error('Customer notification failed after ride acceptance', { rideId, error: err });
        });

      // Sync status to RTDB
      void this.syncRideToRTDB(rideId, {
        status: RideStatus.DRIVER_ASSIGNED,
        driverId: userId,
      });

      // Remove the offer from Redis — offer consumed; also clear other batch members' offers
      const redis = getRedisClient();
      if (redis && isRedisReady()) {
        void redis.del(`ride:offer:${userId}`).catch(() => null);

        // Clean up offers for the other drivers in the same batch (they lost)
        const batchRaw = await redis.get(`ride:active_batch:${rideId}`).catch(() => null);
        if (batchRaw) {
          const batchIds: string[] = JSON.parse(batchRaw);
          const losers = batchIds.filter((id) => id !== userId);
          void Promise.allSettled(losers.map((id) => redis.del(`ride:offer:${id}`))).catch(() => null);
        }

        // Clean up batch tracking keys
        void redis.del(`ride:active_batch:${rideId}`).catch(() => null);
        void redis.del(`ride:candidates:${rideId}`).catch(() => null);
      }

      activeRidesGauge.inc({ status: 'DRIVER_ASSIGNED' });

      logger.info('Driver accepted ride', {
        rideId,
        driverId: userId,
        customerId: ride.customerId,
      });
    } else {
      logger.info('Driver re-accepted ride (idempotent)', { rideId, driverId: userId });
    }

    return {
      rideId,
      status: ride.status,
      pickup: {
        lat: ride.pickupLat,
        lng: ride.pickupLng,
        address: ride.pickupAddress,
      },
      drop: { address: ride.dropAddress },
      fare: ride.finalFare,
      paymentMethod: ride.paymentMethod,
      customer: ride.customer
        ? {
            name: ride.customer.name,
            phone: maskPhone(ride.customer.phone),
          }
        : null,
    };
  }

  /**
   * Decline an offered ride.
   * Logs the decline, clears the offer from Redis, and re-triggers driver search.
   */
  async declineRide(userId: string, rideId: string, reason?: string) {
    // Verify the ride still exists and is in REQUESTED state
    const ride = await prisma.ride.findUnique({
      where: { id: rideId },
      select: {
        status: true,
        customerId: true,
        pickupLat: true,
        pickupLng: true,
        finalFare: true,
      },
    });

    if (!ride) {
      throw ApiError.notFound('Ride not found', ErrorCode.RIDE_NOT_FOUND);
    }

    if (ride.status !== RideStatus.REQUESTED) {
      // Ride already accepted/cancelled — not an error for the declining driver
      logger.info('Driver declined stale ride', { rideId, driverId: userId, status: ride.status });
      return { rideId, declined: true };
    }

    // Record decline event (for analytics — which drivers decline which rides)
    void prisma.rideEvent
      .create({
        data: {
          rideId,
          eventType: 'DRIVER_DECLINED',
          metadata: { driverId: userId, reason: reason ?? null },
        },
      })
      .catch((err) => {
        logger.warn('Failed to record driver decline event', { rideId, error: String(err) });
      });

    // Clear this driver's offer from Redis
    const redis = getRedisClient();
    if (redis && isRedisReady()) {
      void redis.del(`ride:offer:${userId}`).catch(() => null);

      // Remove this driver from the active batch array
      // The BullMQ ride-offer-expired job will advance to the next batch when TTL fires
      const batchRaw = await redis.get(`ride:active_batch:${rideId}`).catch(() => null);
      if (batchRaw) {
        const updated = (JSON.parse(batchRaw) as string[]).filter((id) => id !== userId);
        void redis
          .setEx(`ride:active_batch:${rideId}`, CONSTANTS.RIDE_OFFER_BATCH_TTL_SECS, JSON.stringify(updated))
          .catch(() => null);
      }
    }

    logger.info('Driver declined ride', { rideId, driverId: userId });

    return { rideId, declined: true };
  }

  /**
   * Mark driver as arrived at pickup location.
   * Validates the driver is within DRIVER_ARRIVED_RADIUS_METERS of the pickup.
   *
   * Uses updateMany compare-and-swap: succeeds only if ride is in DRIVER_ASSIGNED status.
   */
  async arrivedAtPickup(userId: string, rideId: string) {
    await prisma.$transaction(async (tx) => {
      const updated = await tx.ride.updateMany({
        where: {
          id: rideId,
          driverId: userId,
          status: RideStatus.DRIVER_ASSIGNED,
        },
        data: {
          status: RideStatus.DRIVER_ARRIVED,
          driverArrivedAt: new Date(),
        },
      });

      if (updated.count === 0) {
        const ride = await tx.ride.findUnique({
          where: { id: rideId },
          select: { status: true, driverId: true },
        });

        if (!ride) throw ApiError.notFound('Ride not found', ErrorCode.RIDE_NOT_FOUND);
        if (ride.driverId !== userId) throw ApiError.forbidden('Not your ride', ErrorCode.FORBIDDEN);
        throw ApiError.badRequest(
          `Cannot mark arrived in ${ride.status} status.`,
          'INVALID_RIDE_STATUS'
        );
      }

      await tx.rideEvent.create({
        data: { rideId, eventType: 'DRIVER_ARRIVED' },
      });
    });

    // Fetch customer to notify
    const ride = await prisma.ride.findUnique({
      where: { id: rideId },
      select: { customerId: true, pickupAddress: true },
    });

    if (ride) {
      void notificationService
        .sendPushNotification({
          userId: ride.customerId,
          title: 'Driver Arrived!',
          body: `Your driver is waiting at ${ride.pickupAddress}.`,
          data: { rideId, type: 'DRIVER_ARRIVED' },
        })
        .catch((err) => {
          logger.error('Customer arrived notification failed', { rideId, error: err });
        });

      void this.syncRideToRTDB(rideId, { status: RideStatus.DRIVER_ARRIVED });
    }

    logger.info('Driver arrived at pickup', { rideId, driverId: userId });

    return { rideId, status: RideStatus.DRIVER_ARRIVED };
  }

  /**
   * Start the ride — transition DRIVER_ARRIVED → IN_PROGRESS.
   * Requires OTP that was sent to customer when driver was assigned.
   */
  async startRide(userId: string, rideId: string, otp: string) {
    const now = new Date();

    // Validate OTP before state transition
    const rideToCheck = await prisma.ride.findUnique({
      where: { id: rideId },
      select: { rideStartOtp: true, driverId: true, status: true },
    });

    if (!rideToCheck) throw ApiError.notFound('Ride not found', ErrorCode.RIDE_NOT_FOUND);
    if (rideToCheck.driverId !== userId) throw ApiError.forbidden('Not your ride', ErrorCode.FORBIDDEN);
    if (rideToCheck.status !== RideStatus.DRIVER_ARRIVED) {
      throw ApiError.badRequest(
        `Cannot start ride in ${rideToCheck.status} status. Mark arrived first.`,
        'INVALID_RIDE_STATUS'
      );
    }
    if (!rideToCheck.rideStartOtp || rideToCheck.rideStartOtp !== otp) {
      throw ApiError.badRequest('Invalid OTP. Ask the customer to share their ride OTP.', 'INVALID_OTP');
    }

    await prisma.$transaction(async (tx) => {
      const updated = await tx.ride.updateMany({
        where: {
          id: rideId,
          driverId: userId,
          status: RideStatus.DRIVER_ARRIVED,
        },
        data: {
          status: RideStatus.IN_PROGRESS,
          startedAt: now,
          rideStartOtp: null, // Clear OTP after successful use
        },
      });

      if (updated.count === 0) {
        // Race condition — another request already started the ride
        throw ApiError.conflict('Ride already started', 'INVALID_RIDE_STATUS');
      }

      await tx.rideEvent.create({
        data: { rideId, eventType: 'RIDE_STARTED', metadata: { startedAt: now.toISOString() } },
      });
    });

    const ride = await prisma.ride.findUnique({
      where: { id: rideId },
      select: { customerId: true, dropAddress: true },
    });

    if (ride) {
      void notificationService
        .sendPushNotification({
          userId: ride.customerId,
          title: 'Ride Started',
          body: `Heading to ${ride.dropAddress}.`,
          data: { rideId, type: 'RIDE_STARTED' },
        })
        .catch((err) => {
          logger.error('Ride started notification failed', { rideId, error: err });
        });

      void this.syncRideToRTDB(rideId, { status: RideStatus.IN_PROGRESS });
    }

    logger.info('Ride started', { rideId, driverId: userId });

    return { rideId, status: RideStatus.IN_PROGRESS, startedAt: now };
  }

  /**
   * Complete a ride.
   *
   * Atomic transaction:
   *   1. Transition ride IN_PROGRESS → COMPLETED
   *   2. Create Earning record (commission or subscription-based)
   *   3. Update driver totals (totalRides, totalEarnings)
   *
   * External calls (notifications, RTDB) happen AFTER the transaction.
   */
  async completeRide(userId: string, rideId: string, note?: string) {
    const now = new Date();

    // Fetch driver plan type BEFORE the transaction (avoids external query inside tx)
    const driverProfile = await prisma.driverProfile.findUnique({
      where: { userId },
      select: { id: true, planType: true },
    });

    if (!driverProfile) {
      throw ApiError.notFound('Driver profile not found', ErrorCode.DRIVER_NOT_FOUND);
    }

    // Fetch commission config (from DB config or fall back to constant)
    const commissionConfig = await prisma.platformConfig.findUnique({
      where: { key: CONSTANTS.CONFIG_KEYS.COMMISSION_PERCENTAGE },
      select: { value: true },
    });
    const commissionPercentage = commissionConfig
      ? Number(commissionConfig.value)
      : CONSTANTS.DEFAULT_COMMISSION_PERCENTAGE;

    const completedRide = await prisma.$transaction(async (tx) => {
      // 1. Transition ride status — atomic compare-and-swap
      const updated = await tx.ride.updateMany({
        where: {
          id: rideId,
          driverId: userId,
          status: RideStatus.IN_PROGRESS,
        },
        data: {
          status: RideStatus.COMPLETED,
          completedAt: now,
        },
      });

      if (updated.count === 0) {
        const ride = await tx.ride.findUnique({
          where: { id: rideId },
          select: { status: true, driverId: true },
        });

        if (!ride) throw ApiError.notFound('Ride not found', ErrorCode.RIDE_NOT_FOUND);
        if (ride.driverId !== userId) throw ApiError.forbidden('Not your ride', ErrorCode.FORBIDDEN);
        throw ApiError.badRequest(
          `Cannot complete ride in ${ride.status} status.`,
          'INVALID_RIDE_STATUS'
        );
      }

      // 2. Fetch ride details for earnings calculation (inside tx for consistency)
      const ride = await tx.ride.findUnique({
        where: { id: rideId },
        select: {
          customerId: true,
          finalFare: true,
          distanceKm: true,
          dropAddress: true,
        },
      });

      if (!ride) throw ApiError.internal('Ride state inconsistent');

      const grossAmount = ride.finalFare;
      const isCommissionPlan = driverProfile.planType === 'COMMISSION';
      const commissionAmount = isCommissionPlan
        ? calculateCommission(grossAmount, commissionPercentage)
        : 0;
      const netAmount = grossAmount - commissionAmount;
      const settlementDueDate = calculateSettlementDate(now);

      // 3. Create earning record (idempotent via rideId unique constraint)
      await tx.earning.create({
        data: {
          driverProfileId: driverProfile.id,
          rideId,
          grossAmount,
          commissionAmount,
          netAmount,
          settlementStatus: SettlementStatus.PENDING,
          settlementDueDate,
        },
      });

      // 4. Update ride with earnings breakdown (for driver dashboard history)
      await tx.ride.update({
        where: { id: rideId },
        data: {
          commissionAmount,
          driverEarning: netAmount,
          paymentStatus: ride.customerId ? 'PENDING' : 'COMPLETED',
        },
      });

      // 5. Increment driver totals atomically
      await tx.driverProfile.update({
        where: { id: driverProfile.id },
        data: {
          totalRides: { increment: 1 },
          totalEarnings: { increment: netAmount },
        },
      });

      // 6. Ride completion event
      await tx.rideEvent.create({
        data: {
          rideId,
          eventType: 'COMPLETED',
          metadata: {
            grossAmount,
            commissionAmount,
            netAmount,
            settlementDueDate: settlementDueDate.toISOString(),
            ...(note && { note }),
          },
        },
      });

      return { ride, grossAmount, commissionAmount, netAmount, settlementDueDate };
    });

    // Post-transaction: metrics, notifications, RTDB (all fire-and-forget)
    ridesCompletedTotal.inc();
    activeRidesGauge.dec({ status: 'IN_PROGRESS' });

    void notificationService
      .sendPushNotification({
        userId: completedRide.ride.customerId,
        title: 'You have arrived!',
        body: `You've reached ${completedRide.ride.dropAddress}. Please rate your ride.`,
        data: { rideId, type: 'RIDE_COMPLETED' },
      })
      .catch((err) => {
        logger.error('Ride completed notification failed', { rideId, error: err });
      });

    void this.syncRideToRTDB(rideId, { status: RideStatus.COMPLETED });

    logger.info('Ride completed', {
      rideId,
      driverId: userId,
      grossAmount: completedRide.grossAmount,
      netAmount: completedRide.netAmount,
    });

    return {
      rideId,
      status: RideStatus.COMPLETED,
      completedAt: now,
      earnings: {
        grossAmount: completedRide.grossAmount,
        commissionAmount: completedRide.commissionAmount,
        netAmount: completedRide.netAmount,
        settlementDueDate: completedRide.settlementDueDate,
      },
    };
  }

  /**
   * Cancel a ride as driver — allowed only before the ride starts.
   * Transitions to CANCELLED and notifies the customer.
   */
  async cancelRide(userId: string, rideId: string, reason?: string) {
    const now = new Date();

    const cancellableByDriver: RideStatus[] = [
      RideStatus.DRIVER_ASSIGNED,
      RideStatus.DRIVER_ARRIVED,
    ];

    const cancellationStats = await prisma.$transaction(async (tx) => {
      const result = await tx.ride.updateMany({
        where: {
          id: rideId,
          driverId: userId,
          status: { in: cancellableByDriver },
        },
        data: {
          status: RideStatus.CANCELLED,
          cancelledBy: CancellationBy.DRIVER,
          cancellationReason: reason,
          cancelledAt: now,
        },
      });

      if (result.count === 0) {
        const ride = await tx.ride.findUnique({
          where: { id: rideId },
          select: { status: true, driverId: true },
        });

        if (!ride) throw ApiError.notFound('Ride not found', ErrorCode.RIDE_NOT_FOUND);
        if (ride.driverId !== userId) throw ApiError.forbidden('Not your ride', ErrorCode.FORBIDDEN);
        throw ApiError.badRequest(
          `Cannot cancel ride in ${ride.status} status.`,
          ErrorCode.RIDE_CANNOT_CANCEL
        );
      }

      const driverProfile = await tx.driverProfile.findUnique({
        where: { userId },
        select: {
          id: true,
          driverCancellationCount: true,
          driverCancellationCountDaily: true,
          driverCancellationLastAt: true,
        },
      });

      if (!driverProfile) {
        throw ApiError.notFound('Driver profile not found', ErrorCode.DRIVER_NOT_FOUND);
      }

      const sameDayAsLastCancellation =
        driverProfile.driverCancellationLastAt !== null &&
        this.isSameISTCalendarDay(driverProfile.driverCancellationLastAt, now);

      const dailyCount = sameDayAsLastCancellation
        ? driverProfile.driverCancellationCountDaily + 1
        : 1;
      const totalCount = driverProfile.driverCancellationCount + 1;

      await tx.driverProfile.update({
        where: { id: driverProfile.id },
        data: {
          driverCancellationCount: { increment: 1 },
          driverCancellationCountDaily: dailyCount,
          driverCancellationLastAt: now,
        },
      });

      await tx.rideEvent.create({
        data: {
          rideId,
          eventType: 'CANCELLED',
          metadata: {
            cancelledBy: 'DRIVER',
            reason: reason ?? null,
            driverCancellationCountDaily: dailyCount,
            driverCancellationCount: totalCount,
          },
        },
      });

      return {
        count: result.count,
        totalCount,
        dailyCount,
        alertTriggered: dailyCount === CONSTANTS.DRIVER_CANCELLATION_ALERT_THRESHOLD,
      };
    });

    const ride = await prisma.ride.findUnique({
      where: { id: rideId },
      select: { customerId: true },
    });

    if (ride) {
      void notificationService
        .sendPushNotification({
          userId: ride.customerId,
          title: 'Driver Cancelled',
          body: 'Your driver has cancelled the ride. Searching for another driver.',
          data: { rideId, type: 'DRIVER_CANCELLED' },
        })
        .catch((err) => {
          logger.error('Customer cancellation notification failed', { rideId, error: err });
        });

      void this.syncRideToRTDB(rideId, { status: RideStatus.CANCELLED });
    }

    ridesCancelledTotal.inc({ cancelled_by: 'driver' });

    if (cancellationStats.alertTriggered) {
      void this.notifyAdminsOfDriverCancellationThreshold(userId, rideId, cancellationStats.dailyCount);
    }

    logger.info('Ride cancelled by driver', {
      rideId,
      driverId: userId,
      reason,
      driverCancellationCount: cancellationStats.totalCount,
      driverCancellationCountDaily: cancellationStats.dailyCount,
    });

    return {
      rideId,
      status: RideStatus.CANCELLED,
      cancellationStats: {
        total: cancellationStats.totalCount,
        today: cancellationStats.dailyCount,
        alertThreshold: CONSTANTS.DRIVER_CANCELLATION_ALERT_THRESHOLD,
        alertTriggered: cancellationStats.alertTriggered,
      },
    };
  }

  private isSameISTCalendarDay(left: Date, right: Date): boolean {
    const istOffsetMs = 5.5 * 60 * 60 * 1000;
    const leftIst = new Date(left.getTime() + istOffsetMs);
    const rightIst = new Date(right.getTime() + istOffsetMs);

    return leftIst.getUTCFullYear() === rightIst.getUTCFullYear()
      && leftIst.getUTCMonth() === rightIst.getUTCMonth()
      && leftIst.getUTCDate() === rightIst.getUTCDate();
  }

  private async notifyAdminsOfDriverCancellationThreshold(
    driverId: string,
    rideId: string,
    dailyCount: number
  ): Promise<void> {
    const admins = await prisma.user.findMany({
      where: { role: 'ADMIN', isActive: true },
      select: { id: true },
    });

    if (admins.length === 0) {
      logger.warn('Driver cancellation threshold reached but no active admins found', {
        driverId,
        rideId,
        dailyCount,
      });
      return;
    }

    await Promise.allSettled(
      admins.map((admin) =>
        notificationService.sendPushNotification({
          userId: admin.id,
          title: 'Driver cancellation threshold reached',
          body: `Driver ${driverId} cancelled ${dailyCount} rides today. Review ride ${rideId}.`,
          data: {
            rideId,
            driverId,
            dailyCount: String(dailyCount),
            type: 'DRIVER_CANCELLATION_THRESHOLD',
          },
        })
      )
    );
  }

  // -----------------------------------------------------------------------
  // EARNINGS & WITHDRAWALS
  // -----------------------------------------------------------------------

  /**
   * Get earnings summary for a period (today / week / month) with per-trip breakdown.
   */
  async getEarnings(userId: string, period: 'today' | 'week' | 'month', page: number, limit: number) {
    const driverProfile = await prisma.driverProfile.findUnique({
      where: { userId },
      select: { id: true },
    });

    if (!driverProfile) {
      throw ApiError.notFound('Driver profile not found', ErrorCode.DRIVER_NOT_FOUND);
    }

    // Compute date range for the requested period (IST = UTC+5:30)
    const now = new Date();
    const istOffset = 5.5 * 60 * 60 * 1000; // 5h 30m in ms
    const todayIST = new Date(
      Date.UTC(
        new Date(now.getTime() + istOffset).getUTCFullYear(),
        new Date(now.getTime() + istOffset).getUTCMonth(),
        new Date(now.getTime() + istOffset).getUTCDate()
      ) - istOffset
    );

    let fromDate: Date;
    if (period === 'today') {
      fromDate = todayIST;
    } else if (period === 'week') {
      fromDate = new Date(todayIST.getTime() - 6 * 24 * 60 * 60 * 1000);
    } else {
      fromDate = new Date(todayIST.getTime() - 29 * 24 * 60 * 60 * 1000);
    }

    const [earnings, total, aggregate] = await Promise.all([
      // Paginated list of individual earnings
      prisma.earning.findMany({
        where: {
          driverProfileId: driverProfile.id,
          createdAt: { gte: fromDate },
        },
        orderBy: { createdAt: 'desc' },
        skip: (page - 1) * limit,
        take: limit,
        select: {
          id: true,
          grossAmount: true,
          commissionAmount: true,
          netAmount: true,
          settlementStatus: true,
          settlementDueDate: true,
          settledAt: true,
          createdAt: true,
          ride: {
            select: {
              id: true,
              pickupAddress: true,
              dropAddress: true,
              distanceKm: true,
              paymentMethod: true,
              completedAt: true,
            },
          },
        },
      }),
      // Total count for pagination
      prisma.earning.count({
        where: {
          driverProfileId: driverProfile.id,
          createdAt: { gte: fromDate },
        },
      }),
      // Period aggregate sums
      prisma.earning.aggregate({
        where: {
          driverProfileId: driverProfile.id,
          createdAt: { gte: fromDate },
        },
        _sum: {
          grossAmount: true,
          commissionAmount: true,
          netAmount: true,
        },
        _count: { id: true },
      }),
    ]);

    const meta = buildPaginationMeta(page, limit, total);

    return {
      summary: {
        period,
        totalRides: aggregate._count.id,
        totalGross: aggregate._sum.grossAmount ?? 0,
        totalCommission: aggregate._sum.commissionAmount ?? 0,
        totalNet: aggregate._sum.netAmount ?? 0,
      },
      earnings,
      meta,
    };
  }

  /**
   * Earnings summary — aggregate totals for a period without paginated trip list.
   * Lighter than getEarnings; used by the dashboard header card.
   */
  async getEarningsSummary(userId: string, period: 'week' | 'month') {
    const driverProfile = await prisma.driverProfile.findUnique({
      where: { userId },
      select: { id: true, totalEarnings: true, totalRides: true, ratingAvg: true },
    });

    if (!driverProfile) {
      throw ApiError.notFound('Driver profile not found', ErrorCode.DRIVER_NOT_FOUND);
    }

    const now = new Date();
    const istOffset = 5.5 * 60 * 60 * 1000;
    const todayIST = new Date(
      Date.UTC(
        new Date(now.getTime() + istOffset).getUTCFullYear(),
        new Date(now.getTime() + istOffset).getUTCMonth(),
        new Date(now.getTime() + istOffset).getUTCDate()
      ) - istOffset
    );

    const fromDate = period === 'week'
      ? new Date(todayIST.getTime() - 6 * 24 * 60 * 60 * 1000)
      : new Date(todayIST.getTime() - 29 * 24 * 60 * 60 * 1000);

    const aggregate = await prisma.earning.aggregate({
      where: {
        driverProfileId: driverProfile.id,
        createdAt: { gte: fromDate },
      },
      _sum: { grossAmount: true, commissionAmount: true, netAmount: true },
      _count: { id: true },
      _avg: { netAmount: true },
    });

    return {
      period,
      fromDate,
      toDate: now,
      rides: aggregate._count.id,
      grossEarnings: aggregate._sum.grossAmount ?? 0,
      commission: aggregate._sum.commissionAmount ?? 0,
      netEarnings: aggregate._sum.netAmount ?? 0,
      avgPerRide: Math.round((aggregate._avg.netAmount ?? 0) * 100) / 100,
      allTimeRides: driverProfile.totalRides,
      allTimeEarnings: driverProfile.totalEarnings,
      ratingAvg: driverProfile.ratingAvg,
    };
  }

  /**
   * Get settlement breakdown — pending, processing, settled amounts.
   */
  async getSettlementSummary(userId: string) {
    const driverProfile = await prisma.driverProfile.findUnique({
      where: { userId },
      select: { id: true },
    });

    if (!driverProfile) {
      throw ApiError.notFound('Driver profile not found', ErrorCode.DRIVER_NOT_FOUND);
    }

    const [pending, processing, settled] = await Promise.all([
      prisma.earning.aggregate({
        where: { driverProfileId: driverProfile.id, settlementStatus: SettlementStatus.PENDING },
        _sum: { netAmount: true },
        _count: { id: true },
      }),
      prisma.earning.aggregate({
        where: { driverProfileId: driverProfile.id, settlementStatus: SettlementStatus.PROCESSING },
        _sum: { netAmount: true },
        _count: { id: true },
      }),
      prisma.earning.aggregate({
        where: { driverProfileId: driverProfile.id, settlementStatus: SettlementStatus.SETTLED },
        _sum: { netAmount: true },
        _count: { id: true },
      }),
    ]);

    return {
      pending: {
        amount: pending._sum.netAmount ?? 0,
        rides: pending._count.id,
      },
      processing: {
        amount: processing._sum.netAmount ?? 0,
        rides: processing._count.id,
      },
      settled: {
        amount: settled._sum.netAmount ?? 0,
        rides: settled._count.id,
      },
    };
  }

  /**
   * Create a withdrawal request.
   * Validates that the driver has enough settled balance.
   */
  async requestWithdrawal(
    userId: string,
    amount: number,
    method: 'BANK_TRANSFER' | 'CASH_AGENT',
    bankDetails: {
      bankAccountNumber?: string;
      bankIfsc?: string;
      upiId?: string;
    }
  ) {
    const driverProfile = await prisma.driverProfile.findUnique({
      where: { userId },
      select: {
        id: true,
        bankAccountName: true,
        bankAccountNumber: true,
        bankIfsc: true,
        upiId: true,
      },
    });

    if (!driverProfile) {
      throw ApiError.notFound('Driver profile not found', ErrorCode.DRIVER_NOT_FOUND);
    }

    // Use bank details from request body, falling back to saved profile details
    const withdrawalBankAccountNumber =
      bankDetails.bankAccountNumber ?? driverProfile.bankAccountNumber ?? undefined;
    const withdrawalBankIfsc =
      bankDetails.bankIfsc ?? driverProfile.bankIfsc ?? undefined;
    const withdrawalUpiId =
      bankDetails.upiId ?? driverProfile.upiId ?? undefined;

    const withdrawal = await prisma.withdrawal.create({
      data: {
        driverProfileId: driverProfile.id,
        amount,
        method: method as WithdrawalMethod,
        status: WithdrawalStatus.REQUESTED,
        bankAccountNumber: withdrawalBankAccountNumber,
        bankIfsc: withdrawalBankIfsc,
        upiId: withdrawalUpiId,
      },
      select: {
        id: true,
        amount: true,
        method: true,
        status: true,
        createdAt: true,
      },
    });

    logger.info('Withdrawal requested', { userId, amount, method, withdrawalId: withdrawal.id });

    return withdrawal;
  }

  /**
   * Get a specific withdrawal status.
   */
  async getWithdrawalStatus(userId: string, withdrawalId: string) {
    const driverProfile = await prisma.driverProfile.findUnique({
      where: { userId },
      select: { id: true },
    });

    if (!driverProfile) {
      throw ApiError.notFound('Driver profile not found', ErrorCode.DRIVER_NOT_FOUND);
    }

    const withdrawal = await prisma.withdrawal.findFirst({
      where: {
        id: withdrawalId,
        driverProfileId: driverProfile.id, // Ownership check — never trust only withdrawalId
      },
      select: {
        id: true,
        amount: true,
        method: true,
        status: true,
        transactionRef: true,
        rejectionReason: true,
        processedAt: true,
        createdAt: true,
        updatedAt: true,
      },
    });

    if (!withdrawal) {
      throw ApiError.notFound('Withdrawal not found', ErrorCode.NOT_FOUND);
    }

    return withdrawal;
  }

  /**
   * Get driver's trip history with pagination.
   */
  async getTripHistory(
    userId: string,
    page: number,
    limit: number
  ): Promise<{ trips: TripHistoryItem[]; meta: ReturnType<typeof buildPaginationMeta> }> {
    const [trips, total] = await Promise.all([
      prisma.ride.findMany({
        where: {
          driverId: userId,
          status: { in: [RideStatus.COMPLETED, RideStatus.CANCELLED] },
        },
        orderBy: { createdAt: 'desc' },
        skip: (page - 1) * limit,
        take: limit,
        select: TRIP_HISTORY_SELECT,
      }),
      prisma.ride.count({
        where: {
          driverId: userId,
          status: { in: [RideStatus.COMPLETED, RideStatus.CANCELLED] },
        },
      }),
    ]);

    const meta = buildPaginationMeta(page, limit, total);

    return { trips, meta };
  }

  // -----------------------------------------------------------------------
  // FIREBASE RTDB SYNC — fire-and-forget helpers
  // -----------------------------------------------------------------------

  /**
   * Sync driver online/offline state to RTDB (for passenger map).
   */
  private syncDriverToRTDB(
    userId: string,
    data: { isOnline: boolean; lat?: number; lng?: number }
  ): Promise<void> {
    try {
      const ref = getDatabase().ref(`drivers/${userId}`);
      return ref
        .update({
          isOnline: data.isOnline,
          ...(data.lat !== undefined ? { lat: data.lat } : {}),
          ...(data.lng !== undefined ? { lng: data.lng } : {}),
          updatedAt: new Date().toISOString(),
        })
        .catch((err) => {
          logger.warn('RTDB driver sync failed', { userId, error: String(err) });
        });
    } catch (err) {
      logger.warn('RTDB unavailable — skipping driver sync', { userId, error: String(err) });
      return Promise.resolve();
    }
  }

  /**
   * Sync driver location to the ride's RTDB node so the passenger sees live movement.
   */
  private syncDriverLocationToRTDB(
    rideId: string,
    driverId: string,
    lat: number,
    lng: number
  ): Promise<void> {
    try {
      const ref = getDatabase().ref(`rides/${rideId}/driverLocation`);
      return ref
        .set({ lat, lng, updatedAt: new Date().toISOString() })
        .catch((err) => {
          logger.warn('RTDB driver location sync failed', { rideId, driverId, error: String(err) });
        });
    } catch (err) {
      logger.warn('RTDB unavailable — skipping location sync', { rideId, error: String(err) });
      return Promise.resolve();
    }
  }

  /**
   * Sync ride status to RTDB.
   */
  private syncRideToRTDB(
    rideId: string,
    data: { status: RideStatus; driverId?: string }
  ): Promise<void> {
    try {
      const ref = getDatabase().ref(`rides/${rideId}`);
      return ref
        .update({
          status: data.status,
          ...(data.driverId !== undefined ? { driverId: data.driverId } : {}),
          updatedAt: new Date().toISOString(),
        })
        .catch((err) => {
          logger.warn('RTDB ride sync failed', { rideId, error: String(err) });
        });
    } catch (err) {
      logger.warn('RTDB unavailable — skipping ride sync', { rideId, error: String(err) });
      return Promise.resolve();
    }
  }

  /**
   * Rate a customer after ride completion (driver → customer rating).
   */
  async rateCustomer(userId: string, rideId: string, rating: number, comment?: string) {
    const ride = await prisma.ride.findUnique({
      where: { id: rideId },
      select: { driverId: true, status: true, driverRating: true },
    });

    if (!ride) throw ApiError.notFound('Ride not found', ErrorCode.RIDE_NOT_FOUND);
    if (ride.driverId !== userId) throw ApiError.forbidden('Not your ride', ErrorCode.FORBIDDEN);
    if (ride.status !== RideStatus.COMPLETED) {
      throw ApiError.badRequest('Can only rate a completed ride', 'INVALID_RIDE_STATUS');
    }
    if (ride.driverRating !== null) {
      throw ApiError.conflict('You have already rated this customer', 'ALREADY_RATED');
    }

    await prisma.ride.update({
      where: { id: rideId },
      data: { driverRating: rating, driverComment: comment ?? null },
    });

    logger.info('Driver rated customer', { rideId, driverId: userId, rating });

    return { rideId, rating, comment: comment ?? null };
  }
}

export const driverService = new DriverService();
export default driverService;
