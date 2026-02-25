// ============================================================
// Chalo Backend — Rate Limiter Middleware
// Prevents abuse, brute force, and DDoS
// ============================================================

import rateLimit, { Options } from 'express-rate-limit';

interface RateLimitConfig {
  windowMs: number;
  max: number;
  message?: string;
}

/**
 * Create a rate limiter with custom config
 */
export function createRateLimiter(options: RateLimitConfig) {
  return rateLimit({
    windowMs: options.windowMs,
    max: options.max,
    message: {
      success: false,
      statusCode: 429,
      message: options.message || 'Too many requests. Please try again later.',
      timestamp: new Date().toISOString(),
    },
    standardHeaders: true,   // Return rate limit info in `RateLimit-*` headers
    legacyHeaders: false,    // Disable `X-RateLimit-*` headers
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
});

/**
 * Standard API limiter — 100 requests per 15 minutes
 */
export const apiRateLimiter = createRateLimiter({
  windowMs: 15 * 60 * 1000,
  max: 100,
});

/**
 * Ride booking limiter — prevent spam bookings
 * 10 ride requests per 5 minutes
 */
export const rideRateLimiter = createRateLimiter({
  windowMs: 5 * 60 * 1000,
  max: 10,
  message: 'Too many ride requests. Please wait a few minutes.',
});

/**
 * Payment webhook limiter — generous for Razorpay callbacks
 * 200 requests per minute
 */
export const webhookRateLimiter = createRateLimiter({
  windowMs: 60 * 1000,
  max: 200,
});
