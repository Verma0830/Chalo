# Chalo Backend — Codebase Reference for AI Assistants

> Give this file to any Claude (or other AI) at the start of a session so it instantly understands the project without needing to explore the repo from scratch.

---

## 1. What Is Chalo?

A **bike ride-hailing app** for Faridabad, Haryana. Customers book bike rides via an Android app (Kotlin + Jetpack Compose). Drivers accept rides through a separate driver app.

- **V1 scope**: Bike rides only, cash + UPI payment, Punjabi + English UI.
- **Market**: Small city (Faridabad) — not scaling to millions, but built correctly.
- **Backend**: Node.js 20 + TypeScript 5.3 (strict) + Express 4. API running at `http://localhost:3001/api/v1`.

---

## 2. Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Node.js 20 |
| Language | TypeScript 5.3 strict mode |
| Framework | Express 4 |
| ORM | Prisma 5 |
| Database | PostgreSQL 15 + PostGIS (geospatial), Docker container `chalo-db` on port 5432 |
| Cache | Redis 7 (localhost:6379) — requires `REDIS_URL` env var; if absent in dev, app runs without Redis |
| Auth | Firebase Admin SDK — phone OTP → Firebase token → verified by middleware |
| Push | Firebase Cloud Messaging (FCM) |
| Realtime | Firebase Realtime Database (driver location + ride status sync) |
| Storage | Firebase Storage (document uploads) |
| Payments | Razorpay (UPI + webhooks) |
| Maps | Google Maps Directions API (Haversine fallback if key absent) |
| Validation | Zod — every endpoint has a validator |
| Jobs | BullMQ (two queues: `chalo-maintenance`, `chalo-rides`) |
| Circuit breaker | opossum (on Razorpay calls) |
| Metrics | prom-client → `/metrics` endpoint |
| Tests | Jest + ts-jest — 249 tests, 100% passing |

---

## 3. Directory Structure

```
chalo-backend/
├── prisma/
│   ├── schema.prisma          # Single source of truth for DB schema
│   ├── seed.ts                # Seeds platform_config table
│   └── migrations/
│       ├── 20260301011006_init/            # Full schema init
│       ├── 20260301011007_add_postgis_indexes/ # GIST indexes for lat/lng
│       └── 20260301020000_add_verification_metadata/ # verificationNote + verificationMetadata
│
└── src/
    ├── app.ts                 # Express app setup (middleware stack, routes mount)
    ├── server.ts              # Entry point (connects DB, Redis, starts HTTP server)
    │
    ├── config/
    │   ├── index.ts           # Reads env vars, exports typed config object
    │   ├── database.ts        # Prisma client singleton (import as `prisma`)
    │   ├── redis.ts           # Redis client singleton — connectRedis(), getRedisClient(), isRedisReady()
    │   ├── firebase.ts        # Firebase Admin init — getAuth(), getDb(), getStorage(), getMessaging()
    │   └── logger.ts          # Winston logger (JSON in prod, colorized in dev)
    │
    ├── controllers/
    │   ├── auth.controller.ts
    │   ├── ride.controller.ts
    │   ├── driver.controller.ts
    │   ├── payment.controller.ts
    │   ├── notification.controller.ts
    │   └── admin.controller.ts
    │
    ├── jobs/
    │   └── queue.ts           # BullMQ queues + workers + scheduleRideOfferExpiry()
    │
    ├── middleware/
    │   ├── auth.ts            # authenticate (Firebase token verify) + authorize(role)
    │   ├── validate.ts        # validateBody / validateQuery / validateParams (Zod wrappers)
    │   ├── errorHandler.ts    # Catches all errors, formats ApiError → JSON response
    │   ├── rateLimiter.ts     # Per-endpoint Redis-backed rate limiting
    │   ├── idempotency.ts     # 24h idempotency keys for payment endpoints
    │   ├── requestId.ts       # Attaches X-Request-ID to every request
    │   └── sanitize.middleware.ts # HPP + XSS sanitization
    │
    ├── routes/
    │   ├── index.ts           # Mounts all sub-routers under /api/v1/
    │   ├── auth.routes.ts
    │   ├── ride.routes.ts
    │   ├── driver.routes.ts
    │   ├── payment.routes.ts
    │   ├── notification.routes.ts
    │   └── admin.routes.ts    # All admin routes — requires ADMIN role
    │
    ├── services/
    │   ├── auth.service.ts    # OTP send/verify, profile CRUD
    │   ├── ride.service.ts    # Core ride lifecycle + broadcast driver search + dispatchBatch()
    │   ├── driver.service.ts  # Driver online/offline, accept/decline/complete ride, earnings
    │   ├── fare.service.ts    # Fare estimation (Google Maps + Haversine fallback) + commission calc
    │   ├── payment.service.ts # Razorpay order creation, payment verification, webhook
    │   ├── notification.service.ts # FCM push + in-DB notification storage
    │   ├── sos.service.ts     # SOS trigger + SMS/WhatsApp via MSG91
    │   ├── sms.service.ts     # MSG91 SMS/WhatsApp wrapper
    │   ├── admin.service.ts   # Driver verification, live rides, platform config CRUD
    │   └── kyc/
    │       ├── kyc.interface.ts    # KYCProvider interface + KYCResult / FaceMatchResult types
    │       ├── manual.provider.ts  # Returns unverified — admin must review manually (V1 default)
    │       ├── surepass.provider.ts # Calls Surepass REST API (activates if SUREPASS_API_KEY set)
    │       └── index.ts            # getKYCProvider() factory singleton
    │
    ├── types/
    │   └── index.ts           # AuthenticatedRequest, Location, PaginationMeta, etc.
    │
    ├── utils/
    │   ├── constants.ts       # All magic numbers (CONSTANTS object) — see section 7
    │   ├── apiError.ts        # ApiError class + ErrorCode enum
    │   ├── apiResponse.ts     # ApiResponse static helpers (success, paginated, error)
    │   ├── helpers.ts         # paginationToSkip(), buildPaginationMeta(), haversineDistance()
    │   ├── circuitBreaker.ts  # withCircuitBreaker(), withCircuitBreakerFallback() wrappers
    │   └── metrics.ts         # prom-client metrics definitions
    │
    ├── validators/
    │   ├── auth.validator.ts
    │   ├── ride.validator.ts
    │   ├── driver.validator.ts
    │   ├── payment.validator.ts
    │   └── admin.validator.ts
    │
    └── __tests__/
        ├── services/
        │   ├── fare.service.test.ts
        │   ├── ride.service.test.ts
        │   └── ...
        └── integration/
            └── ...
```

---

## 4. Request Lifecycle

Every request flows through this chain:

```
HTTP Request
  → requestId middleware          (attaches X-Request-ID)
  → helmet / cors / hpp           (security headers)
  → sanitize middleware            (XSS clean)
  → rateLimiter                   (Redis-backed, per-endpoint)
  → authenticate                  (Firebase token → req.user)
  → authorize('ROLE')             (optional role check)
  → validateBody/Query/Params     (Zod schema)
  → idempotency                   (payment endpoints only)
  → Controller method
      → Service method(s)
          → Prisma (DB)
          → Redis
          → Firebase / external APIs
      → ApiResponse.success(res, data, message)
  ← JSON response

  ← On any error: errorHandler catches, formats as { success: false, message, code, errors[] }
```

---

## 5. Auth System

**How it works:**

1. Customer/Driver calls `POST /api/v1/auth/otp/send` with phone number
2. Server stores SHA-256 hashed OTP in `otp_verifications` table (4-digit, 5-minute TTL)
3. Customer calls `POST /api/v1/auth/otp/verify` — if OTP matches, server creates/finds User, then **creates a Firebase custom token** for that user
4. Client exchanges custom token for a Firebase ID token (client-side Firebase SDK)
5. All subsequent requests send `Authorization: Bearer <firebase_id_token>`
6. `authenticate` middleware calls `firebase.getAuth().verifyIdToken(token)` → gets Firebase UID
7. Middleware looks up user in Redis cache (`auth:user:{uid}`, 5-min TTL) or Postgres
8. Attaches user to `req.user` as `AuthenticatedRequest`

**Role check:** `authorize('ADMIN')` reads `req.user.role` — role is stored in Postgres, NOT in the Firebase token. So if you change a user's role in the DB, they must **re-login** to get a new token (Redis cache expires in 5 min).

**IMPORTANT:** Admin users must have `role = 'ADMIN'` in the `users` table (set via SQL). There is no signup endpoint for admins.

```sql
-- To promote a user to ADMIN:
UPDATE users SET role = 'ADMIN' WHERE phone = '+91XXXXXXXXXX';
```

After this SQL update, the user must re-send OTP and re-verify to get a fresh token.

---

## 6. Database Schema (11 Models)

| Model | Table | Purpose |
|---|---|---|
| `User` | `users` | Core account — shared by customers and drivers. Has `role: CUSTOMER \| DRIVER \| ADMIN` |
| `CustomerProfile` | `customer_profiles` | Customer extras: emergency contact, saved home/work locations, ride stats |
| `DriverProfile` | `driver_profiles` | Driver docs (license, RC, Aadhaar), verification status, location, earnings, KYC metadata |
| `Ride` | `rides` | Full ride lifecycle — pickup/drop coords, fare breakdown, status, payment, timestamps |
| `RideEvent` | `ride_events` | Immutable audit log — every status transition is logged here with GPS + metadata |
| `Earning` | `earnings` | Per-ride earning record for driver (gross, commission, net, settlement status) |
| `Withdrawal` | `withdrawals` | Driver payout requests (bank transfer or UPI) |
| `SOSAlert` | `sos_alerts` | SOS triggers during active rides — tracks who was notified |
| `OTPVerification` | `otp_verifications` | OTP records: hashed code, expiry, attempt count |
| `Notification` | `notifications` | In-app push notification log (FCM history) |
| `PlatformConfig` | `platform_config` | Key-value table for runtime-configurable business rules |

**Key relationships:**
- `User` 1–1 `CustomerProfile`, `User` 1–1 `DriverProfile`
- `User` 1–N `Ride` (as customer), `User` 1–N `Ride` (as driver)
- `DriverProfile` 1–N `Earning`, `DriverProfile` 1–N `Withdrawal`

**DriverProfile verification fields:**
- `verificationStatus`: `PENDING | UNDER_REVIEW | VERIFIED | REJECTED`
- `verifiedAt`: set when approved
- `rejectionReason`: set when rejected
- `verificationNote`: admin note on approve or reject
- `verificationMetadata`: JSON blob from KYC API (Surepass result or null)

---

## 7. Constants (All in One Place)

File: `src/utils/constants.ts` — `CONSTANTS` object (as const).

**Ride:**
- `RIDE_ACCEPT_WINDOW_SECS = 60` — window for a driver to accept before batch expires
- `DRIVER_SEARCH_RADIUS_KM = 5` — search radius for nearby drivers
- `DRIVER_SEARCH_MAX_CANDIDATES = 10` — max drivers fetched for scoring
- `DRIVER_BROADCAST_SIZE = 5` — drivers notified per batch (top-5 simultaneous FCM)
- `RIDE_OFFER_BATCH_TTL_SECS = 65` — TTL for batch offer window (60s + 5s grace)
- `RIDE_CANDIDATES_TTL_SECS = 600` — Redis TTL for full candidate list (10 min)
- `DRIVER_ARRIVED_RADIUS_METERS = 200` — radius for "I've Arrived" button to activate

**Fare:**
- `MIN_FARE = 30` — ₹30 minimum fare
- `BASE_FARE_PER_KM = 12` — ₹12/km (DB config overrides this)
- `BASE_FARE_PER_MIN = 2` — ₹2/min (DB config overrides this)
- `BOOKING_FEE = 5` — ₹5 flat booking fee

**Surge:**
- `SURGE_MIN_MULTIPLIER = 1.0`, `SURGE_MAX_MULTIPLIER = 2.0`, `SURGE_STEP = 0.1`

**Auth:**
- `OTP_LENGTH = 4`, `OTP_EXPIRY_MINS = 5`, `MAX_OTP_ATTEMPTS = 3`
- `PHONE_REGEX`: Indian mobile numbers only (`+91` + 6-9 start + 9 digits)

**Platform config keys (DB):**
- `CONFIG_KEYS.COMMISSION_PERCENTAGE = 'commission_percentage'`
- `CONFIG_KEYS.SURGE_ENABLED = 'surge_enabled'`
- `CONFIG_KEYS.MIN_FARE = 'min_fare'`
- `CONFIG_KEYS.BASE_FARE_PER_KM = 'base_fare_per_km'`
- `CONFIG_KEYS.BASE_FARE_PER_MIN = 'base_fare_per_min'`
- (etc — see file for full list)

**IMPORTANT:** DB config keys are lowercase snake_case. Test mocks must use lowercase keys too (`'base_fare_per_km'`, not `'BASE_FARE_PER_KM'`).

---

## 8. Redis Key Patterns

| Key | TTL | Purpose |
|---|---|---|
| `auth:user:{firebaseUid}` | 300s (5 min) | Cached user record (avoids DB hit on every request) |
| `otp:{phone}` | varies | OTP rate limiting |
| `ride:offer:{driverUserId}` | 65s | Pending ride offer for this driver (value = rideId) |
| `ride:active_batch:{rideId}` | 65s | Array of userIds in the current broadcast batch |
| `ride:candidates:{rideId}` | 600s | Array of all candidate userIds sorted by score |
| `fare:config` | 300s | Cached platform config for fare calculation |
| `idempotency:{userId}:{key}` | 86400s (24h) | Payment idempotency keys |
| Rate limit keys | varies | Per-endpoint, per-IP rate limits |

---

## 9. BullMQ Jobs

Two queues in `src/jobs/queue.ts`:

**`chalo-maintenance`**
- `otp-cleanup` — periodic job that deletes expired OTP records from DB

**`chalo-rides`**
- `ride-offer-expired` — delayed job, fires at `RIDE_OFFER_BATCH_TTL_SECS * 1000` ms
  - Checks if ride is still `REQUESTED` (if not, noop — already resolved)
  - Reads `ride:candidates:{rideId}` from Redis
  - Slices next batch starting at `nextBatchStart`
  - If no more candidates → marks ride as `NO_DRIVER`, notifies customer
  - If candidates remain → calls `rideService.dispatchBatch(...)` with next batch

**Key pattern:** Workers use deferred `import('../services/...')` to avoid circular dependencies.

---

## 10. Broadcast Driver Search (How It Works)

Old design: sequential, no timeout → rides stuck forever.
New design: broadcast + BullMQ timeout.

**Flow:**
1. Customer creates ride → `rideService.searchAndNotifyDrivers()` is called
2. PostGIS query finds nearby online drivers within 5km radius
3. Drivers are **scored** (distance, rating, acceptance rate)
4. **Top 10 candidates** stored in Redis (`ride:candidates:{rideId}`, 10min TTL)
5. **First batch of 5** notified simultaneously via FCM
6. Their offer keys set (`ride:offer:{userId}`, 65s TTL)
7. BullMQ delayed job scheduled to fire in 65s (`ride-offer-expired`)

**If driver accepts:** Atomic DB update (compare-and-swap on `REQUESTED` status). Cleans up offer keys for the other 4 drivers in the batch. Cancels the BullMQ job (or BullMQ finds ride no longer `REQUESTED` → noop).

**If driver declines:** Their userId removed from `ride:active_batch`. BullMQ timeout handles advancing to next batch.

**If 65s passes:** BullMQ fires `ride-offer-expired`. Checks ride still `REQUESTED`. Loads candidates from Redis. Dispatches next batch of 5 (indices 5–9). If no candidates left → `NO_DRIVER`.

---

## 11. Error Handling Pattern

**Always use `ApiError`** — never throw raw errors in services/controllers.

```typescript
import { ApiError } from '../utils/apiError';
import { ErrorCode } from '../utils/apiError';

// Examples:
throw ApiError.notFound('Driver not found', ErrorCode.DRIVER_NOT_FOUND);
throw ApiError.badRequest('Invalid input', ErrorCode.VALIDATION_ERROR);
throw ApiError.unauthorized('Token required');
throw ApiError.forbidden('Admin only');
```

**Controller pattern:**
```typescript
async myMethod(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const result = await myService.doSomething();
    ApiResponse.success(res, result, 'Success message');
  } catch (error) {
    next(error);  // Always pass to errorHandler
  }
}
```

**Response format:**
```json
// Success
{ "success": true, "message": "...", "data": { ... } }

// Paginated
{ "success": true, "message": "...", "data": [...], "meta": { "page": 1, "limit": 20, "total": 100, "totalPages": 5 } }

// Error
{ "success": false, "message": "...", "code": "DRIVER_NOT_FOUND", "errors": [] }
```

---

## 12. Validation Pattern

Every endpoint has a Zod schema in `src/validators/`:

```typescript
// In validator file:
export const mySchema = z.object({
  name: z.string().min(1).max(100),
  age: z.number().int().min(0),
});
export type MyInput = z.infer<typeof mySchema>;

// In route file:
router.post('/path', validateBody(mySchema), controller.myMethod.bind(controller));

// In controller:
const { name, age } = req.body as MyInput;
```

Middleware: `validateBody`, `validateQuery`, `validateParams` — all in `src/middleware/validate.ts`. They call `schema.parse()` and forward `ZodError` to the error handler.

---

## 13. KYC Provider

File: `src/services/kyc/`

```typescript
// Get the active provider (singleton):
const provider = getKYCProvider();

// Returns ManualKYCProvider by default (always returns { verified: false })
// Returns SurepassKYCProvider if process.env.SUREPASS_API_KEY is set

// Usage in admin.service.ts autoVerifyDriver():
const result = await provider.verifyDocument({ documentType: 'aadhaar', documentNumber: '...' });
if (result.confidence && result.confidence >= 0.85) {
  // Auto-approve
} else {
  // Leave for manual review
}
```

---

## 14. Environment Variables

| Variable | Required in Prod | Default / Notes |
|---|---|---|
| `DATABASE_URL` | Yes | `postgresql://postgres:luffy@localhost:5432/chalo?schema=public` |
| `REDIS_URL` | No (dev only optional) | `redis://localhost:6379` — if absent in dev, Redis features disabled |
| `PORT` | No | `3001` (use 3001 locally — Docker Desktop uses 5000) |
| `NODE_ENV` | No | `development` |
| `FIREBASE_SERVICE_ACCOUNT_PATH` | Yes | `./firebase-service-account.json` |
| `FIREBASE_DATABASE_URL` | Yes | Firebase project's RTDB URL |
| `RAZORPAY_KEY_ID` | No (dev, cash works without) | `rzp_test_...` |
| `RAZORPAY_KEY_SECRET` | No (dev) | — |
| `RAZORPAY_WEBHOOK_SECRET` | No (dev) | — |
| `GOOGLE_MAPS_API_KEY` | No | Haversine fallback used if absent |
| `MSG91_AUTH_KEY` | No | SMS/SOS alert — skipped if absent |
| `SUREPASS_API_KEY` | No | KYC API — ManualKYCProvider used if absent |
| `INTERNAL_API_KEY` | No | Protects `/metrics` endpoint in prod |

---

## 15. Business Rules (Cannot Change Without DB Config)

All configurable via `PUT /api/v1/admin/config/:key` (ADMIN role required):

| Config key | Default | Description |
|---|---|---|
| `commission_percentage` | 15 | % platform takes per ride (COMMISSION plan drivers) |
| `subscription_fee_weekly` | 199 | ₹199/week (SUBSCRIPTION plan) |
| `surge_enabled` | false | Enable/disable surge pricing |
| `surge_multiplier` | — | Manual surge multiplier override |
| `min_fare` | 30 | ₹30 minimum fare |
| `base_fare_per_km` | 12 | ₹12/km |
| `base_fare_per_min` | 2 | ₹2/min |
| `settlement_days` | 2 | T+2 settlement |

SUBSCRIPTION plan drivers pay zero commission (fixed weekly fee instead). COMMISSION plan drivers pay 15% per ride.

---

## 16. Test Structure

Tests in `src/__tests__/`. Run with `npm test` from `chalo-backend/`.

**Standard mocking pattern** (look at any existing test to copy):

```typescript
// Mock DB
const mockFindMany = jest.fn();
jest.mock('../../config/database', () => ({
  __esModule: true,
  default: { myModel: { findMany: mockFindMany } },
}));

// Mock Redis
const mockRedisGet = jest.fn();
jest.mock('../../config/redis', () => ({
  getRedisClient: () => ({ get: mockRedisGet, setEx: jest.fn().mockResolvedValue('OK') }),
  isRedisReady: () => true,
}));

// Mock logger (always mock this to avoid noise)
jest.mock('../../config/logger', () => ({
  __esModule: true,
  default: { info: jest.fn(), warn: jest.fn(), error: jest.fn(), debug: jest.fn() },
}));
```

**IMPORTANT:** Config keys in test mocks must be **lowercase snake_case** (matching `CONSTANTS.CONFIG_KEYS`):
```typescript
// CORRECT:
{ key: 'base_fare_per_km', value: '12' }

// WRONG (will cause fare tests to fail at night when surge kicks in):
{ key: 'BASE_FARE_PER_KM', value: '12' }
```

---

## 17. Migration Gotchas (Windows + PostGIS)

- Always use `npx prisma migrate deploy` — NOT `prisma migrate dev` (shadow DB fails with PostGIS on Windows)
- SQL in migrations must use `CAST(value AS geography)` — NOT `::geography` (Prisma SQL parser issue)
- PostGIS extension must be pre-installed on the container before running migrations
- If you add new fields to `schema.prisma`, run `npx prisma generate` after migration to update the client types

---

## 18. Current Status (March 2026)

**Done:**
- All 41 API endpoints implemented and tested
- Broadcast driver search (top-5 batch, BullMQ timeout)
- Admin panel (8 endpoints, ADMIN role gate)
- KYC provider (pluggable — Surepass activates automatically via env var)
- Firebase auth, FCM, RTDB sync
- Prisma schema with PostGIS
- 249/249 tests passing
- Docker Compose for full-stack local dev

**Pending (one-time setup):**
- `npx prisma migrate deploy` — apply `20260301020000_add_verification_metadata` migration
- `npm run db:seed` — seed `platform_config` table with initial values

**Next steps:**
- Customer Android app (Kotlin + Jetpack Compose)
- Driver Android app
- Google Maps API key (optional — Haversine fallback works)
- Razorpay live keys (Cash-only mode works without them)
- Surepass KYC API key (manual review works without it)

---

## 19. Common Patterns to Follow

When adding a new endpoint, always follow this pattern:

1. **Validator** (`src/validators/`) — Zod schema + inferred type export
2. **Service** (`src/services/`) — business logic, uses Prisma + Redis, throws `ApiError`
3. **Controller** (`src/controllers/`) — thin handler: `try { result = await service.method(); ApiResponse.success(...) } catch(e) { next(e) }`
4. **Route** (`src/routes/`) — wire validator + controller, apply `authenticate` + `authorize` as needed
5. **Test** (`src/__tests__/`) — mock DB + Redis + logger, test service logic directly

Do not add business logic to controllers. Do not add HTTP concepts (status codes, req/res) to services.

---

## 20. Key File Locations Quick Reference

| What you want to change | File |
|---|---|
| Add a constant | `src/utils/constants.ts` |
| Add an error code | `src/utils/apiError.ts` (ErrorCode enum) |
| Change DB schema | `prisma/schema.prisma` → new migration file |
| Add a platform config key | `prisma/schema.prisma` → seed.ts → CONSTANTS.CONFIG_KEYS |
| Change fare calculation | `src/services/fare.service.ts` |
| Change driver search logic | `src/services/ride.service.ts` |
| Change auth middleware | `src/middleware/auth.ts` |
| Change BullMQ job logic | `src/jobs/queue.ts` |
| Add a new KYC provider | `src/services/kyc/` — implement `KYCProvider` interface |
| Change admin endpoints | `src/services/admin.service.ts` + `src/controllers/admin.controller.ts` |
| Change rate limits | `src/middleware/rateLimiter.ts` |
