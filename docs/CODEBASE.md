# Chalo Codebase Reference

Last updated: 2026-03-21

Authoritative source for what is actually implemented. When this file conflicts with older review docs, trust this file and the source code. When in doubt, trust the source code.

---

## 1. What exists right now

Two modules in the monorepo:

- **`chalo-backend`** — Node.js/TypeScript REST API. 60 routed endpoints across 7 route groups. 320 passing tests. Fully running on port 3001.
- **`chalo-customer-app`** — Android app (Kotlin + Jetpack Compose). All major screen groups implemented. 53 JVM unit tests in `app/src/test`.

---

## 2. Workspace layout

```
Chalo/
├── chalo-backend/
│   ├── prisma/
│   │   ├── schema.prisma          # DB schema (no postgresqlExtensions — removed due to PostGIS issue)
│   │   ├── migrations/            # 10 migrations applied
│   │   └── seed.ts                # Seeds 13 platform_config keys
│   ├── src/
│   │   ├── config/                # env.ts, database.ts, redis.ts, firebase.ts, logger.ts
│   │   ├── middleware/            # auth, validate, idempotency, rateLimiter, requestId, sanitize, errorHandler
│   │   ├── routes/                # auth, ride, driver, payment, notification, admin, track
│   │   ├── controllers/           # HTTP layer — thin, delegates to services
│   │   ├── services/              # Business logic: auth, ride, driver, fare, payment, notification, sos, admin, kyc/*
│   │   ├── jobs/                  # queue.ts — BullMQ queue + worker definitions
│   │   ├── validators/            # Zod schemas for every route
│   │   └── utils/                 # constants.ts, apiError.ts, helpers.ts
│   └── src/__tests__/
│       ├── integration/           # api.integration.test.ts
│       ├── services/              # auth, ride, driver, fare, notification, sos, sms
│       └── validators/            # auth, ride, driver, payment
│
├── chalo-customer-app/
│   └── app/src/main/java/com/chalo/customer/
│       ├── ChaloApplication.kt    # Hilt app class, Timber init
│       ├── MainActivity.kt        # Single activity, NavHost root
│       ├── di/                    # NetworkModule, DatabaseModule, RepositoryModule
│       ├── data/
│       │   ├── remote/
│       │   │   ├── api/           # AuthApiService, RideApiService, PaymentApiService, NotificationApiService
│       │   │   ├── dto/           # AuthDtos, RideDtos, PaymentDtos, NotificationDtos, ApiResponse
│       │   │   └── interceptor/   # AuthInterceptor (auto-attaches Firebase ID token)
│       │   ├── local/
│       │   │   ├── AppDatabase.kt # Room DB v1 — RideEntity, NotificationEntity
│       │   │   ├── dao/           # RideDao, NotificationDao
│       │   │   ├── entity/        # RideEntity, NotificationEntity
│       │   │   └── preferences/   # UserPreferences (DataStore)
│       │   └── repository/        # AuthRepositoryImpl, RideRepositoryImpl, RtdbRepositoryImpl,
│       │                          # NotificationRepositoryImpl, PaymentRepositoryImpl
│       ├── domain/
│       │   ├── model/             # User, Ride, Notification (pure Kotlin, no Android deps)
│       │   └── repository/        # AuthRepository, RideRepository, RtdbRepository,
│       │                          # NotificationRepository, PaymentRepository (interfaces)
│       └── presentation/
│           └── screens/
│               ├── auth/          # Splash, PhoneInput, OtpVerify, CompleteProfile (+ ViewModels)
│               ├── home/          # Home, FareEstimate (+ ViewModels)
│               ├── activeride/    # ActiveRide (+ ViewModel)
│               ├── postride/      # Payment, Rating, Receipt (+ ViewModels)
│               ├── history/       # RideHistory, RideDetail (+ ViewModels)
│               ├── profile/       # Profile, EmergencyContact, SavedLocations (+ ViewModels)
│               ├── notifications/ # Notifications (+ ViewModel)
│               └── scheduled/     # ScheduleRide, ScheduledList (+ ViewModels)
│
└── docs/
    ├── CODEBASE.md                # This file
    ├── api/                       # POSTMAN_GUIDE.md, token-exchange-guide.md
    ├── development/               # NEXT_STEPS.md, EMULATOR_SETUP.md, FRIEND_SETUP.md, IMPROVEMENTS.md
    ├── reviews/                   # CODE_REVIEW.md, SECURITY_PERFORMANCE_REVIEW.md
    ├── design/                    # UI/UX HTML mockups
    └── product/                   # chalo-product-documentation.md
```

---

## 3. Backend endpoint surface

Total: **60 endpoints** across 7 route groups. All under `/api/v1`.

### Auth — 8 endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/otp/send` | Public | Send 6-digit OTP to phone via MSG91 SMS. Rate limited: 3/15 min |
| POST | `/auth/otp/verify` | Public | Verify OTP → returns Firebase custom token |
| POST | `/auth/register-driver` | Public | OTP verify + driver account creation in one call |
| GET  | `/auth/profile` | Bearer | Get current user profile |
| PUT  | `/auth/profile` | Bearer | Complete or update profile (name, email, languagePref) |
| PUT  | `/auth/emergency-contact` | Bearer | Set emergency contact name + phone |
| PUT  | `/auth/saved-location` | Bearer | Save home or work location |
| PUT  | `/auth/device-token` | Bearer | Register FCM token for push notifications |

### Rides — 14 endpoints (CUSTOMER role)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/rides/fare-estimate` | Bearer | Get estimated fare for pickup → drop |
| POST | `/rides` | CUSTOMER + Idempotency-Key | Create on-demand ride |
| POST | `/rides/schedule` | CUSTOMER + Idempotency-Key | Create scheduled ride (future datetime, max 7 days) |
| GET  | `/rides/history` | CUSTOMER | Paginated ride history. Query: `page`, `limit`, `status` |
| GET  | `/rides/scheduled` | CUSTOMER | List upcoming scheduled rides |
| GET  | `/rides/:rideId` | Bearer | Get ride details (status, driver, OTP, fare) |
| GET  | `/rides/:rideId/location` | Bearer | Get current driver lat/lng from RTDB |
| GET  | `/rides/:rideId/receipt` | CUSTOMER | Fare breakdown for completed ride |
| POST | `/rides/:rideId/cancel` | CUSTOMER | Cancel ride with structured reason code |
| POST | `/rides/:rideId/share` | CUSTOMER | Generate public tracking share link |
| POST | `/rides/:rideId/rate` | CUSTOMER | Rate driver (1–5) after completion |
| POST | `/rides/:rideId/skip-rating` | CUSTOMER | Mark rating as permanently skipped |
| POST | `/rides/:rideId/sos` | Bearer | Trigger SOS alert with current location |
| POST | `/rides/sos/:sosAlertId/resolve` | Bearer | Resolve an active SOS alert |

### Driver — 19 endpoints (DRIVER role)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/driver/documents` | DRIVER | Submit KYC documents for admin review |
| POST | `/driver/go-online` | DRIVER | Go online with current GPS location |
| POST | `/driver/go-offline` | DRIVER | Go offline |
| GET  | `/driver/status` | DRIVER | Current online/offline state + active ride |
| POST | `/driver/location` | DRIVER | Update GPS location (called periodically) |
| GET  | `/driver/rides/incoming` | DRIVER | Poll for pending ride offer |
| POST | `/driver/rides/:rideId/accept` | DRIVER | Accept an incoming ride |
| POST | `/driver/rides/:rideId/decline` | DRIVER | Decline a ride offer |
| POST | `/driver/rides/:rideId/arrived` | DRIVER | Mark arrived at pickup |
| POST | `/driver/rides/:rideId/start` | DRIVER | Start ride (requires customer's 4-digit ride-start OTP) |
| POST | `/driver/rides/:rideId/complete` | DRIVER | Complete the ride |
| POST | `/driver/rides/:rideId/cancel` | DRIVER | Cancel active ride |
| POST | `/driver/rides/:rideId/rate-customer` | DRIVER | Rate customer (1–5) after completion |
| GET  | `/driver/trips` | DRIVER | Paginated trip history |
| GET  | `/driver/earnings` | DRIVER | Earnings list — period: `today`/`week`/`month` |
| GET  | `/driver/earnings/summary` | DRIVER | Aggregated totals — period: `week`/`month` |
| GET  | `/driver/earnings/settlement` | DRIVER | Settled vs unsettled balance |
| POST | `/driver/withdrawals` | DRIVER | Request payout (UPI, bank transfer, or cash agent) |
| GET  | `/driver/withdrawals/:withdrawalId` | DRIVER | Check withdrawal status |

### Payments — 3 endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/payments/order` | Bearer | Create Razorpay order for UPI ride |
| POST | `/payments/verify` | Bearer | Verify Razorpay payment signature |
| POST | `/payments/webhook` | None (signature) | Razorpay webhook — raw body, verified by HMAC |

### Notifications — 4 endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| GET   | `/notifications` | Bearer | Paginated list. Query: `page`, `limit` |
| GET   | `/notifications/unread-count` | Bearer | Count of unread notifications |
| PATCH | `/notifications/:notificationId/read` | Bearer | Mark one as read |
| PATCH | `/notifications/read-all` | Bearer | Mark all as read |

### Admin — 10 endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/admin/promote` | x-internal-api-key header | Promote existing user to ADMIN role |
| GET  | `/admin/drivers/pending` | ADMIN | List drivers awaiting KYC approval |
| GET  | `/admin/drivers/:driverId` | ADMIN | Driver detail + documents |
| POST | `/admin/drivers/:driverId/approve` | ADMIN | Approve driver KYC |
| POST | `/admin/drivers/:driverId/reject` | ADMIN | Reject driver KYC with reason |
| POST | `/admin/drivers/:driverId/auto-verify` | ADMIN | Trigger KYC provider (Surepass or manual) |
| GET  | `/admin/rides/live` | ADMIN | All currently active rides |
| GET  | `/admin/rides` | ADMIN | Filtered ride list (status, paymentStatus, pagination) |
| GET  | `/admin/config` | ADMIN | All 13 platform config values |
| PUT  | `/admin/config/:key` | ADMIN | Update a single config value |

### Track — 1 endpoint

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/track/:token` | None | Public ride tracking via share token |

---

## 4. Backend: key implementation details

### Auth flow

1. `POST /auth/otp/send` — generates a 6-digit OTP (SHA-256 hashed in DB), calls MSG91 SMS API in production (or prints to console in dev).
2. `POST /auth/otp/verify` — validates OTP from Redis, creates or retrieves user in DB, mints a Firebase custom token via Firebase Admin SDK, returns it with user data.
3. Client signs into Firebase with the custom token (`signInWithCustomToken`). Firebase SDK then manages the ID token lifecycle (auto-refresh, expiry).
4. All protected routes receive a Firebase ID token in `Authorization: Bearer`. The `authenticate` middleware verifies it server-side via Firebase Admin.

### Ride lifecycle states

```
REQUESTED → DRIVER_ASSIGNED → DRIVER_ARRIVED → IN_PROGRESS → COMPLETED
                                                            ↘ CANCELLED
REQUESTED → NO_DRIVER  (broadcast exhausted, no one accepted)
SCHEDULED  (pending dispatch, transitions to REQUESTED when scheduled time arrives)
```

### Driver broadcast (dispatch)

- When a ride is created, top-5 nearest online+approved drivers within initial radius receive FCM push simultaneously.
- Each batch has a 30-second acceptance window (`RIDE_OFFER_BATCH_TTL_SECS`).
- BullMQ delayed job (`ride-offer-expired`) fires after window expires. If no acceptance, next batch is dispatched at expanded radius (`driver_search_radius_km_expanded` config key, default 12km).
- Redis coordinates the offer state and prevents double-assignment.

### Cancellation policy

- Fee is applied only when status is `DRIVER_ARRIVED` or later.
- Fee amount is a platform config key: `cancel_fee_arrived_amount` (default ₹40).
- Driver-fault reason codes (`DRIVER_ASKED_TO_CANCEL`, `DRIVER_NOT_MOVING`, `DRIVER_WRONG_VEHICLE`, `DRIVER_BEHAVIOUR`) waive the fee regardless of status.
- Serial canceller policy: Redis tracks cancellation count per user. Threshold enforced server-side.

### Fare calculation

- Base fare + per-km rate + booking fee + surge multiplier.
- GST applied on top as a percentage: `gst_percentage` config key (default 5%).
- Minimum fare enforced: `minimumFare` returned in estimate response, `minimumFareApplied` flag set.
- All values in rupees (integers).

### KYC

- Pluggable provider interface: `IKYCProvider`.
- `ManualKYCProvider` — default when `SUREPASS_API_KEY` is not set. No-op for auto-verify; admin manually approves/rejects via panel.
- `SurepassKYCProvider` — used when `SUREPASS_API_KEY` is set in env. Calls external verification API.

### BullMQ jobs

Two queues defined in `src/jobs/queue.ts`:

- `chalo-maintenance` — periodic cron jobs: phantom driver cleanup, scheduled ride dispatch.
- `chalo-rides` — delayed jobs: `ride-offer-expired` (per-ride, fires after broadcast window).

### Platform config keys (seeded by `npm run db:seed`)

| Key | Default | Purpose |
|---|---|---|
| `base_fare` | varies | Minimum base charge |
| `per_km_rate` | varies | Per-km charge |
| `booking_fee` | varies | Flat booking fee |
| `minimum_fare` | varies | Floor fare |
| `surge_multiplier` | `1.0` | Peak pricing multiplier |
| `gst_percentage` | `5` | GST on fare |
| `cancel_fee_arrived_amount` | `40` | Cancellation fee after driver arrived |
| `driver_search_radius_km` | varies | Initial broadcast radius |
| `driver_search_radius_km_expanded` | `12` | Expanded radius if first pass fails |
| `ride_offer_batch_ttl_secs` | varies | Broadcast window per batch |
| `serial_cancel_threshold` | varies | Cancellations before block |
| `max_schedule_days` | `7` | How far ahead a ride can be scheduled |
| `phantom_driver_ttl_mins` | varies | Stale online-driver cleanup threshold |

### Middleware stack (applied per-request)

```
requestId → sanitize → [rateLimiter] → authenticate → [authorize(role)] → validateBody/Params/Query → [idempotency] → controller → errorHandler
```

- `idempotency` — Redis-based. Required on POST /rides and POST /rides/schedule. Same key = same response, no duplicate ride.
- `rateLimiter` — auth endpoints: 5/15 min. Ride creation: separate limit. Webhook: separate limit.
- `sanitize` — strips prototype pollution and XSS from request body.
- `errorHandler` — converts `ApiError` instances and unexpected errors to structured JSON with `success: false`.

### Database migrations (10 applied)

```
1. init
2. postgis_indexes
3. verification_metadata
4. ride_otp_and_driver_rating
5. add_gst_amount
6. add_rating_skipped_at
7. add_cancellation_reason_code
8. add_driver_accept_decline_and_customer_cooldown
9. add_ride_share_links
10. add_driver_cancellation_tracking
```

Run with: `DATABASE_URL="postgresql://postgres:luffy@localhost:5433/chalo_db?schema=public" npx prisma migrate deploy`

Do **not** use `prisma migrate dev` — shadow DB creation fails with PostGIS on Windows.

---

## 5. Android customer app: implementation details

### Architecture

MVVM + Repository pattern. Clean separation:

- **Presentation layer**: Compose screens + HiltViewModels. State managed via `StateFlow`. One-time events via `Channel`.
- **Domain layer**: Pure Kotlin interfaces and models. No Android framework dependencies.
- **Data layer**: Repository implementations, Retrofit API services, Room DAOs, DataStore.

### Dependency injection (Hilt)

- `NetworkModule` — provides `OkHttpClient` (with `AuthInterceptor` + `HttpLoggingInterceptor`), `Retrofit` (base URL from `BuildConfig.BASE_URL`), and all four API service interfaces.
- `DatabaseModule` — provides `AppDatabase` (Room) and DAOs.
- `RepositoryModule` — binds repository interfaces to implementations.

### Network layer

**OkHttp + Retrofit configuration:**
- Base URL: `BuildConfig.BASE_URL` (from `local.properties` in debug; hardcoded production URL in release)
- Timeouts: 30s connect / 30s read / 30s write
- Logging: BODY level in debug, NONE in release
- Auth interceptor: attached before logging interceptor

**AuthInterceptor behaviour:**
1. On each request: calls `FirebaseAuth.getInstance().currentUser?.getIdToken(false)` (non-refreshing).
2. Injects result as `Authorization: Bearer <token>`.
3. On 401 response: calls `getIdToken(forceRefresh = true)` and retries the request once.
4. If no Firebase user is logged in, no header is attached (unauthenticated request proceeds as-is).

**API services (Retrofit interfaces):**

| Interface | Endpoints |
|---|---|
| `AuthApiService` | sendOtp, verifyOtp, getProfile, completeProfile, updateEmergencyContact, updateSavedLocation, registerDeviceToken |
| `RideApiService` | getFareEstimate, createRide, scheduleRide, getRideHistory, getScheduledRides, getRideDetails, getRideLocation, getRideReceipt, cancelRide, shareRide, rateRide, skipRating, triggerSos |
| `PaymentApiService` | createOrder, verifyPayment |
| `NotificationApiService` | getNotifications, getUnreadCount, markAsRead, markAllAsRead |

### Local persistence

**Room database (`AppDatabase`, version 1):**

| Entity | Purpose |
|---|---|
| `RideEntity` | Offline cache of ride data |
| `NotificationEntity` | Offline cache of notifications |

**DataStore (`UserPreferences`):**

| Key | Type | Purpose |
|---|---|---|
| `userId` | String | Logged-in user ID |
| `userName` | String | Display name |
| `userPhone` | String | Phone number |
| `profileComplete` | Boolean | Whether profile setup is done |
| `pendingRatingRideId` | String | Ride awaiting post-trip rating prompt |
| `pendingRatingShown` | Boolean | Whether rating prompt has been shown |
| `pendingRatingTime` | Long | Timestamp when rating was saved (for staleness check) |

### Firebase integration

- `google-services.json` — present at `app/google-services.json`. Project: `chalo-dev`.
- Firebase RTDB URL: `https://chalo-dev-default-rtdb.asia-southeast1.firebasedatabase.app`
- `ChaloFirebaseMessagingService` — handles FCM push notifications. Default channel: `chalo_rides`.
- `RtdbRepositoryImpl` — reads driver location updates from RTDB in real time during active ride.
- Token management: fully handled by Firebase SDK after `signInWithCustomToken`. App never stores or manages raw tokens.

### OTP + sign-in flow (detailed)

```
PhoneInputViewModel
  → authRepository.sendOtp(phone)           # POST /auth/otp/send
  → navigate to OtpVerifyScreen

OtpVerifyViewModel
  → authRepository.verifyOtp(phone, otp)    # POST /auth/otp/verify
  → FirebaseAuth.signInWithCustomToken()    # exchanges custom token → Firebase session
  → userPrefs.saveUser(...)                 # persists user info in DataStore
  → emit Verified(isNewUser)
  → navigate: isNewUser → CompleteProfile, else → Home

SplashViewModel
  → checks FirebaseAuth.currentUser         # non-null = returning user
  → navigate: logged in → Home, else → PhoneInput
```

### Screen inventory (36 files = 18 screens + 18 ViewModels)

| Screen group | Screens | ViewModels |
|---|---|---|
| auth | SplashScreen, PhoneInputScreen, OtpVerifyScreen, CompleteProfileScreen | SplashViewModel, PhoneInputViewModel, OtpVerifyViewModel, CompleteProfileViewModel |
| home | HomeScreen, FareEstimateScreen | HomeViewModel, FareEstimateViewModel |
| activeride | ActiveRideScreen | ActiveRideViewModel |
| postride | PaymentScreen, RatingScreen, ReceiptScreen | PaymentViewModel, RatingViewModel, ReceiptViewModel |
| history | RideHistoryScreen, RideDetailScreen | RideHistoryViewModel, RideDetailViewModel |
| profile | ProfileScreen, EmergencyContactScreen, SavedLocationsScreen | ProfileViewModel, EmergencyContactViewModel, SavedLocationsViewModel |
| notifications | NotificationsScreen | NotificationsViewModel |
| scheduled | ScheduleRideScreen, ScheduledListScreen | ScheduleRideViewModel, ScheduledListViewModel |

### Build config

| Field | Debug value | Release value |
|---|---|---|
| `BASE_URL` | `DEV_BASE_URL` from `local.properties` | `https://api.chalo.in/api/v1` (hardcoded) |
| `FIREBASE_DATABASE_URL` | `https://chalo-dev-default-rtdb.asia-southeast1.firebasedatabase.app` | same |
| `MAPS_API_KEY` | From `local.properties` | From `local.properties` (must be set for release too) |
| `isMinifyEnabled` | false | true |
| `isShrinkResources` | false | true |
| `isDebuggable` | true | false |

- `minSdk`: 28 (Android 9 Pie)
- `targetSdk`: 34
- `compileSdk`: 34

---

## 6. Testing coverage

### Backend — 320 passing tests

| Category | Files | What's covered |
|---|---|---|
| Integration | `api.integration.test.ts` | Full HTTP request/response cycle against real Express app |
| Services | auth, ride, driver, fare, notification, sos, sms | Business logic with mocked DB and Redis |
| Validators | auth, ride, driver, payment | Zod schema edge cases, invalid inputs, boundary values |
| Utils | constants, helpers, apiError | Pure function correctness |

Run: `cd chalo-backend && npm test`

### Android — 53 passing JVM tests

Android unit tests now exist under `app/src/test/` and cover ViewModel behavior and DTO/repository mapping contracts.

Run: `gradle -p chalo-customer-app test`

**What still needs to be written:**

Instrumentation tests (Espresso + Compose test, in `src/androidTest/`):
- OTP flow: enter phone → enter OTP → verify Firebase sign-in + navigation
- Book ride flow: fare estimate → create ride → confirmation screen
- Active ride status: ride status polling → UI updates on status change
- Post-ride: payment screen → rating screen → receipt screen

---

## 7. Known gaps and issues

### Android instrumentation coverage (high)

JVM unit tests exist and run in CI, but there are still no instrumentation tests in `src/androidTest/`. UI/navigation regressions can still slip through without emulator/device-level automation.

### DTO contract fragility

The Android DTOs (`RideDtos.kt`, `AuthDtos.kt`, etc.) are manually maintained and not generated from the backend Zod schemas. A field rename or type change on the backend produces a silent null in the app rather than a build error. There is no contract enforcement layer between the two sides.

### Network security config IP entries

`network_security_config.xml` lists `192.168.0.0` and `192.168.1.0` as allowed cleartext domains for physical device testing. These are network addresses, not host IPs, and do not work. The actual host machine IP (e.g. `192.168.1.105`) must be added manually per developer environment.

### Android CI dependency on global Gradle

GitHub Actions CI runs Android lint, unit tests, and debug APK assembly in `.github/workflows/ci.yml`. Since `gradlew` is not committed in `chalo-customer-app`, the workflow uses a configured Gradle version (`gradle -p chalo-customer-app ...`). Keep Gradle and AGP versions aligned when upgrading Android build tooling.

### Documentation lag risk

Docs (including this file) are updated manually. Route additions or schema changes that are not reflected here will cause confusion for new contributors. The POSTMAN_GUIDE.md was refreshed in March 2026 but has no automated validation.

---

## 8. Build and run reference

### Backend daily commands

```bash
cd chalo-backend

# Development (hot reload via ts-node-dev)
npm run dev

# Run all tests
npm test

# Apply migrations (use migrate deploy, not migrate dev)
DATABASE_URL="postgresql://postgres:luffy@localhost:5433/chalo_db?schema=public" npx prisma migrate deploy

# Seed platform config
npm run db:seed

# Type check
npx tsc --noEmit
```

### Android

- **Debug run**: Android Studio → select emulator → Run (Shift+F10)
- **Base URL**: reads `DEV_BASE_URL` from `local.properties`, falls back to `http://10.0.2.2:3001/api/v1`
- **Maps key**: `MAPS_API_KEY` in `local.properties` → injected via manifest placeholder
- **minSdk**: 28 — emulator or device must be API 28+
- **Emulator image**: must be "Google APIs" or "Google Play" (not plain AOSP) for Maps + Firebase

See [docs/development/EMULATOR_SETUP.md](development/EMULATOR_SETUP.md) for the full setup walkthrough.

### Local infra

| Container | External port | Internal port | Purpose |
|---|---|---|---|
| `chalo-postgres` | 5433 | 5432 | PostgreSQL + PostGIS |
| `chalo-redis` | 6379 | 6379 | Redis |
| `chalo-api` | 5000 | 5000 | Dockerised API (not used in dev) |

Use `DATABASE_URL` with port **5433** when running commands from the host machine. Port **5432** is for container-to-container communication (inside Docker network).

---

## 9. Documentation map

| File | Purpose |
|---|---|
| `docs/CODEBASE.md` | This file — implementation truth |
| `docs/api/POSTMAN_GUIDE.md` | Full API testing guide — all 60 endpoints with bodies and expected responses |
| `docs/api/token-exchange-guide.md` | How to exchange custom token for Firebase ID token in Postman |
| `docs/development/EMULATOR_SETUP.md` | Step-by-step guide to run app on Android emulator |
| `docs/development/NEXT_STEPS.md` | All pending work — testing, release readiness, product ops |
| `docs/development/IMPROVEMENTS.md` | Prioritised improvement backlog |
| `docs/development/FRIEND_SETUP.md` | Onboarding guide for new developers |
| `docs/reviews/CODE_REVIEW.md` | Historical code review notes |
| `docs/reviews/SECURITY_PERFORMANCE_REVIEW.md` | Security and performance review |
| `README.md` | Quick start |
