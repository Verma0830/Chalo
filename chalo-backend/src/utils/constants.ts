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
  DRIVER_SEARCH_MAX_CANDIDATES: 10,      // Max drivers to consider for matching
  DRIVER_BROADCAST_SIZE: 5,             // How many drivers get notified per batch
  RIDE_OFFER_BATCH_TTL_SECS: 65,        // Redis TTL per batch window (60s window + 5s grace)
  RIDE_CANDIDATES_TTL_SECS: 600,        // Redis TTL for full candidate list (10 min)
  RIDE_SHARE_TTL_HOURS: 24,
  RIDE_SHARE_TOKEN_BYTES: 24,
  DRIVER_CANCELLATION_ALERT_THRESHOLD: 3,

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

  // --- Request Limits ---
  MAX_BODY_SIZE_API: '100kb',             // Default API body limit
  MAX_BODY_SIZE_UPLOAD: '5mb',            // File upload body limit

  // --- Timeouts (ms) ---
  GOOGLE_MAPS_TIMEOUT_MS: 10000,          // Google Maps API timeout
  RAZORPAY_TIMEOUT_MS: 15000,             // Razorpay API timeout
  DEFAULT_API_TIMEOUT_MS: 30000,          // Generic API timeout

  // --- Circuit Breaker ---
  CIRCUIT_BREAKER_THRESHOLD_PERCENT: 50,
  CIRCUIT_BREAKER_RESET_MS: 30000,
  CIRCUIT_BREAKER_MIN_REQUESTS: 5,

  // --- Cancellation fee defaults (overridden by platform_config) ---
  FREE_CANCEL_WINDOW_SECS: 120,   // 2 min free window after driver assignment
  DEFAULT_CANCEL_FEE: 20,         // ₹20 if customer cancels after free window

  // --- GST (internal accounting only — not shown to customer/driver) ---
  DEFAULT_GST_PERCENTAGE: 5,      // 5% GST on ride fare (Indian ride-hailing tax)

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
    FREE_CANCEL_WINDOW_SECS: 'free_cancel_window_secs',
    CANCEL_FEE_AMOUNT: 'cancel_fee_amount',
    GST_PERCENTAGE: 'gst_percentage',
  },
} as const;

export default CONSTANTS;
