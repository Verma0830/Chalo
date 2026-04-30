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

function requireInt(key: string, defaultValue: number): number {
  const raw = process.env[key];
  const parsed = raw !== undefined ? parseInt(raw, 10) : defaultValue;
  if (!Number.isFinite(parsed)) {
    throw new Error(`Environment variable ${key} must be a valid integer, got: "${raw}"`);
  }
  return parsed;
}

function optionalEnv(key: string, defaultValue: string): string {
  return process.env[key] || defaultValue;
}

export const config = {
  // --- Server ---
  env: optionalEnv('NODE_ENV', 'development'),
  port: requireInt('PORT', 5000),
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
    keyId: process.env.NODE_ENV === 'production'
      ? requireEnv('RAZORPAY_KEY_ID')
      : optionalEnv('RAZORPAY_KEY_ID', 'rzp_test_placeholder'),
    keySecret: process.env.NODE_ENV === 'production'
      ? requireEnv('RAZORPAY_KEY_SECRET')
      : optionalEnv('RAZORPAY_KEY_SECRET', 'placeholder_secret'),
    webhookSecret: process.env.NODE_ENV === 'production'
      ? requireEnv('RAZORPAY_WEBHOOK_SECRET')
      : optionalEnv('RAZORPAY_WEBHOOK_SECRET', ''),
  },

  // --- Google Maps ---
  googleMaps: {
    apiKey: process.env.NODE_ENV === 'production'
      ? requireEnv('GOOGLE_MAPS_API_KEY')
      : optionalEnv('GOOGLE_MAPS_API_KEY', 'placeholder_key'),
  },

  // --- MSG91 (SMS / WhatsApp) ---
  msg91: {
    authKey: process.env.MSG91_AUTH_KEY || '',
    senderId: optionalEnv('MSG91_SENDER_ID', 'CHALO'),
  },

  // --- Business Rules (defaults — overridden by PlatformConfig DB table at runtime) ---
  business: {
    commissionPercentage: requireInt('COMMISSION_PERCENTAGE', 15),
    subscriptionFeeWeekly: requireInt('SUBSCRIPTION_FEE_WEEKLY', 199),
    surgeEnabled: optionalEnv('SURGE_ENABLED', 'true') === 'true',
    settlementDays: requireInt('SETTLEMENT_DAYS', 2),
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
  allowedOrigins: optionalEnv('ALLOWED_ORIGINS', 'http://localhost:3000').split(',').map(s => s.trim()),
} as const;

export type Config = typeof config;
export default config;
