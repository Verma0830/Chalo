// ============================================================
// Chalo Backend — Environment Configuration
// Single source of truth for all env variables with validation
// ============================================================

import dotenv from 'dotenv';
import path from 'path';

// Load .env file
dotenv.config({ path: path.resolve(__dirname, '../../.env') });

function requireEnv(key: string, defaultValue?: string): string {
  const value = process.env[key] || defaultValue;
  if (!value) {
    throw new Error(`Missing required environment variable: ${key}`);
  }
  return value;
}

function optionalEnv(key: string, defaultValue: string): string {
  return process.env[key] || defaultValue;
}

export const config = {
  // --- Server ---
  env: optionalEnv('NODE_ENV', 'development'),
  port: parseInt(optionalEnv('PORT', '5000'), 10),
  apiVersion: optionalEnv('API_VERSION', 'v1'),
  isDev: optionalEnv('NODE_ENV', 'development') === 'development',
  isProd: process.env.NODE_ENV === 'production',

  // --- Database ---
  databaseUrl: requireEnv('DATABASE_URL'),

  // --- Redis ---
  redisUrl: optionalEnv('REDIS_URL', 'redis://localhost:6379'),

  // --- Firebase ---
  firebase: {
    serviceAccountPath: optionalEnv('FIREBASE_SERVICE_ACCOUNT_PATH', './firebase-service-account.json'),
    databaseUrl: process.env.FIREBASE_DATABASE_URL || '',
  },

  // --- Razorpay ---
  razorpay: {
    keyId: requireEnv('RAZORPAY_KEY_ID', 'rzp_test_placeholder'),
    keySecret: requireEnv('RAZORPAY_KEY_SECRET', 'placeholder_secret'),
    webhookSecret: process.env.RAZORPAY_WEBHOOK_SECRET || '',
  },

  // --- Google Maps ---
  googleMaps: {
    apiKey: requireEnv('GOOGLE_MAPS_API_KEY', 'placeholder_key'),
  },

  // --- Business Rules (defaults — overridden by PlatformConfig DB table at runtime) ---
  business: {
    commissionPercentage: parseInt(optionalEnv('COMMISSION_PERCENTAGE', '15'), 10),
    subscriptionFeeWeekly: parseInt(optionalEnv('SUBSCRIPTION_FEE_WEEKLY', '199'), 10),
    surgeEnabled: optionalEnv('SURGE_ENABLED', 'true') === 'true',
    settlementDays: parseInt(optionalEnv('SETTLEMENT_DAYS', '2'), 10),
    rideAcceptWindowSecs: 60,
    maxScheduleDays: 7,
    driverSearchRadiusKm: 5,
    maxDriverSearchTimeSecs: 120,
    sosHoldDurationMs: 2000,
    minFare: 30,          // ₹30 minimum fare
    baseFarePerKm: 12,    // ₹12 per km
    baseFarePerMin: 2,    // ₹2 per minute
  },

  // --- Auth ---
  internalApiKey: process.env.INTERNAL_API_KEY || '',

  // --- Logging ---
  logLevel: optionalEnv('LOG_LEVEL', 'debug'),

  // --- CORS ---
  allowedOrigins: optionalEnv('ALLOWED_ORIGINS', 'http://localhost:3000').split(','),
} as const;

export type Config = typeof config;
export default config;
