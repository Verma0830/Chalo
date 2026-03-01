// ============================================================
// Chalo Backend — Job Queue (BullMQ)
// Replaces setInterval for scheduled tasks — safe in multi-instance
// ============================================================

import { Queue, Worker } from 'bullmq';
import config from '../config';
import logger from '../config/logger';
import { authService } from '../services/auth.service';

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

const QUEUE_NAME = 'chalo-maintenance';

let maintenanceQueue: Queue | null = null;
let maintenanceWorker: Worker | null = null;

/**
 * Initialize BullMQ queue and worker for background jobs
 * Call once at server startup
 */
export async function initJobQueue(): Promise<void> {
  maintenanceQueue = new Queue(QUEUE_NAME, { connection });

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

  // Worker processes jobs
  maintenanceWorker = new Worker(
    QUEUE_NAME,
    async (job) => {
      switch (job.name) {
        case 'otp-cleanup': {
          const deleted = await authService.cleanupExpiredOTPs();
          return { deletedCount: deleted };
        }
        default:
          logger.warn('Unknown job type', { jobName: job.name });
          return;
      }
    },
    { connection, concurrency: 1 }
  );

  maintenanceWorker.on('completed', (job) => {
    logger.info('Job completed', { jobName: job?.name, jobId: job?.id });
  });

  maintenanceWorker.on('failed', (job, err) => {
    logger.error('Job failed', { jobName: job?.name, jobId: job?.id, error: err.message });
  });

  logger.info('BullMQ job queue initialized', { queue: QUEUE_NAME });
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
  logger.info('BullMQ job queue closed');
}
