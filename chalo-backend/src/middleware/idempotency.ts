// ============================================================
// Chalo Backend — Idempotency Middleware
// Prevents duplicate ride creation on client retries
// ============================================================

import { Request, Response, NextFunction } from 'express';
import { createClient, RedisClientType } from 'redis';
import config from '../config';
import logger from '../config/logger';

let redisClient: RedisClientType | null = null;
let redisReady = false;

// Idempotency key TTL: 24 hours
const IDEMPOTENCY_TTL_SECONDS = 86400;

/**
 * Initialize Redis for idempotency storage
 * Call during app startup
 */
export async function initIdempotencyRedis(): Promise<void> {
  if (config.isDev && !process.env.REDIS_URL) {
    logger.warn('Redis URL not configured — idempotency disabled (dev only)');
    return;
  }

  try {
    redisClient = createClient({ url: config.redisUrl });

    redisClient.on('error', (err) => {
      logger.error('Redis idempotency error', { error: err.message });
      redisReady = false;
    });

    redisClient.on('ready', () => {
      logger.info('Redis idempotency store connected');
      redisReady = true;
    });

    await redisClient.connect();
  } catch (error) {
    logger.error('Failed to connect Redis for idempotency', { error });
  }
}

/**
 * Disconnect idempotency Redis
 */
export async function disconnectIdempotencyRedis(): Promise<void> {
  if (redisClient) {
    await redisClient.quit();
    redisClient = null;
    redisReady = false;
  }
}

/**
 * Idempotency Middleware
 * 
 * Checks for `Idempotency-Key` header. If present:
 * - Returns cached response if key was seen before
 * - Stores new response for future duplicate requests
 * 
 * Use on POST endpoints that create resources (rides, payments)
 */
export function idempotencyMiddleware(
  req: Request,
  res: Response,
  next: NextFunction
): void {
  const idempotencyKey = req.headers['idempotency-key'] as string;

  // No idempotency key — proceed normally
  if (!idempotencyKey) {
    return next();
  }

  // Redis not available — proceed without idempotency
  if (!redisClient || !redisReady) {
    logger.warn('Idempotency check skipped — Redis unavailable');
    return next();
  }

  // Scope idempotency key to user to prevent cross-user collisions
  const userId = (req as { user?: { id?: string } }).user?.id || req.ip || 'anon';
  const cacheKey = `idempotency:${userId}:${idempotencyKey}`;

  // Check for existing response
  redisClient
    .get(cacheKey)
    .then((cached) => {
      if (cached) {
        // Return cached response
        logger.info('Idempotency hit — returning cached response', { key: idempotencyKey });
        const cachedResponse = JSON.parse(cached);
        res.status(cachedResponse.statusCode).json(cachedResponse.body);
        return;
      }

      // No cached response — proceed and capture response
      const originalJson = res.json.bind(res);

      res.json = function (body: unknown) {
        // Cache the response
        const toCache = {
          statusCode: res.statusCode,
          body,
        };

        redisClient!
          .setEx(cacheKey, IDEMPOTENCY_TTL_SECONDS, JSON.stringify(toCache))
          .catch((err) => {
            logger.error('Failed to cache idempotency response', { error: err });
          });

        return originalJson(body);
      };

      next();
    })
    .catch((err) => {
      logger.error('Idempotency check failed', { error: err });
      next();
    });
}

/**
 * Check if an idempotency key already exists
 * Useful for manual checks in services
 */
export async function hasIdempotencyKey(key: string): Promise<boolean> {
  if (!redisClient || !redisReady) return false;

  try {
    const exists = await redisClient.exists(`idempotency:${key}`);
    return exists === 1;
  } catch {
    return false;
  }
}
