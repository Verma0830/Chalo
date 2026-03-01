// ============================================================
// Chalo Backend — Job Queue (BullMQ)
// Replaces setInterval for scheduled tasks — safe in multi-instance
// ============================================================

import { Queue, Worker } from 'bullmq';
import config from '../config';
import logger from '../config/logger';
import { authService } from '../services/auth.service';
import CONSTANTS from '../utils/constants';

/**
 * Parse Redis URL into IORedis-compatible connection options
 */
function parseRedisUrl(url: string): { host: string; port: number } {
  const parsed = new URL(url);
  return {
    host: parsed.hostname || 'localhost',
    port: parseInt(parsed.port || '6379', 10),
  };
}

const connection = parseRedisUrl(config.redisUrl);

// -------------------------------------------------------
// Queue Definitions
// -------------------------------------------------------

const MAINTENANCE_QUEUE = 'chalo-maintenance';
const RIDES_QUEUE = 'chalo-rides';

let maintenanceQueue: Queue | null = null;
let maintenanceWorker: Worker | null = null;
let ridesQueue: Queue | null = null;
let ridesWorker: Worker | null = null;

// -------------------------------------------------------
// Ride offer expiry payload
// -------------------------------------------------------

export interface RideOfferExpiredPayload {
  rideId: string;
  nextBatchStart: number;   // index into candidates array for the next batch
  pickupLat: number;
  pickupLng: number;
  fare: number;
}

/**
 * Schedule a delayed job that fires when the current batch window expires.
 * If all drivers in the batch ignored the offer, the worker advances to the next batch.
 */
export async function scheduleRideOfferExpiry(payload: RideOfferExpiredPayload): Promise<void> {
  if (!ridesQueue) {
    logger.warn('Rides queue not initialized — skipping ride offer expiry scheduling', { rideId: payload.rideId });
    return;
  }
  await ridesQueue.add('ride-offer-expired', payload, {
    delay: CONSTANTS.RIDE_OFFER_BATCH_TTL_SECS * 1000,
    removeOnComplete: { count: 50 },
    removeOnFail: { count: 100 },
  });
}

/**
 * Initialize BullMQ queue and worker for background jobs
 * Call once at server startup
 */
export async function initJobQueue(): Promise<void> {
  // ---- Maintenance queue (OTP cleanup) ----
  maintenanceQueue = new Queue(MAINTENANCE_QUEUE, { connection });

  // Add repeating OTP cleanup job (every 24 hours)
  // BullMQ deduplicates repeating jobs by name — safe to call on every startup
  await maintenanceQueue.add(
    'otp-cleanup',
    {},
    {
      repeat: { every: 24 * 60 * 60 * 1000 }, // 24 hours
      removeOnComplete: { count: 10 },
      removeOnFail: { count: 50 },
    }
  );

  // Run once immediately on startup
  await maintenanceQueue.add('otp-cleanup', {}, {
    removeOnComplete: true,
    removeOnFail: { count: 50 },
  });

  // Worker processes maintenance jobs
  maintenanceWorker = new Worker(
    MAINTENANCE_QUEUE,
    async (job) => {
      switch (job.name) {
        case 'otp-cleanup': {
          const deleted = await authService.cleanupExpiredOTPs();
          return { deletedCount: deleted };
        }
        default:
          logger.warn('Unknown maintenance job type', { jobName: job.name });
          return;
      }
    },
    { connection, concurrency: 1 }
  );

  maintenanceWorker.on('completed', (job) => {
    logger.info('Maintenance job completed', { jobName: job?.name, jobId: job?.id });
  });

  maintenanceWorker.on('failed', (job, err) => {
    logger.error('Maintenance job failed', { jobName: job?.name, jobId: job?.id, error: err.message });
  });

  // ---- Rides queue (ride-offer-expired) ----
  ridesQueue = new Queue(RIDES_QUEUE, { connection });

  ridesWorker = new Worker(
    RIDES_QUEUE,
    async (job) => {
      switch (job.name) {
        case 'ride-offer-expired': {
          const { rideId, nextBatchStart, pickupLat, pickupLng, fare } = job.data as RideOfferExpiredPayload;

          // 1. DB check — if ride is no longer REQUESTED, another path resolved it
          const { default: prisma } = await import('../config/database');
          const { RideStatus } = await import('@prisma/client');
          const ride = await prisma.ride.findUnique({
            where: { id: rideId },
            select: { status: true },
          });

          if (!ride || ride.status !== RideStatus.REQUESTED) {
            logger.debug('ride-offer-expired: ride already resolved', { rideId, status: ride?.status });
            return;
          }

          // 2. Read candidate list from Redis
          const { getRedisClient, isRedisReady } = await import('../config/redis');
          const redis = getRedisClient();

          if (!redis || !isRedisReady()) {
            logger.warn('ride-offer-expired: Redis unavailable, skipping batch advance', { rideId });
            return;
          }

          const candidatesRaw = await redis.get(`ride:candidates:${rideId}`).catch(() => null);
          const allCandidateIds: string[] = candidatesRaw ? JSON.parse(candidatesRaw) : [];

          const nextBatch = allCandidateIds.slice(nextBatchStart, nextBatchStart + CONSTANTS.DRIVER_BROADCAST_SIZE);

          // 3. No more drivers — mark NO_DRIVER
          if (nextBatch.length === 0) {
            await prisma.ride.update({
              where: { id: rideId },
              data: {
                status: RideStatus.NO_DRIVER,
                rideEvents: {
                  create: {
                    eventType: 'NO_DRIVER_FOUND',
                    metadata: { reason: 'all_batches_exhausted', nextBatchStart },
                  },
                },
              },
            });

            // Notify customer
            const fullRide = await prisma.ride.findUnique({ where: { id: rideId }, select: { customerId: true } });
            if (fullRide) {
              const { notificationService } = await import('../services/notification.service');
              await notificationService.sendPushNotification({
                userId: fullRide.customerId,
                title: 'No riders available',
                body: 'No riders are available nearby. Please try again in a few minutes.',
                data: { rideId, type: 'NO_DRIVER' },
              });
            }

            // Sync to RTDB
            const { getDatabase } = await import('../config/firebase');
            const db = getDatabase();
            if (db) {
              void db.ref(`rides/${rideId}`).update({ status: RideStatus.NO_DRIVER }).catch(() => null);
            }

            // Cleanup Redis
            void redis.del(`ride:candidates:${rideId}`).catch(() => null);
            void redis.del(`ride:active_batch:${rideId}`).catch(() => null);

            logger.info('ride-offer-expired: no more candidates, marked NO_DRIVER', { rideId });
            return;
          }

          // 4. Dispatch the next batch
          const { rideService } = await import('../services/ride.service');
          await rideService.dispatchBatch(
            rideId,
            nextBatch,
            fare,
            pickupLat,
            pickupLng,
            nextBatchStart + CONSTANTS.DRIVER_BROADCAST_SIZE
          );

          logger.info('ride-offer-expired: advanced to next batch', {
            rideId,
            nextBatchStart,
            batchSize: nextBatch.length,
          });
          return;
        }

        default:
          logger.warn('Unknown rides job type', { jobName: job.name });
          return;
      }
    },
    { connection, concurrency: 5 }
  );

  ridesWorker.on('completed', (job) => {
    logger.info('Rides job completed', { jobName: job?.name, jobId: job?.id });
  });

  ridesWorker.on('failed', (job, err) => {
    logger.error('Rides job failed', { jobName: job?.name, jobId: job?.id, error: err.message });
  });

  logger.info('BullMQ job queues initialized', { queues: [MAINTENANCE_QUEUE, RIDES_QUEUE] });
}

/**
 * Gracefully close queue and worker connections
 * Call during server shutdown
 */
export async function closeJobQueue(): Promise<void> {
  if (maintenanceWorker) {
    await maintenanceWorker.close();
    maintenanceWorker = null;
  }
  if (maintenanceQueue) {
    await maintenanceQueue.close();
    maintenanceQueue = null;
  }
  if (ridesWorker) {
    await ridesWorker.close();
    ridesWorker = null;
  }
  if (ridesQueue) {
    await ridesQueue.close();
    ridesQueue = null;
  }
  logger.info('BullMQ job queues closed');
}
