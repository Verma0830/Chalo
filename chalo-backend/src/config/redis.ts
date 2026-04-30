// ============================================================
// Chalo Backend — Shared Redis Client (Singleton)
// Single connection used by rate limiter, idempotency, caching
// ============================================================

import { createClient, RedisClientType } from 'redis';
import config from './index';
import logger from './logger';

let redisClient: RedisClientType | null = null;
let redisReady = false;

/**
 * Connect the shared Redis client.
 * Call once at server startup — all subsystems share this connection.
 */
export async function connectRedis(): Promise<void> {
  if (config.isDev && !process.env.REDIS_URL) {
    logger.warn('REDIS_URL not set — running without Redis (dev only)');
    return;
  }

  try {
    redisClient = createClient({ url: config.redisUrl });

    redisClient.on('error', (err) => {
      logger.error('Redis client error', { error: err.message });
      redisReady = false;
    });

    redisClient.on('ready', () => {
      logger.info('Redis client connected');
      redisReady = true;
    });

    await redisClient.connect();
  } catch (error) {
    logger.error('Failed to connect shared Redis client', { error });
    // Continue without Redis — subsystems fall back to in-memory / no-op
  }
}

/**
 * Gracefully disconnect the shared Redis client.
 * Call during server shutdown.
 */
export async function disconnectRedis(): Promise<void> {
  if (redisClient) {
    await redisClient.quit();
    redisClient = null;
    redisReady = false;
    logger.info('Redis client disconnected');
  }
}

/**
 * Get the shared Redis client instance.
 * Returns null if Redis is not connected.
 */
export function getRedisClient(): RedisClientType | null {
  return redisClient;
}

/**
 * Check if the shared Redis client is connected and ready.
 */
export function isRedisReady(): boolean {
  return redisReady;
}

/**
 * Ping Redis — used by health checks.
 * Returns true if Redis responds within timeout.
 */
export async function pingRedis(): Promise<boolean> {
  if (!redisClient || !redisReady) return false;
  try {
    const result = await redisClient.ping();
    return result === 'PONG';
  } catch {
    return false;
  }
}
