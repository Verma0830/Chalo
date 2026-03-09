// ============================================================
// Chalo Backend — Rate Limiter Middleware
// Prevents abuse, brute force, and DDoS
// Uses Redis for distributed rate limiting across instances
// ============================================================

import rateLimit, { Options } from 'express-rate-limit';
import { getRedisClient, isRedisReady } from '../config/redis';
import logger from '../config/logger';

// -------------------------------------------------------
// Custom Redis Store for express-rate-limit
// -------------------------------------------------------

interface RedisStoreOptions {
  prefix: string;
  windowMs: number;
}

function createRedisStore(options: RedisStoreOptions) {
  const { prefix, windowMs } = options;

  return {
    // Each limiter instance namespaces keys with its own prefix, so counts are isolated.
    localKeys: true,
    async increment(key: string): Promise<{ totalHits: number; resetTime: Date }> {
      const client = getRedisClient();
      if (!client || !isRedisReady()) {
        // Fallback: allow request but log warning
        return { totalHits: 1, resetTime: new Date(Date.now() + windowMs) };
      }

      const redisKey = `${prefix}:${key}`;
      const now = Date.now();

      try {
        const result = await client.multi()
          .incr(redisKey)
          .pExpire(redisKey, windowMs)
          .exec();

        const totalHits = (result?.[0] as number) || 1;
        const ttl = await client.pTTL(redisKey);
        const resetTime = new Date(now + (ttl > 0 ? ttl : windowMs));

        return { totalHits, resetTime };
      } catch (error) {
        logger.error('Redis rate limit increment failed', { error });
        return { totalHits: 1, resetTime: new Date(now + windowMs) };
      }
    },

    async decrement(key: string): Promise<void> {
      const client = getRedisClient();
      if (!client || !isRedisReady()) return;

      try {
        const redisKey = `${prefix}:${key}`;
        await client.decr(redisKey);
      } catch (error) {
        logger.error('Redis rate limit decrement failed', { error });
      }
    },

    async resetKey(key: string): Promise<void> {
      const client = getRedisClient();
      if (!client || !isRedisReady()) return;

      try {
        const redisKey = `${prefix}:${key}`;
        await client.del(redisKey);
      } catch (error) {
        logger.error('Redis rate limit reset failed', { error });
      }
    },
  };
}

// -------------------------------------------------------
// Rate Limiter Factory
// -------------------------------------------------------

interface RateLimitConfig {
  windowMs: number;
  max: number;
  message?: string;
  prefix?: string;
}

/**
 * Create a rate limiter with Redis store (production) or memory (dev fallback)
 */
export function createRateLimiter(options: RateLimitConfig) {
  const store = createRedisStore({
    prefix: options.prefix || 'rl',
    windowMs: options.windowMs,
  });

  return rateLimit({
    windowMs: options.windowMs,
    max: options.max,
    message: {
      success: false,
      statusCode: 429,
      message: options.message || 'Too many requests. Please try again later.',
      timestamp: new Date().toISOString(),
    },
    standardHeaders: true,
    legacyHeaders: false,
    store,
  } as Partial<Options>);
}

/**
 * Strict limiter for auth endpoints (OTP requests)
 * 5 attempts per 15 minutes per IP
 */
export const authRateLimiter = createRateLimiter({
  windowMs: 15 * 60 * 1000,
  max: 5,
  message: 'Too many OTP requests. Please wait 15 minutes before trying again.',
  prefix: 'rl:auth',
});

/**
 * Standard API limiter — 100 requests per 15 minutes
 */
export const apiRateLimiter = createRateLimiter({
  windowMs: 15 * 60 * 1000,
  max: 100,
  prefix: 'rl:api',
});

/**
 * Ride booking limiter — prevent spam bookings
 * 10 ride requests per 5 minutes
 */
export const rideRateLimiter = createRateLimiter({
  windowMs: 5 * 60 * 1000,
  max: 10,
  message: 'Too many ride requests. Please wait a few minutes.',
  prefix: 'rl:ride',
});

/**
 * Payment webhook limiter — generous for Razorpay callbacks
 * 200 requests per minute
 */
export const webhookRateLimiter = createRateLimiter({
  windowMs: 60 * 1000,
  max: 200,
  prefix: 'rl:webhook',
});
