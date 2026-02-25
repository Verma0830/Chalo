// ============================================================
// Chalo Backend — Application Constants
// All magic numbers and strings live here
// ============================================================

export const CONSTANTS = {
  // --- Ride ---
  RIDE_ACCEPT_WINDOW_SECS: 60,
  MAX_SCHEDULE_DAYS: 7,
  DRIVER_SEARCH_RADIUS_KM: 5,
  MAX_DRIVER_SEARCH_TIME_SECS: 120,
  DRIVER_ARRIVED_RADIUS_METERS: 200,     // "I've Arrived" activates within 200m
  SCHEDULED_RIDE_REMINDER_MINS: 30,

  // --- Fare ---
  MIN_FARE: 30,                           // ₹30 minimum fare
  BASE_FARE_PER_KM: 12,                  // ₹12/km
  BASE_FARE_PER_MIN: 2,                  // ₹2/min
  BOOKING_FEE: 5,                         // ₹5 platform booking fee
  CURRENCY: 'INR',
  CURRENCY_SYMBOL: '₹',

  // --- Surge ---
  SURGE_MIN_MULTIPLIER: 1.0,
  SURGE_MAX_MULTIPLIER: 2.0,
  SURGE_STEP: 0.1,

  // --- Commission & Subscription ---
  DEFAULT_COMMISSION_PERCENTAGE: 15,
  DEFAULT_SUBSCRIPTION_FEE_WEEKLY: 199,

  // --- Settlement ---
  SETTLEMENT_DAYS: 2,                     // T+2

  // --- Auth ---
  OTP_LENGTH: 4,
  OTP_EXPIRY_MINS: 5,
  MAX_OTP_ATTEMPTS: 3,
  PHONE_REGEX: /^\+91[6-9]\d{9}$/,       // Indian mobile numbers

  // --- SOS ---
  SOS_HOLD_DURATION_MS: 2000,             // 2-second press-and-hold

  // --- Pagination ---
  DEFAULT_PAGE: 1,
  DEFAULT_LIMIT: 20,
  MAX_LIMIT: 100,

  // --- File Upload ---
  MAX_FILE_SIZE_MB: 5,
  ALLOWED_IMAGE_TYPES: ['image/jpeg', 'image/png', 'image/webp'],

  // --- Rating ---
  MIN_RATING: 1,
  MAX_RATING: 5,

  // --- Platform Config Keys (in DB) ---
  CONFIG_KEYS: {
    COMMISSION_PERCENTAGE: 'commission_percentage',
    SUBSCRIPTION_FEE_WEEKLY: 'subscription_fee_weekly',
    SURGE_ENABLED: 'surge_enabled',
    SURGE_MULTIPLIER: 'surge_multiplier',
    MIN_FARE: 'min_fare',
    BASE_FARE_PER_KM: 'base_fare_per_km',
    BASE_FARE_PER_MIN: 'base_fare_per_min',
    SETTLEMENT_DAYS: 'settlement_days',
  },
} as const;

export default CONSTANTS;
