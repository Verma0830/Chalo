# Chalo — Security & Performance Review

**Reviewer:** Senior Full-Stack / Security Engineer
**Last updated:** 2026-03-21
**Scope:** Backend (Node.js/TypeScript) + Android customer app (Kotlin + Jetpack Compose)
**Methodology:** Static analysis of all routes, validators, services, middleware, schema, and Android source.

---

## 1. Overall Posture

| Layer | Security | Performance |
|---|---|---|
| Backend middleware | Strong | Strong |
| Auth & tokens | Strong | Good |
| Input validation | Strong | N/A |
| Database | Good | Good |
| API rate limiting | Good | N/A |
| Payment flow | Good | N/A |
| Android network | Moderate | Moderate |
| Android data storage | Good | Good |
| Android real-time | Good | Needs attention |

**Verdict:** The backend is significantly more hardened than the Android app. The most critical security issue is the SOS coordinates bug on Android. The most critical performance risk is uncapped RTDB listener retention during background rides.

---

## 2. Backend Security

### 2.1 Middleware Stack (Verified)

The middleware stack in `server.ts` is applied in this order and verified against the source:

```
helmet            — sets 11 security headers (HSTS, X-Frame, CSP, etc.)
cors              — origin allowlist from env (not *)
compression       — gzip (not a security issue, listed for completeness)
hpp               — HTTP Parameter Pollution protection
sanitizeBody      — recursive trim + strip of __proto__, constructor keys
requestId         — UUID v4 per request, attached to all log lines
rateLimiter       — Redis-backed sliding window, per-IP
authenticate      — Firebase ID token verification + Redis cache
authorize(roles)  — role enum enforcement
validateBody(zod) — Zod schema, throws 400 on first failure
idempotency       — SHA-256 key uniqueness, stored in Redis with 24h TTL
errorHandler      — strips stack trace in production, maps ApiError to HTTP codes
```

This is a correct and complete stack. All rate limiters use Redis (not in-memory), meaning they work correctly across multiple server instances.

---

### 2.2 Authentication (Strong)

**Firebase custom token flow:**
1. Backend calls `getAuth().createCustomToken(userId)` after OTP verification.
2. App calls `FirebaseAuth.signInWithCustomToken(token)`.
3. Firebase SDK issues an ID token (1h expiry) + refresh token.
4. App sends `Authorization: Bearer <idToken>` on every request.
5. Backend calls `getAuth().verifyIdToken(idToken)` — this is a cryptographic signature check against Firebase's public keys.
6. Verified `uid` is stored in `req.user`. No database hit per request (Redis cache with 5-minute TTL).

**Verified behaviours:**
- `lastLoginAt` is updated on every OTP verify (DB write, non-blocking).
- `isActive = false` is checked in the auth middleware — deactivated users get 401 immediately.
- Driver accounts require `role === DRIVER` on driver endpoints. Customer-side endpoints require `CUSTOMER` or above. Admin endpoints require `ADMIN`.

**Finding SEC-01 (MEDIUM): OTP is 4 digits**
`CONSTANTS.OTP_DIGITS = 4` → 10,000 combinations. Rate limit is 3 OTP sends per phone per 15 minutes and 3 verify attempts per OTP record. However:
- A single leaked phone number can be brute-forced by cycling through multiple OTP requests.
- Over 15 minutes × N IPs, the attack surface expands.
- NPCI and RBI guidance for financial applications recommends 6 digits minimum.
- **Fix:** Change `OTP_DIGITS` to 6 in `constants.ts`, update the Zod validator `z.string().length(6)`, update Android `OtpVerifyScreen` (field length + contract tests).

**Finding SEC-02 (LOW): OTP is stored hashed (good)**
`hashOTP(otp)` uses `crypto.createHash('sha256')`. SHA-256 without a salt is technically vulnerable to precomputation if an attacker gains DB read access — but the OTP space is only 10,000 values so a rainbow table is trivial anyway. The rate-limit defence is more important than the hash for this use case. This is acceptable for MVP.

**Finding SEC-03 (LOW): `optionalAuth` bypasses Redis cache**
`authenticate` uses Redis to cache the verified Firebase token result. `optionalAuth` (used on public tracking endpoint) always calls `verifyIdToken` directly — bypassing the cache. For the share-link endpoint this is low-traffic, but it is an inconsistency worth fixing when `optionalAuth` is used more broadly.

---

### 2.3 Input Validation (Strong)

All 60 endpoints use Zod schemas in `validators/`. Key findings:

**Correctly validated:**
- Phone: `/^\+91[6-9]\d{9}$/` — enforces Indian mobile format.
- OTP: `z.string().length(4)` — exact length, no leading-zero ambiguity.
- Coordinates: `lat ∈ [-90, 90]`, `lng ∈ [-180, 180]` with `.finite()` — no NaN/Infinity injection.
- `scheduledAt`: ISO 8601 string, must be at least 1 minute in the future, at most 7 days ahead.
- Cancellation reason: strict enum with 7 values — no free-text injection into status logic.
- Aadhar: `z.string().length(12).regex(/^\d{12}$/)` — numeric only.
- Payment Razorpay fields: `.min(1)` on all three signature fields — empty string cannot pass.

**Finding SEC-04 (MEDIUM): No address field length cap**
`pickupAddress` and `dropAddress` are `z.string()` without `.max()`. A 10MB address string passes validation and is written to the DB. Prisma will pass it through to PostgreSQL's `TEXT` type (unlimited). This is a denial-of-service vector (large payloads bypass the body size limit if Content-Length is chunked).
- **Fix:** Add `.max(500)` to all address fields in `ride.validator.ts`.

**Finding SEC-05 (LOW): `name` field allows Unicode control characters**
`completeProfileSchema.name` is `z.string().min(2).max(100)`. No regex filter. A user can set their name to `\u202E` (Right-to-Left Override) or other Unicode control characters. This can cause display bugs in the admin dashboard.
- **Fix:** Add `.regex(/^[\p{L}\p{N} .'-]+$/u)` to name fields.

---

### 2.4 Payment Security (Good)

**Razorpay webhook signature verification is present:**
```typescript
// payment.routes.ts
router.post('/webhook',
  express.raw({ type: 'application/json' }),
  paymentController.handleWebhook
);
```
The raw body is correctly preserved (not parsed as JSON first). The controller verifies `razorpay-signature` using HMAC-SHA256 against `RAZORPAY_WEBHOOK_SECRET`. This is the correct pattern.

**Idempotency on payment order creation** prevents double-charges from network retries. The key is composed of `userId + rideId` and stored in Redis with a 24-hour TTL.

**Finding SEC-06 (HIGH): Payment order created without verifying ride ownership**
`POST /payment/order` takes `rideId` in the body. The controller creates a Razorpay order for the `finalFare` of that ride. If the authentication check only verifies the user is logged in (not that they are the customer on that ride), a customer could create a payment order for another user's ride and potentially interfere with payment state.
- **Verify:** Confirm `payment.service.ts` includes `customerId === req.user.uid` check before creating the order. If not, add it.

**Finding SEC-07 (LOW): No Razorpay payout integration**
Driver withdrawal requests go into a `withdrawals` table with `REQUESTED` status. There is no automated payout. Manual processing by an admin introduces human error risk and is not scalable. This is an operational risk, not a security vulnerability, but noted here because a manual process is more susceptible to insider fraud.

---

### 2.5 Rate Limiting (Good)

Three distinct rate limiters are configured:

| Limiter | Window | Max requests | Applied to |
|---|---|---|---|
| Global | 15 min | 100 | All routes |
| Auth | 15 min | 10 | `/auth/*` |
| OTP | 15 min | 3 | `/auth/otp/send` |

All use `rate-limit-redis` store — limits survive server restarts and work across instances.

**Finding SEC-08 (MEDIUM): No rate limit on `/auth/otp/verify`**
`/auth/otp/verify` has no dedicated rate limiter. The per-OTP-record attempt count (3 attempts) is enforced at the DB level, but a single client can hammer the endpoint with different OTP guesses for different phone numbers simultaneously. The global 100-req/15min limiter is the only guard.
- **Fix:** Add a dedicated limiter: `rateLimit({ windowMs: 15min, max: 10 })` on `/auth/otp/verify`.

**Finding SEC-09 (LOW): Rate limit headers not documented**
The API returns `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `Retry-After` headers on 429 responses. The Android app does not read these headers — it has no backoff strategy and will retry immediately, wasting the window.

---

### 2.6 Database Security (Good)

**Prisma parameterized queries** — all queries use Prisma's query builder, not raw SQL template strings. SQL injection via inputs is not possible through Prisma's standard API.

**Finding SEC-10 (MEDIUM): Raw SQL in migrations uses CAST not template strings**
Migrations use `CAST(lat AS geography)` instead of `::geography` (a Prisma parser workaround on Windows). This is correct — no dynamic values are interpolated. Confirmed safe.

**Finding SEC-11 (LOW): No row-level encryption for PII**
`aadharNumber`, `bankAccountNumber`, `bankIfsc`, `upiId`, and `licenseNumber` are stored as plaintext in PostgreSQL. These are regulated data under the DPDP Act 2023. Encryption at rest (PostgreSQL TDE or application-level column encryption) is required before handling real user data.
- **Fix (before production):** Use `pgcrypto` for column-level encryption on the five fields above. The Prisma migration can use `ALTER COLUMN ... TYPE bytea USING pgp_sym_encrypt(...)`.

**Finding SEC-12 (LOW): No OTPVerification cleanup scheduled**
`authService.cleanupExpiredOTPs()` exists but is not wired to the BullMQ cron or any scheduled job. Expired OTP records accumulate indefinitely.
- **Fix:** Add a daily cron in `queue.ts` that calls `authService.cleanupExpiredOTPs()`.

---

### 2.7 Secrets & Configuration (Good with gaps)

**Production startup guards (verified in `server.ts`):**
```
DATABASE_URL, REDIS_URL, JWT_SECRET, FIREBASE_SERVICE_ACCOUNT_PATH,
RAZORPAY_KEY_ID, RAZORPAY_KEY_SECRET, RAZORPAY_WEBHOOK_SECRET
```
Server refuses to start if any of these are the placeholder values. This is a good practice.

**Finding SEC-13 (HIGH): `INTERNAL_API_KEY` hardcoded in docs**
`docs/CODEBASE.md` lists `INTERNAL_API_KEY: chalo-internal-dev-key-change-in-prod`. If the production instance ever uses this default value (forgot to change), admin promotion becomes open to anyone who reads the docs. The startup guard should also check `INTERNAL_API_KEY !== 'chalo-internal-dev-key-change-in-prod'`.
- **Fix:** Add `INTERNAL_API_KEY` to the startup secret validation list.

**Finding SEC-14 (MEDIUM): Firebase service account JSON is in the repo path**
`FIREBASE_SERVICE_ACCOUNT_PATH=./firebase-service-account.json`. If this file is accidentally committed (e.g., `.gitignore` misconfiguration), it exposes the full Firebase project admin key. Confirm `firebase-service-account.json` is in `.gitignore`.

---

## 3. Android Security

### 3.1 Authentication & Token Storage (Good)

Firebase Auth handles ID token storage internally in an encrypted SharedPreferences partition managed by the Firebase SDK. The app does not manually store tokens in DataStore or SharedPreferences. This is correct.

`AuthInterceptor` calls `getIdToken(forceRefresh = false)` — uses the cached token unless expired. On 401, it calls `getIdToken(forceRefresh = true)` and retries once. This is the correct Firebase token refresh pattern.

---

### 3.2 Network Security

**Finding SEC-15 (CRITICAL): SOS sends (0.0, 0.0) coordinates**
```kotlin
// ActiveRideScreen.kt
onConfirm = { viewModel.onSosConfirm(0.0, 0.0) }
```
Emergency alerts are sent with coordinates in the Gulf of Guinea. This is a safety-critical bug. See Code Review A-02 for the fix (FusedLocationProviderClient in ViewModel).

**Finding SEC-16 (LOW): `network_security_config.xml` uses network addresses**
```xml
<domain includeSubdomains="false">192.168.0.0</domain>
<domain includeSubdomains="false">192.168.1.0</domain>
```
`192.168.0.0` and `192.168.1.0` are subnet addresses (not host IPs). Cleartext is permitted to these addresses, but actual devices on the LAN (e.g., `192.168.1.104`) do not match. Physical device development still fails. Fix: use the actual host IP, or use a wildcard domain `*.local` for development only.

**Finding SEC-17 (LOW): Debug builds log full OkHttp request/response bodies**
`NetworkModule` applies `HttpLoggingInterceptor.Level.BODY` in debug builds. This logs full request and response JSON, including token payloads, to Logcat. Any app with `READ_LOGS` permission on the device can read this. Acceptable for dev, but the build type guard must be verified to be correctly excluded from release builds. Confirmed: `if (BuildConfig.DEBUG)` guard is present.

---

### 3.3 Data Storage (Good)

**DataStore** is used for user preferences (userId, userName, userPhone, profileComplete, pendingRatingRideId). DataStore on Android is backed by Proto or Preferences file in the app's private data directory — not accessible to other apps without root. This is correct.

**Room database** stores `RideEntity` and `NotificationEntity` in a SQLite file in the app's private data directory. No sensitive financial data is stored locally — final fare and payment status are fetched from the server on demand. This is correct.

**Finding SEC-18 (MEDIUM): No database encryption (SQLCipher)**
Room uses an unencrypted SQLite file. On rooted devices, another app or ADB can read ride history and notification data. For a financial app handling ride payment data, SQLCipher integration (via `room-database-sqlcipher`) adds a layer of protection.

**Finding SEC-19 (LOW): No screenshot prevention on sensitive screens**
Payment confirmation and receipt screens do not set `WindowManager.LayoutParams.FLAG_SECURE`. Screenshots of fare receipts or payment details can be taken by other apps using `MediaProjection`.
- **Fix:** Add `window.setFlags(FLAG_SECURE, FLAG_SECURE)` on `PaymentScreen` and `ReceiptScreen`.

---

### 3.4 Code Obfuscation (Gaps)

**Finding SEC-20 (HIGH): No ProGuard keep rules for Gson DTOs**
Release builds have `isMinifyEnabled = true`. Gson uses reflection to map JSON field names to Kotlin property names. R8 minification renames fields, breaking deserialization silently. All DTO classes in `data/remote/dto/` need `@Keep` or corresponding ProGuard rules.

**Fix option A (fastest):** Add `@Keep` annotation to all DTO data classes.
**Fix option B (recommended):** Switch to `kotlinx.serialization` which uses compile-time codegen — not reflection — and is unaffected by minification.

---

## 4. Backend Performance

### 4.1 Config Cache (Three-Tier — Strong)

`fare.service.ts` implements a three-tier config cache:
- **L1:** In-process `Map<string, {value, expiresAt}>` — zero-latency, no network hop.
- **L2:** Redis — shared across server instances, 60-second TTL.
- **L3:** PostgreSQL `platform_config` table — source of truth.

Cache miss path: L1 miss → L2 miss → DB read → populate L1 + L2. This means a cold start or after a config change, one request pays the DB cost; all subsequent requests within 60 seconds are served from L1. This is correct and fast.

**Observation:** The L1 cache is per-process. If the server is horizontally scaled (3 instances), config changes take up to 60 seconds to propagate to all instances. This is acceptable for business rule configs (fare rates, cancellation fees) which rarely change mid-ride.

---

### 4.2 Database Queries (Good with gaps)

**Verified indexes** in Prisma schema:
```
users:            phone, role
driver_profiles:  verificationStatus, isOnline, (currentLat, currentLng)
rides:            customerId, driverId, status, (isScheduled, scheduledAt), createdAt
ride_events:      rideId, eventType
earnings:         driverProfileId, settlementStatus, settlementDueDate
notifications:    (userId, isRead), sentAt
otp_verifications: (phone, otpCode), expiresAt
ride_share_links: (rideId, revokedAt), expiresAt
```

**Finding PERF-01 (MEDIUM): Driver search is a full table scan within radius**
`ride.service.ts` `searchAndNotifyDrivers` queries `driverProfile.findMany` with a latitude/longitude bounding box filter. Bounding box queries on `(currentLat, currentLng)` use a B-tree index — this can do range scans but is not as efficient as a PostGIS spatial index.

The `postgis/postgis:16-3.4-alpine` Docker image is used in CI (PostGIS is available). PostGIS indexes (GiST on `geography` columns) exist from migration `postgis_indexes`. However, the Prisma client cannot use PostGIS operators via the standard API — the driver search may be falling back to the B-tree composite index.

**Fix (if confirmed):** Use Prisma `$queryRaw` with `ST_DWithin(geography, geography, radius_meters)` for the driver search. This uses the GiST index and runs a true spherical distance check — far more accurate and faster than a bounding box.

**Finding PERF-02 (LOW): `getRideHistory` has no pagination by default**
`GET /rides/history` defaults to `page=1, limit=10` from the validator. This is fine. However, the admin `GET /admin/rides` endpoint has a default limit of 20 with no upper bound cap in the validator — a caller could request `limit=10000`.
- **Fix:** Add `.max(100)` to the admin rides limit in `admin.validator.ts`.

**Finding PERF-03 (LOW): N+1 on notification read**
`notificationService.getNotifications` returns notifications sorted by `sentAt`. If the client calls this endpoint and then resolves each notification's associated ride data separately, it becomes N+1. Currently the notification response does not include ride data, so this is not currently triggered — but worth noting if ride context is added to notifications.

---

### 4.3 Redis & BullMQ (Strong)

**BullMQ configuration (verified):**
- `chalo-rides` queue: per-ride `ride-offer-expired` delayed jobs. Job data contains `rideId` and `batchDriverIds`. Delayed by `BROADCAST_BATCH_TTL_MS = 65000ms`.
- `chalo-maintenance` queue: phantom driver cleanup cron (`*/30 * * * *` = every 30 minutes), scheduled ride dispatch cron (`*/5 * * * *` = every 5 minutes).

**Finding PERF-04 (LOW): BullMQ concurrency not explicitly set**
BullMQ workers default to concurrency=1. If the ride-offer-expired job processor is slow (e.g., Firebase FCM send + DB write takes 2 seconds), and 50 rides expire simultaneously, 49 jobs queue behind the first. Explicitly setting `concurrency: 5` on the worker would parallelize these.

---

### 4.4 Fare Calculation (Google Maps Circuit Breaker)

The fare service uses Google Maps Directions API as the primary route calculator. When the API is unavailable:
1. Circuit breaker opens after 3 failures in 30 seconds.
2. Fallback: Haversine straight-line × 1.3 road factor.
3. Speed: 25 km/h average → duration estimate.

**Finding PERF-05 (MEDIUM): Haversine fallback consistently underestimates**
Faridabad road network is grid-like in sectors but irregular in the old town area. A 1.3x road factor is a global estimate. Real road-to-straight-line ratio in dense urban grids is 1.4–1.6x. During circuit breaker fallback, customers are systematically undercharged by 10–20%.
- **Fix:** Increase fallback road factor to 1.45 and duration speed to 20 km/h (Faridabad peak congestion average).

---

### 4.5 Firebase RTDB (Good with cautions)

Driver location updates come from the driver app → Firebase RTDB → customer Android app. The customer app observes two RTDB paths per active ride:

```
/rides/{rideId}/status
/rides/{rideId}/driver_location
```

**Finding PERF-06 (HIGH): RTDB listeners not cancelled when app is backgrounded on Android 12+**
`ActiveRideViewModel` collects two `Flow`s from `RtdbRepository`. These flows hold RTDB WebSocket connections. On Android 12+, apps in the background have their coroutines suspended by the system after ~10 minutes (and sooner in low-memory situations). The RTDB listener disconnects, and the customer loses live tracking without knowing it.

**Fix:** Use a `ForegroundService` with `foregroundServiceType="location"` and a persistent notification ("Your ride is in progress") to keep the process alive while a ride is `IN_PROGRESS`. Tie the service start/stop to `ActiveRideViewModel` state transitions.

**Finding PERF-07 (LOW): No RTDB security rules specified**
The RTDB URL is `chalo-dev-default-rtdb.asia-southeast1.firebasedatabase.app`. RTDB security rules are configured in the Firebase Console, not in the codebase. If the rules are set to `read: true` (Firebase's default for new databases), any authenticated user can read any ride's location data — including other users' rides.

**Fix:** Verify RTDB rules enforce ride ownership: `".read": "auth.uid === data.child('customerId').val() || auth.uid === data.child('driverId').val()"`.

---

## 5. Android Performance

### 5.1 Startup (Not yet profiled)

The app uses Hilt for dependency injection. Hilt generates code at compile time (KSP), so DI has minimal startup cost. However:

**Finding PERF-08 (MEDIUM): NetworkModule creates Retrofit synchronously on app start**
`NetworkModule.kt` provides `Retrofit` and `OkHttpClient` as `@Singleton` objects, created eagerly at app startup. OkHttpClient initialization includes connection pool creation and DNS resolution warm-up. On a cold start on a mid-range device (2GB RAM), this adds to the initial startup time.

**Fix:** Use `@Provides @Singleton` with Hilt lazy injection where possible. The `Retrofit` instance is only needed when the first network call is made, not on app open.

### 5.2 Map Performance

`HomeScreen` and `ActiveRideScreen` use Google Maps Compose (`GoogleMap` composable). The map renders in a `SurfaceView` — heavy GPU usage.

**Finding PERF-09 (MEDIUM): Driver location marker updates trigger full Compose recomposition**
`ActiveRideViewModel` exposes `driverLocation: StateFlow<LatLng?>`. Every RTDB update (typically every 3–5 seconds from the driver app) triggers recomposition of `ActiveRideScreen`. If the full screen recomposes, the `GoogleMap` composable re-renders, which causes a map flicker.

**Fix:** Move driver location into a separate `derivedStateOf` or isolate the `Marker` update using `MarkerState` — only the marker position updates, not the entire map composable.

### 5.3 Memory

**Finding PERF-10 (LOW): No image caching strategy for driver/bike photos**
Driver profile cards and bike photos are fetched via URLs. Without explicit caching (Coil's `ImageLoader` is set up but caching policy not specified), repeated visits to profile screens re-fetch photos from the network.

---

## 6. OWASP Mobile Top 10 Checklist

| # | Risk | Status | Notes |
|---|---|---|---|
| M1 | Improper Credential Usage | Pass | No credentials hardcoded in APK. Secrets in local.properties (git-ignored). Firebase config in google-services.json (not sensitive on its own). |
| M2 | Inadequate Supply Chain Security | Pass | All dependencies pinned in `libs.versions.toml`. No dynamic version ranges. |
| M3 | Insecure Authentication/Authorization | Partial | Firebase Auth is correct. SOS at (0,0) is a data integrity issue. OTP is 4 digits. |
| M4 | Insufficient Input/Output Validation | Partial | Backend validates all inputs via Zod. Android has no local input validation before API calls. |
| M5 | Insecure Communication | Pass (debug) | TLS enforced in release. Cleartext only for 10.0.2.2 in debug. Issue: network_security_config.xml host IP bug. |
| M6 | Inadequate Privacy Controls | Fail | No SQLCipher. No ProGuard on DTOs. No FLAG_SECURE on financial screens. |
| M7 | Insufficient Binary Protections | Fail | No ProGuard keep rules for Gson DTOs — R8 will rename fields in release. |
| M8 | Security Misconfiguration | Partial | INTERNAL_API_KEY not in startup guard. RTDB rules not in source control. |
| M9 | Insecure Data Storage | Partial | Room DB unencrypted. DataStore is fine. No crash log redaction. |
| M10 | Insufficient Cryptography | Pass | Uses Firebase SDK crypto. OTP hashed (SHA-256, acceptable for short codes with rate-limiting). |

---

## 7. Top Risks by Priority

### Critical (fix before any real user data is collected)

| # | Finding | Location | Fix |
|---|---|---|---|
| 1 | SOS sends (0,0) coordinates | `ActiveRideScreen.kt:88` | FusedLocationProviderClient in ViewModel |
| 2 | No ProGuard keep rules for Gson DTOs | `proguard-rules.pro` | Add `@Keep` or switch to kotlinx.serialization |
| 3 | PII stored in plaintext (Aadhar, bank details) | `driver_profiles` DB table | Column-level encryption with pgcrypto |

### High (fix before public launch)

| # | Finding | Location | Fix |
|---|---|---|---|
| 4 | RTDB listeners die in background on Android 12+ | `ActiveRideViewModel` | ForegroundService with FOREGROUND_SERVICE_TYPE_LOCATION |
| 5 | Payment order ownership not verified | `payment.service.ts` | Add `customerId === req.user.uid` guard |
| 6 | INTERNAL_API_KEY not in startup guard | `server.ts` | Add to secret validation list |
| 7 | OTP is 4 digits (brute-force risk) | `constants.ts` + Android | Upgrade to 6 digits |

### Medium (fix before scale)

| # | Finding | Location | Fix |
|---|---|---|---|
| 8 | No rate limit on `/auth/otp/verify` | `auth.routes.ts` | Add dedicated rate limiter |
| 9 | Address fields have no max length | `ride.validator.ts` | Add `.max(500)` |
| 10 | Haversine fallback underestimates fare | `fare.service.ts` | Increase road factor to 1.45 |
| 11 | Driver search uses B-tree, not PostGIS GiST index | `ride.service.ts` | Use `$queryRaw` with `ST_DWithin` |
| 12 | RTDB security rules not in source control | Firebase Console | Export and document rules |

---

## 8. Immediate Action Plan

### Before any user data is collected

1. Fix SOS coordinates — wire `FusedLocationProviderClient` in `ActiveRideViewModel.onSosConfirm()`.
2. Add `@Keep` to all files in `data/remote/dto/` — prevents R8 from breaking Gson in release.
3. Verify payment order ownership check in `payment.service.ts`.
4. Add `INTERNAL_API_KEY` to server startup secret validation.

### Before public launch

5. Upgrade OTP to 6 digits across backend + Android + all tests.
6. Wire SMS gateway (MSG91 or Fast2SMS) to `sms.service.ts`.
7. Add `ForegroundService` for active ride background tracking.
8. Verify and document RTDB security rules — ride data must be owner-only readable.
9. Add address field `.max(500)` in Zod validators.
10. Plan column encryption for Aadhar, bank account, license numbers.

### Before scale (> 500 rides/day)

11. Replace Haversine driver search with `ST_DWithin` PostGIS query.
12. Replace Gson with `kotlinx.serialization` (eliminates ProGuard risk entirely).
13. Set BullMQ worker concurrency to 5 on `chalo-rides` queue.
14. Add OTP cleanup cron to BullMQ `chalo-maintenance` queue.
15. Profile Android cold start on Redmi 9 (representative mid-range Faridabad device).
