# Chalo — Complete Project Reference

**Last updated:** March 2026
**Status:** Backend production-ready (60 endpoints, 321 tests passing). Android customer app feature-complete, pre-launch hardening in progress.

---

## Table of Contents

1. [Project Summary](#1-project-summary)
2. [Monorepo Structure](#2-monorepo-structure)
3. [Tech Stack](#3-tech-stack)
4. [Backend Architecture](#4-backend-architecture)
5. [Database Schema](#5-database-schema)
6. [API Endpoint Reference](#6-api-endpoint-reference)
7. [Background Job System](#7-background-job-system)
8. [Android Customer App Architecture](#8-android-customer-app-architecture)
9. [Screen Inventory and Navigation](#9-screen-inventory-and-navigation)
10. [Firebase Integration](#10-firebase-integration)
11. [Security Posture](#11-security-posture)
12. [Performance Architecture](#12-performance-architecture)
13. [CI/CD Pipeline](#13-cicd-pipeline)
14. [Local Development Setup](#14-local-development-setup)
15. [Testing Coverage](#15-testing-coverage)
16. [Known Gaps and Pending Work](#16-known-gaps-and-pending-work)
17. [Design Decisions and Principles](#17-design-decisions-and-principles)

---

## 1. Project Summary

**Chalo** is a bike ride-hailing platform built for Faridabad, Haryana (North India). The product connects customers booking on-demand or scheduled bike rides with nearby driver partners, all managed through a real-time backend with an admin operations panel.

### Target Users

| Persona | Goal |
|---|---|
| Customer commuter | Quick bike ride booking, predictable fare, live tracking, safety |
| Driver partner | Reliable ride demand, simple ride workflow, clear earnings |
| Admin / operator | Driver verification, live ops visibility, platform config |

### V1 Scope

- OTP-based phone authentication (Firebase custom token flow)
- Fare estimation using Google Maps Directions API with Haversine fallback
- On-demand and scheduled ride booking (up to 7 days ahead)
- Real-time driver assignment via simultaneous FCM broadcast to top-5 nearby drivers
- Ride lifecycle tracking via Firebase RTDB (status + driver GPS)
- Post-ride payment (Cash or Razorpay UPI), customer rating, and receipt
- Emergency SOS with GPS coordinates sent to emergency contacts
- Driver KYC submission and admin approval workflow
- Notification centre (push + in-app log)
- Admin panel: driver management, live ride monitoring, platform config

### Out of Scope (V1)

- iOS app
- Driver Android app (schema and endpoints exist; app not yet built)
- In-app wallet
- Promo / referral engine
- Automated driver payouts via Razorpay Route
- Dynamic surge automation beyond time-of-day baseline

---

## 2. Monorepo Structure

```
Chalo/
├── chalo-backend/                  Node.js/TypeScript REST API
│   ├── prisma/
│   │   ├── schema.prisma           PostgreSQL + PostGIS schema (13 models, 10 enums)
│   │   ├── migrations/             10 applied migrations
│   │   └── seed.ts                 Seeds 13 platform_config keys
│   └── src/
│       ├── config/                 env.ts, database.ts, redis.ts, firebase.ts, logger.ts
│       ├── middleware/             auth, validate, idempotency, rateLimiter, requestId, sanitize, errorHandler
│       ├── routes/                 auth, ride, driver, payment, notification, admin, track
│       ├── controllers/            HTTP layer — thin, delegates to services
│       ├── services/               auth, ride, driver, fare, payment, notification, sos, admin, kyc/*
│       ├── jobs/                   queue.ts — BullMQ queues and workers
│       ├── validators/             Zod schemas for every route
│       ├── telemetry/              OpenTelemetry bootstrap + trace context propagation
│       ├── utils/                  constants.ts, apiError.ts, helpers.ts
│       └── __tests__/
│           ├── integration/        Full HTTP cycle against real Express app
│           ├── services/           auth, ride, driver, fare, notification, sos, sms
│           └── validators/         auth, ride, driver, payment
│
├── chalo-customer-app/             Android app (Kotlin + Jetpack Compose)
│   └── app/src/main/java/com/chalo/customer/
│       ├── ChaloApplication.kt     Hilt entry point, Timber init, FeatureFlags init
│       ├── MainActivity.kt         Single-activity host, NavHost root
│       ├── di/                     NetworkModule, DatabaseModule, RepositoryModule
│       ├── data/
│       │   ├── remote/
│       │   │   ├── api/            AuthApiService, RideApiService, PaymentApiService, NotificationApiService
│       │   │   ├── dto/            AuthDtos, RideDtos, PaymentDtos, NotificationDtos, ApiResponse
│       │   │   └── interceptor/    AuthInterceptor (auto-attaches + auto-refreshes Firebase ID token)
│       │   ├── local/
│       │   │   ├── AppDatabase.kt  Room DB v1 — RideEntity, NotificationEntity
│       │   │   ├── dao/            RideDao, NotificationDao
│       │   │   ├── entity/         RideEntity, NotificationEntity
│       │   │   ├── DatabaseMigrations.kt  Centralized migration registry
│       │   │   └── preferences/    UserPreferences (DataStore)
│       │   └── repository/         AuthRepositoryImpl, RideRepositoryImpl, RtdbRepositoryImpl,
│       │                           NotificationRepositoryImpl, PaymentRepositoryImpl, FeatureFlagRepositoryImpl
│       ├── domain/
│       │   ├── model/              User, Ride, Notification (pure Kotlin, no Android deps)
│       │   └── repository/         AuthRepository, RideRepository, RtdbRepository,
│       │                           NotificationRepository, PaymentRepository, FeatureFlagRepository
│       ├── presentation/
│       │   ├── navigation/         NavGraph.kt, Routes.kt
│       │   ├── components/         ChaloComponents.kt (shared composables)
│       │   ├── theme/              Color.kt, Typography.kt, Theme.kt
│       │   └── screens/
│       │       ├── auth/           Splash, PhoneInput, OtpVerify, CompleteProfile
│       │       ├── home/           Home, FareEstimate
│       │       ├── activeride/     ActiveRide
│       │       ├── postride/       Payment, Rating, Receipt
│       │       ├── history/        RideHistory, RideDetail
│       │       ├── profile/        Profile, EmergencyContact, SavedLocations
│       │       ├── notifications/  Notifications
│       │       └── scheduled/      ScheduleRide, ScheduledList
│       └── service/
│           └── ChaloFirebaseMessagingService.kt
│
└── docs/
    ├── PROJECT_OVERVIEW.md         This file
    ├── CODEBASE.md                 Implementation truth — all endpoints, screens, migrations
    ├── api/                        POSTMAN_GUIDE.md, CUSTOMER_APP_API_GUIDE.md, token-exchange-guide.md
    ├── development/                NEXT_STEPS.md, EMULATOR_SETUP.md, FRIEND_SETUP.md,
    │                               BACKEND_FLOWS.md, IMPROVEMENTS.md, INCIDENT_RUNBOOK.md,
    │                               BACKUP_DISASTER_RECOVERY.md
    ├── reviews/                    CODE_REVIEW.md, SECURITY_PERFORMANCE_REVIEW.md
    ├── design/                     UI/UX HTML mockups
    └── product/                    chalo-product-documentation.md
```

---

## 3. Tech Stack

### Backend

| Layer | Technology | Purpose |
|---|---|---|
| Runtime | Node.js 20 | Server runtime |
| Framework | Express 4 | HTTP routing and middleware |
| Language | TypeScript (strict mode) | Type safety |
| ORM | Prisma 5 | DB access, migrations |
| Database | PostgreSQL 16 + PostGIS 3.4 | Primary datastore + geospatial queries |
| Cache / Queues | Redis 7 + BullMQ | Rate limiting, idempotency, background jobs, driver offer state |
| Auth | Firebase Admin SDK | Token verification (cryptographic, no DB hit) |
| Realtime | Firebase RTDB | Driver GPS location push to customer during ride |
| Push notifications | Firebase Cloud Messaging (FCM) | Driver ride offers, customer status updates |
| SMS | MSG91 | OTP delivery (phone verification) |
| Payments | Razorpay | UPI order creation + webhook verification |
| Validation | Zod | Request body, params, query schemas for all 60 endpoints |
| Logging | Winston | Structured JSON logs with request ID correlation |
| Metrics | Prometheus client | Exposed at `/metrics` |
| Tracing | OpenTelemetry | Distributed tracing; trace context propagated through BullMQ |
| Tests | Jest + ts-jest | 321 passing tests |

### Android Customer App

| Layer | Technology | Purpose |
|---|---|---|
| Language | Kotlin | Primary language |
| UI | Jetpack Compose + Material 3 | Declarative UI |
| Architecture | MVVM + Repository + Clean layers | Separation of concerns |
| DI | Hilt + KSP | Compile-time dependency injection |
| Networking | Retrofit 2 + OkHttp 4 | REST API calls |
| Serialization | kotlinx.serialization | Compile-time safe JSON (replaces Gson) |
| Local DB | Room 2 | Offline cache (rides, notifications) |
| Auth | Firebase Auth (custom token flow) | Session management |
| Realtime | Firebase RTDB | Driver location + ride status during active ride |
| Push | Firebase Cloud Messaging | Incoming ride notifications to driver; status to customer |
| Maps | Google Maps Compose | Interactive map on Home and ActiveRide screens |
| Location | Play Services FusedLocationProviderClient | GPS for SOS, map centering |
| Preferences | DataStore (Preferences) | userId, profile state, pending rating |
| Feature flags | Firebase Remote Config | `enable_dynamic_surge`, `enable_wallet`, `enable_places_autocomplete` |
| Tests | JUnit 4 + MockK + Turbine + kotlinx-coroutines-test | 53 unit tests |
| minSdk | API 28 (Android 9 Pie) | |
| targetSdk | API 34 (Android 14) | |

---

## 4. Backend Architecture

### Request Lifecycle

Every request passes through a fixed middleware chain:

```
requestId
  → sanitizeBody          (strips __proto__, XSS, prototype pollution)
  → [rateLimiter]         (Redis sliding window, per-IP)
  → authenticate          (Firebase ID token verify + Redis 5-min cache)
  → [authorize(role)]     (CUSTOMER / DRIVER / ADMIN enum check)
  → validateBody/Params/Query (Zod schema, 400 on first failure)
  → [idempotency]         (Redis SHA-256 key, 24h TTL — required on ride creation)
  → controller            (thin HTTP adapter)
  → service               (all business logic lives here)
  → errorHandler          (ApiError → structured JSON; stack stripped in prod)
```

### Service Layer Design

Each service is a singleton class injected into controllers. Key services:

| Service | Responsibility |
|---|---|
| `authService` | OTP generate/verify, Firebase custom token mint, profile CRUD, OTP cleanup |
| `rideService` | Ride create/schedule, driver broadcast, lifecycle state transitions, share links |
| `driverService` | Online/offline toggle, location update, ride accept/decline/complete, earnings |
| `fareService` | Google Maps route call, Haversine fallback, three-tier config cache, surge calc |
| `paymentService` | Razorpay order create, signature verify, webhook handler |
| `notificationService` | FCM send, notification DB log, paginated read, mark-read |
| `sosService` | SOS trigger (GPS + emergency contacts via SMS), resolve |
| `adminService` | Driver KYC approve/reject/auto-verify, live ride query, config CRUD |
| `kycService` | Pluggable interface: `ManualKYCProvider` (default) or `SurepassKYCProvider` |

### Fare Calculation Flow

```
POST /rides/fare-estimate
  → enforceServiceArea()      Punjab bounding box check (SW: 29.50N/73.85E → NE: 32.60N/76.95E)
  → getRouteDetails()         Google Maps Directions API → distanceKm, durationMins, polyline
                              Circuit breaker: 3 failures/30s → fallback Haversine × 1.45 / 20 km/h
  → calculateSurgeMultiplier()
      Layer 1: time-of-day   (1.3× rush 8-10am, 1.5× evening 5-8pm, 1.3× late night)
      Layer 2: demand/supply (Redis, 60s TTL — activeRides/onlineDrivers in ±0.05° box, clamped 1.0-2.0)
      = max(time-based, demand-based)
  → applyPlatformConfig()    base_fare + (distanceKm × per_km_rate) + booking_fee
                              × surgeMultiplier, floor at minimum_fare
  → addGST()                 + gst_percentage% (config key, default 5%)
  → return estimate + minimumFare + minimumFareApplied flag
```

### Driver Broadcast (Dispatch) Flow

```
POST /rides  (or scheduled-ride-dispatch BullMQ job)
  → queryNearbyDrivers()    ST_DWithin PostGIS (GiST index) — online + VERIFIED drivers
  → Store candidates in Redis (ride:candidates:{rideId})
  → dispatchBatch()         FCM to top-5 simultaneously
  → scheduleRideOfferExpiry() BullMQ delayed job (30s TTL per batch)

  On ride-offer-expired job:
    → If ride status != REQUESTED: exit (already resolved)
    → Read next batch from Redis
    → If no more candidates: expandDriverSearch() (expanded radius, pass 2)
    → If still none: status = NO_DRIVER, FCM to customer, cleanup Redis
    → Else: dispatchBatch() next 5, schedule next expiry
```

### Cancellation Policy

| Condition | Fee Applied |
|---|---|
| Status is `REQUESTED` or `DRIVER_ASSIGNED` | No fee |
| Status is `DRIVER_ARRIVED` or later | `cancel_fee_arrived_amount` (default ₹40) |
| Reason code: `DRIVER_ASKED_TO_CANCEL`, `DRIVER_NOT_MOVING`, `DRIVER_WRONG_VEHICLE`, `DRIVER_BEHAVIOUR` | Fee waived regardless of status; driver gets cancellation count |
| Serial canceller threshold exceeded | Customer cooldown (Redis-backed, threshold from `serial_cancel_threshold` config) |

### Platform Config Cache (Three-Tier)

```
L1: In-process Map<string, {value, expiresAt}>  — zero latency
L2: Redis (60s TTL)                              — shared across instances
L3: PostgreSQL platform_config table             — source of truth

Cold path: L1 miss → L2 miss → DB read → populate L1 + L2
Hot path: L1 hit (sub-millisecond)
```

---

## 5. Database Schema

**Database:** PostgreSQL 16 + PostGIS 3.4
**ORM:** Prisma 5
**Migrations:** 10 applied (use `prisma migrate deploy`, never `migrate dev` — shadow DB fails with PostGIS on Windows)

### Models

#### User (core account, shared across roles)

| Field | Type | Notes |
|---|---|---|
| `id` | CUID | Primary key |
| `phone` | String (unique) | +91 format |
| `name` | String? | Nullable until profile complete |
| `email` | String? | Optional |
| `role` | Enum | `CUSTOMER`, `DRIVER`, `ADMIN` |
| `languagePref` | String | Default "pa" (Punjabi) |
| `isActive` | Boolean | False = 401 on all requests |
| `fcmToken` | String? | Firebase push token |
| Relations | — | `customerProfile`, `driverProfile`, rides, notifications, sosAlerts |

#### CustomerProfile

Emergency contact, saved home/work locations, `totalRides`, `cancellationCount`, `cancellationCooldownUntil`.

#### DriverProfile

KYC documents (license, RC, Aadhar URLs + numbers), `verificationStatus` (`PENDING/UNDER_REVIEW/VERIFIED/REJECTED`), plan type (`COMMISSION/SUBSCRIPTION`), rating stats, real-time location (`isOnline`, `currentLat/Lng`, `lastLocationUpdate`), bank details for withdrawals, accept/decline counters.

#### Ride (full lifecycle entity)

| Field Group | Fields |
|---|---|
| Participants | `customerId`, `driverId?` |
| Route | `pickupLat/Lng/Address`, `dropLat/Lng/Address`, `distanceKm?`, `durationMins?`, `polyline?` |
| Fare | `baseFare`, `surgeMultiplier`, `finalFare`, `commissionAmount`, `driverEarning`, `gstAmount` |
| Status | `RideStatus` enum (7 states) |
| Payment | `PaymentMethod` (CASH/UPI), `PaymentStatus`, `razorpayPaymentId?`, `razorpayOrderId?` |
| Scheduling | `isScheduled`, `scheduledAt?` |
| Safety | `rideStartOtp?` (4-digit, required to start ride) |
| Ratings | `customerRating?`, `driverRating?`, `ratingSkippedAt?` |
| Cancellation | `cancelledBy?`, `cancellationReasonCode?`, `cancellationReason?` |
| Timestamps | `requestedAt`, `driverAssignedAt?`, `driverArrivedAt?`, `startedAt?`, `completedAt?`, `cancelledAt?` |

#### Ride Status State Machine

```
SCHEDULED   ──(dispatch cron)──► REQUESTED
REQUESTED   ──(driver accepts)──► DRIVER_ASSIGNED
            ──(no drivers)──► NO_DRIVER
DRIVER_ASSIGNED ──(arrived)──► DRIVER_ARRIVED
DRIVER_ARRIVED  ──(OTP verified)──► IN_PROGRESS
IN_PROGRESS     ──(complete)──► COMPLETED
                ──(cancel)──► CANCELLED
Any state   ──(customer/driver cancel)──► CANCELLED
```

#### Other Models

| Model | Purpose |
|---|---|
| `RideEvent` | Immutable audit log of every state transition (rideId, eventType, metadata, GPS, timestamp) |
| `Earning` | Per-ride driver earnings record (gross, commission, net, T+2 settlement) |
| `Withdrawal` | Driver payout requests (UPI / bank transfer / cash agent) |
| `SOSAlert` | GPS-tagged SOS with alert recipients + status (`ACTIVE/RESOLVED/AUTO_RESOLVED`) |
| `OTPVerification` | Phone, hashed OTP, expiry, attempt count |
| `Notification` | Push notification log (type, title, body, data JSON, isRead) |
| `RideShareLink` | Time-limited public tracking token (hashed, revocable) |
| `PlatformConfig` | Key-value runtime config (13 seeded keys) |

### Platform Config Keys

| Key | Default | Controls |
|---|---|---|
| `base_fare` | — | Starting charge per ride |
| `per_km_rate` | — | Per-km fare |
| `booking_fee` | — | Flat booking charge |
| `minimum_fare` | — | Floor fare |
| `surge_multiplier` | 1.0 | Peak pricing multiplier (overridden by dynamic calc) |
| `gst_percentage` | 5 | GST on final fare (%) |
| `cancel_fee_arrived_amount` | 40 | Cancellation fee after driver arrived (₹) |
| `driver_search_radius_km` | — | Initial broadcast radius |
| `driver_search_radius_km_expanded` | 12 | Expanded radius if first pass fails |
| `ride_offer_batch_ttl_secs` | — | Acceptance window per driver batch |
| `serial_cancel_threshold` | — | Cancellations before cooldown |
| `max_schedule_days` | 7 | Max days ahead a ride can be scheduled |
| `phantom_driver_ttl_mins` | — | Stale online-driver cleanup threshold |

---

## 6. API Endpoint Reference

All endpoints are under `/api/v1`. Total: **60 endpoints** across 7 route groups.

### Auth — 8 endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/otp/send` | Public | Send 6-digit OTP via MSG91. Rate limited: 3/15 min |
| POST | `/auth/otp/verify` | Public | Verify OTP → Firebase custom token |
| POST | `/auth/register-driver` | Public | OTP verify + driver account creation in one call |
| GET | `/auth/profile` | Bearer | Get current user profile |
| PUT | `/auth/profile` | Bearer | Update profile (name, email, languagePref) |
| PUT | `/auth/emergency-contact` | Bearer | Set emergency contact |
| PUT | `/auth/saved-location` | Bearer | Save home or work location |
| PUT | `/auth/device-token` | Bearer | Register FCM token |

### Rides — 14 endpoints (CUSTOMER role)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/rides/fare-estimate` | Bearer | Fare + distance + duration estimate |
| POST | `/rides` | CUSTOMER + Idempotency-Key | Create on-demand ride |
| POST | `/rides/schedule` | CUSTOMER + Idempotency-Key | Create scheduled ride |
| GET | `/rides/history` | CUSTOMER | Paginated ride history (`page`, `limit`, `status`) |
| GET | `/rides/scheduled` | CUSTOMER | List upcoming scheduled rides |
| GET | `/rides/:rideId` | Bearer | Ride details (status, driver, OTP, fare) |
| GET | `/rides/:rideId/location` | Bearer | Driver lat/lng from RTDB |
| GET | `/rides/:rideId/receipt` | CUSTOMER | Fare breakdown for completed ride |
| POST | `/rides/:rideId/cancel` | CUSTOMER | Cancel with structured reason code |
| POST | `/rides/:rideId/share` | CUSTOMER | Generate public tracking link |
| POST | `/rides/:rideId/rate` | CUSTOMER | Rate driver (1–5) |
| POST | `/rides/:rideId/skip-rating` | CUSTOMER | Mark rating permanently skipped |
| POST | `/rides/:rideId/sos` | Bearer | Trigger SOS (real GPS via FusedLocationProviderClient) |
| POST | `/rides/sos/:sosAlertId/resolve` | Bearer | Resolve active SOS |

### Driver — 19 endpoints (DRIVER role)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/driver/documents` | DRIVER | Submit KYC documents |
| POST | `/driver/go-online` | DRIVER | Go online with GPS |
| POST | `/driver/go-offline` | DRIVER | Go offline |
| GET | `/driver/status` | DRIVER | Online state + active ride |
| POST | `/driver/location` | DRIVER | Update GPS (periodic) |
| GET | `/driver/rides/incoming` | DRIVER | Poll for pending ride offer |
| POST | `/driver/rides/:rideId/accept` | DRIVER | Accept ride offer |
| POST | `/driver/rides/:rideId/decline` | DRIVER | Decline ride offer |
| POST | `/driver/rides/:rideId/arrived` | DRIVER | Mark arrived at pickup |
| POST | `/driver/rides/:rideId/start` | DRIVER | Start ride (requires customer OTP) |
| POST | `/driver/rides/:rideId/complete` | DRIVER | Complete ride |
| POST | `/driver/rides/:rideId/cancel` | DRIVER | Cancel active ride |
| POST | `/driver/rides/:rideId/rate-customer` | DRIVER | Rate customer (1–5) |
| GET | `/driver/trips` | DRIVER | Paginated trip history |
| GET | `/driver/earnings` | DRIVER | Earnings list (`today`/`week`/`month`) |
| GET | `/driver/earnings/summary` | DRIVER | Aggregated totals (`week`/`month`) |
| GET | `/driver/earnings/settlement` | DRIVER | Settled vs unsettled balance |
| POST | `/driver/withdrawals` | DRIVER | Request payout |
| GET | `/driver/withdrawals/:withdrawalId` | DRIVER | Withdrawal status |

### Payments — 3 endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/payments/order` | Bearer | Create Razorpay order |
| POST | `/payments/verify` | Bearer | Verify Razorpay payment signature |
| POST | `/payments/webhook` | HMAC-SHA256 signature | Razorpay webhook (raw body) |

### Notifications — 4 endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/notifications` | Bearer | Paginated list |
| GET | `/notifications/unread-count` | Bearer | Unread count |
| PATCH | `/notifications/:notificationId/read` | Bearer | Mark one read |
| PATCH | `/notifications/read-all` | Bearer | Mark all read |

### Admin — 10 endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/admin/promote` | `x-internal-api-key` header | Promote user to ADMIN |
| GET | `/admin/drivers/pending` | ADMIN | Drivers awaiting KYC |
| GET | `/admin/drivers/:driverId` | ADMIN | Driver detail + docs |
| POST | `/admin/drivers/:driverId/approve` | ADMIN | Approve KYC |
| POST | `/admin/drivers/:driverId/reject` | ADMIN | Reject KYC with reason |
| POST | `/admin/drivers/:driverId/auto-verify` | ADMIN | Trigger KYC provider |
| GET | `/admin/rides/live` | ADMIN | All active rides |
| GET | `/admin/rides` | ADMIN | Filtered ride list (status, paymentStatus, pagination) |
| GET | `/admin/config` | ADMIN | All 13 platform config values |
| PUT | `/admin/config/:key` | ADMIN | Update a config value |

### Track — 1 endpoint

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/track/:token` | None | Public ride tracking via share token |

---

## 7. Background Job System

Two BullMQ queues are initialized at server startup and closed on graceful shutdown.

### `chalo-maintenance` queue

| Job | Schedule | Action |
|---|---|---|
| `otp-cleanup` | Every 24h + once on startup | Deletes expired `OTPVerification` records |
| `scheduled-ride-dispatch` | Every 60s | Finds SCHEDULED rides due within dispatch window → transitions to REQUESTED → triggers driver broadcast |
| `driver-offline-check` | Every 2 min | Finds online drivers with no location ping in `DRIVER_OFFLINE_TIMEOUT_MINS` → marks offline + syncs RTDB |

### `chalo-rides` queue

| Job | Trigger | Action |
|---|---|---|
| `ride-offer-expired` | Delayed (BROADCAST_BATCH_TTL_MS after each batch dispatch) | If ride still REQUESTED: advance to next driver batch. If all batches exhausted: try expanded radius. If still none: mark `NO_DRIVER`, FCM customer, cleanup Redis |

### OpenTelemetry trace propagation

Trace context is serialized into BullMQ job payloads (`traceContext` field) and extracted in the worker before processing, so job execution is linked to the originating HTTP request in distributed traces.

---

## 8. Android Customer App Architecture

### Layer Separation

```
Presentation (Compose + HiltViewModel + StateFlow)
    ↕ domain events and UI state
Domain (pure Kotlin interfaces + models — zero Android dependencies)
    ↕ repository calls
Data (Retrofit, Room, DataStore, Firebase SDK implementations)
```

### Dependency Injection (Hilt)

| Module | Provides |
|---|---|
| `NetworkModule` | `OkHttpClient` (30s timeouts, `AuthInterceptor`, logging interceptor), `Retrofit`, 4 API service interfaces |
| `DatabaseModule` | `AppDatabase` (Room), `RideDao`, `NotificationDao` |
| `RepositoryModule` | Binds all repository interfaces to their implementations |

`FeatureFlagRepositoryImpl` is injected into `ChaloApplication` at startup and reads Firebase Remote Config with a 1h TTL (60s in debug).

### Network Layer

**AuthInterceptor behaviour:**
1. Calls `FirebaseAuth.getInstance().currentUser?.getIdToken(false)` (use cached token).
2. Injects `Authorization: Bearer <token>`.
3. On 401 response: calls `getIdToken(forceRefresh = true)` and retries once.
4. If no Firebase user: no header attached (unauthenticated pass-through).

**Serialization:** kotlinx.serialization (compile-time codegen; no reflection; unaffected by R8 minification). `ignoreUnknownKeys = true`, `coerceInputValues = true`.

### Local Persistence

**Room (AppDatabase v1):**

| Entity | Fields | Purpose |
|---|---|---|
| `RideEntity` | rideId, status, pickup/drop addresses, fare | Offline ride cache |
| `NotificationEntity` | notifId, type, title, body, isRead, sentAt | Offline notification cache |

Migration registry in `DatabaseMigrations.kt` — `fallbackToDestructiveMigration` removed; `exportSchema = true`.

**DataStore (UserPreferences):**

| Key | Type | Purpose |
|---|---|---|
| `userId` | String | Logged-in user ID |
| `userName` | String | Display name |
| `userPhone` | String | Phone number |
| `profileComplete` | Boolean | Profile setup done |
| `pendingRatingRideId` | String | Ride awaiting post-trip rating |
| `pendingRatingShown` | Boolean | Whether rating prompt was shown |
| `pendingRatingTime` | Long | Timestamp for staleness check |

### Build Variants

| Field | Debug | Release |
|---|---|---|
| `BASE_URL` | `DEV_BASE_URL` from `local.properties` | `https://api.chalo.in/api/v1` |
| `FIREBASE_DATABASE_URL` | `https://chalo-dev-default-rtdb.asia-southeast1.firebasedatabase.app` | same |
| `MAPS_API_KEY` | From `local.properties` | From `local.properties` |
| `isMinifyEnabled` | false | true |
| `isShrinkResources` | false | true |
| `isDebuggable` | true | false |
| Logging | OkHttp BODY level | NONE |

---

## 9. Screen Inventory and Navigation

### Navigation Graph

Single-activity app. `MainActivity` hosts a `NavHost`. Navigation is driven by `Routes.kt` sealed object constants.

```
SplashScreen
  ├── (no session) ──► PhoneInputScreen
  │                       └──► OtpVerifyScreen
  │                               ├── (new user) ──► CompleteProfileScreen
  │                               └── (returning) ──► HomeScreen
  └── (has session, profile complete) ──► HomeScreen

HomeScreen
  ├──► FareEstimateScreen ──► ActiveRideScreen
  ├──► RideHistoryScreen ──► RideDetailScreen
  ├──► ProfileScreen
  │       ├──► EmergencyContactScreen
  │       └──► SavedLocationsScreen
  ├──► NotificationsScreen
  └──► ScheduleRideScreen ──► ScheduledListScreen

ActiveRideScreen
  └── (ride complete) ──► PaymentScreen (UPI) or RatingScreen (Cash)
                              └──► RatingScreen ──► ReceiptScreen
```

### Screen Details

| Screen | ViewModel | Key Behaviour |
|---|---|---|
| `SplashScreen` | `SplashViewModel` | Checks `FirebaseAuth.currentUser`; navigates without flash |
| `PhoneInputScreen` | `PhoneInputViewModel` | POST /auth/otp/send; +91 format enforcement |
| `OtpVerifyScreen` | `OtpVerifyViewModel` | 6-box OTP; SMS Retriever auto-fill; auto-verify on 6th digit; resend countdown |
| `CompleteProfileScreen` | `CompleteProfileViewModel` | PUT /auth/profile; blocks navigation until name set |
| `HomeScreen` | `HomeViewModel` | Google Maps Compose; Places Autocomplete with Punjab bias + 300ms debounce; active ride check on resume |
| `FareEstimateScreen` | `FareEstimateViewModel` | POST /rides/fare-estimate; shows breakdown; leads to ride creation |
| `ActiveRideScreen` | `ActiveRideViewModel` | Status polling; driver GPS from RTDB with animated marker (Animatable, 800ms tween); route polyline; ETA pill; SOS dialog (FusedLocationProviderClient for real GPS) |
| `PaymentScreen` | `PaymentViewModel` | UPI → Razorpay SDK; Cash → skip to RatingScreen |
| `RatingScreen` | `RatingViewModel` | POST /rides/:id/rate; POST /rides/:id/skip-rating |
| `ReceiptScreen` | `ReceiptViewModel` | GET /rides/:id/receipt; shows fare breakdown |
| `RideHistoryScreen` | `RideHistoryViewModel` | GET /rides/history paginated |
| `RideDetailScreen` | `RideDetailViewModel` | GET /rides/:id; shows full ride summary |
| `ProfileScreen` | `ProfileViewModel` | GET + PUT /auth/profile |
| `EmergencyContactScreen` | `EmergencyContactViewModel` | PUT /auth/emergency-contact |
| `SavedLocationsScreen` | `SavedLocationsViewModel` | PUT /auth/saved-location (home/work) |
| `NotificationsScreen` | `NotificationsViewModel` | GET /notifications; PATCH read; unread badge |
| `ScheduleRideScreen` | `ScheduleRideViewModel` | POST /rides/schedule; `scheduledAt` sent as ISO 8601 UTC |
| `ScheduledListScreen` | `ScheduledListViewModel` | GET /rides/scheduled; cancel with reason code |

### Shared Components (`ChaloComponents.kt`)

Reusable composables including loading spinners, error banners, empty state placeholders, and ride status cards.

---

## 10. Firebase Integration

### Services Used

| Service | How Used |
|---|---|
| Firebase Auth | Custom token flow: backend mints token, app signs in with `signInWithCustomToken`. SDK manages ID token lifecycle (1h expiry, auto-refresh). |
| Firebase RTDB | Driver GPS location + ride status mirrored in real time. Paths: `/rides/{rideId}/status`, `/rides/{rideId}/driver_location`, `/drivers/{driverId}` |
| Firebase Cloud Messaging | Driver receives ride offers; customer receives status updates (driver assigned, arrived, completed, no driver found) |
| Firebase Crashlytics | Crash reporting in release builds |
| Firebase Remote Config | Feature flags: `enable_dynamic_surge`, `enable_wallet`, `enable_places_autocomplete` |

### Project Config

- Project ID: `chalo-dev`
- RTDB URL: `https://chalo-dev-default-rtdb.asia-southeast1.firebasedatabase.app`
- Service account path: `./firebase-service-account.json` (backend, in `.gitignore`)
- `google-services.json`: present at `chalo-customer-app/app/google-services.json`
- Notification channel: `chalo_rides` (default; all notification types share this channel in V1)

### RTDB Data Shape

```
/drivers/{driverId}
  isOnline: boolean
  lat: number
  lng: number
  updatedAt: string

/rides/{rideId}
  status: RideStatus
  driver_location:
    lat: number
    lng: number
    updatedAt: string
```

---

## 11. Security Posture

### Backend Security (Strong)

| Area | Status |
|---|---|
| Middleware (Helmet, CORS, HPP, sanitize) | Strong — all enabled in correct order |
| Firebase ID token verification (cryptographic) | Strong — Redis-cached 5 min |
| OTP (6-digit, SHA-256 hashed, rate-limited) | Good |
| Input validation (Zod, all 60 endpoints) | Strong |
| Idempotency on ride creation | Implemented — Redis SHA-256, 24h TTL |
| Razorpay webhook HMAC-SHA256 | Correct — raw body preserved |
| Rate limiting (Redis-backed, 3 tiers) | Good |
| SQL injection | Not possible — Prisma parameterized queries |

### Open Issues (Before Production)

| Priority | Issue | Fix |
|---|---|---|
| Critical | PII stored in plaintext (Aadhar, bank account, license) | pgcrypto column encryption |
| High | RTDB listeners die when app is backgrounded on Android 12+ | ForegroundService (type: location) |
| High | Payment order creation — verify ride ownership | `customerId === req.user.uid` check |
| High | `INTERNAL_API_KEY` not in startup guard | Add to server secret validation |
| Medium | No rate limit on `/auth/otp/verify` | Dedicated limiter (10/15 min) |
| Medium | Address fields have no max length cap | `.max(500)` in Zod validators |
| Medium | RTDB security rules not in source control | Document + enforce owner-only read rules |
| Low | Room DB unencrypted (readable on rooted devices) | SQLCipher integration |
| Low | No `FLAG_SECURE` on PaymentScreen, ReceiptScreen | `window.setFlags(FLAG_SECURE, FLAG_SECURE)` |
| Low | `network_security_config.xml` uses subnet addresses | Use actual host IP per dev machine |

### Resolved Security Issues

- SOS previously sent `(0, 0)` coordinates — fixed: `FusedLocationProviderClient.lastLocation.await()` in `ActiveRideViewModel`
- OTP was 4 digits — fixed: upgraded to 6 digits across backend + Android + all tests
- No SMS gateway — fixed: MSG91 integrated in `sms.service.ts`
- No ProGuard rules for DTOs — fixed: `@Keep` rules added to `proguard-rules.pro`
- Gson reflection breaking R8 — fixed: migrated all DTOs to kotlinx.serialization

---

## 12. Performance Architecture

### Backend Performance

| Area | Implementation |
|---|---|
| Platform config reads | Three-tier cache (in-process L1 → Redis L2 → DB L3), < 1ms hot path |
| Driver search | `ST_DWithin` PostGIS with GiST spatial index (not B-tree bounding box) |
| Auth token verify | Redis-cached 5 min — no Firebase network call on repeat requests |
| Fare estimate caching | Config cached; Maps API call is the bottleneck (circuit breaker + Haversine fallback) |
| BullMQ rides worker | Concurrency = 5 (parallelizes ride-offer-expired jobs) |
| Google Maps fallback | Haversine × 1.45 road factor ÷ 20 km/h (Punjab urban estimate) |
| Prometheus metrics | Exposed at `/metrics` for scraping |

### Android Performance

| Area | Implementation |
|---|---|
| Driver marker animation | `Animatable` (lat + lng) with `tween(800ms)` — smooth interpolation between RTDB updates |
| Places Autocomplete | 300ms debounce before API call; popular places fallback |
| Image caching | Coil `ImageLoader` configured (caching policy defaults) |
| DI startup cost | Hilt uses KSP compile-time codegen — minimal runtime overhead |

### Known Performance Gaps

- RTDB listeners die in background on Android 12+ (missing ForegroundService)
- Driver marker updates trigger full `ActiveRideScreen` recomposition — should use `derivedStateOf` / `MarkerState`
- `NetworkModule` creates `Retrofit` eagerly at app start — can be lazily initialized

---

## 13. CI/CD Pipeline

**Trigger:** Every push and pull request to `main`

### Job Graph

```
lint (no DB needed)  ─┐
                       ├──► build (after both pass)
test (live DB+Redis) ─┘

android  (parallel with above, no dependency)
```

### Job 1: Lint & Type Check

- TypeScript type check (`tsc --noEmit`)
- ESLint
- `npm audit` (fails on high/critical vulnerabilities)

### Job 2: Test

Services: `postgis/postgis:16-3.4-alpine` + `redis:7-alpine`

Steps: install → `prisma generate` → `prisma migrate deploy` → `jest --coverage --forceExit` → Codecov upload

### Job 3: Build

Runs after lint + test pass. TypeScript compile → verify `dist/server.js` exists.

### Job 4: Android

Parallel with backend jobs. Steps:

- Create `local.properties` with placeholder values (no emulator in CI)
- `gradle :app:lintDebug`
- `gradle test` (JVM unit tests, 53 passing)
- `gradle assembleDebug` → verify `app-debug.apk` exists
- Upload test results + lint reports as artifacts (7-day retention)

**Note:** Gradle wrapper (`gradlew`) is not committed — CI uses `gradle -p chalo-customer-app` with Gradle 8.4 configured via `gradle/actions/setup-gradle@v4`.

---

## 14. Local Development Setup

### Prerequisites

- Docker Desktop (starts `chalo-postgres` and `chalo-redis` containers automatically)
- Node.js 20+
- Android Studio (for Android development)
- JDK 17

### Backend Daily Workflow

```bash
# Docker Desktop must be open — postgres (port 5433) and redis (port 6379) auto-start

cd chalo-backend

# First-time only
DATABASE_URL="postgresql://postgres:luffy@localhost:5433/chalo_db?schema=public" npx prisma migrate deploy
npm run db:seed   # seeds 13 platform_config keys

# Daily dev (hot reload via ts-node-dev)
npm run dev       # http://localhost:3001

# Health check
curl http://localhost:3001/health

# Run all tests (321)
npm test

# Type check only
npx tsc --noEmit
```

**Important gotchas:**
- Always use port **5433** for host-side connections (port 5432 is internal Docker network)
- Use `prisma migrate deploy`, never `prisma migrate dev` (shadow DB fails with PostGIS on Windows)
- Migration SQL uses `CAST(x AS geography)`, not `::geography` (Prisma SQL parser issue on Windows)

### Android Daily Workflow

1. Create `chalo-customer-app/local.properties`:
   ```
   sdk.dir=C:/Users/<you>/AppData/Local/Android/Sdk
   DEV_BASE_URL=http://10.0.2.2:3001/api/v1
   MAPS_API_KEY=<your_google_maps_key>
   ```
2. Open `chalo-customer-app/` in Android Studio
3. Sync Gradle
4. Run on emulator (must be "Google APIs" image, not plain AOSP — required for Maps + Firebase)

```bash
# JVM unit tests only (no emulator)
gradle -p chalo-customer-app test
```

### Infrastructure Containers

| Container | External Port | Internal Port | Purpose |
|---|---|---|---|
| `chalo-postgres` | 5433 | 5432 | PostgreSQL 16 + PostGIS 3.4 |
| `chalo-redis` | 6379 | 6379 | Redis 7 |
| `chalo-api` | 5000 | 5000 | Dockerised API (not used in local dev) |

### Environment Variables (Backend)

| Variable | Required | Description |
|---|---|---|
| `DATABASE_URL` | Yes | PostgreSQL connection string |
| `REDIS_URL` | Yes | Redis connection string |
| `JWT_SECRET` | Yes | Signing secret |
| `FIREBASE_SERVICE_ACCOUNT_PATH` | Yes | Path to service account JSON |
| `RAZORPAY_KEY_ID` | Yes (prod) | Razorpay public key |
| `RAZORPAY_KEY_SECRET` | Yes (prod) | Razorpay secret |
| `RAZORPAY_WEBHOOK_SECRET` | Yes (prod) | Webhook HMAC secret |
| `INTERNAL_API_KEY` | Yes | Must not be default value in prod |
| `GOOGLE_MAPS_API_KEY` | Optional | Without it, Haversine fallback is used |
| `MSG91_AUTH_KEY` | Optional | Without it, OTP prints to console |
| `SUREPASS_API_KEY` | Optional | Without it, ManualKYCProvider is used |

---

## 15. Testing Coverage

### Backend — 321 Passing Tests

| Category | What is covered |
|---|---|
| Integration (`api.integration.test.ts`) | Full HTTP request → Express → service → DB → response cycle |
| Service: auth | OTP generate/verify, profile CRUD, cleanup |
| Service: ride | Lifecycle state transitions, broadcast dispatch, fare calc, cancellation policy |
| Service: driver | Online/offline, location, accept/decline, earnings |
| Service: fare | Config cache, Google Maps, Haversine fallback, surge calculation |
| Service: notification | FCM send, paginated read, mark-read |
| Service: sos | Trigger with GPS, resolve |
| Service: sms | MSG91 integration |
| Validators | auth, ride, driver, payment — Zod edge cases, boundary values, invalid inputs |
| Utils | constants, helpers, apiError |

Run: `cd chalo-backend && npm test`

### Android — 53 Passing JVM Unit Tests

| Test class | What is covered |
|---|---|
| `OtpVerifyViewModelTest` | Auto-submit on 6th digit, Firebase sign-in call, error state |
| `AuthRepositoryImplTest` | DTO → domain model mapping, null handling |
| `RideRepositoryImplTest` | Ride DTO mapping, cancel/create request construction |
| `FareEstimateViewModelTest` | Estimate request build, success/error state |
| `ActiveRideViewModelTest` | Status polling, completed/cancelled navigation events |
| `UserPreferencesTest` | DataStore save/clear/pending-rating flow |

Run: `gradle -p chalo-customer-app test`

### Missing Coverage (High Priority)

| Gap | Type | Impact |
|---|---|---|
| Android instrumentation tests | `src/androidTest/` | UI/navigation regressions can ship undetected |
| DTO contract tests (Retrofit JSON parse tests) | JVM unit | Backend field rename → silent null in app |
| Compose UI tests (OTP flow, book ride flow, post-ride flow) | Instrumentation | End-to-end user journey validation |

---

## 16. Known Gaps and Pending Work

### Pre-Launch Blockers (Must Fix)

| Gap | Impact | Where |
|---|---|---|
| No release signing config | Cannot publish to Play Store | `build.gradle.kts` |
| No Android instrumentation tests | UI regressions ship silently | `src/androidTest/` |
| DTO contract not enforced | Backend rename → silent null in app | All DTOs |
| RTDB listeners die in background (Android 12+) | Customer loses live tracking | `ActiveRideViewModel` |
| PII in plaintext (Aadhar, bank, license) | DPDP Act 2023 compliance | `driver_profiles` DB table |
| Payment order ownership not verified | Customer can create order for another's ride | `payment.service.ts` |
| `INTERNAL_API_KEY` not in startup guard | Default key in prod = admin promotion open | `server.ts` |

### Android App Completion

- `ActiveRideScreen` — validate full polling, OTP display, SOS behaviour end-to-end
- `ScheduleRideScreen` — `scheduledAt` must be ISO 8601 UTC (not local time)
- `PaymentScreen` — Razorpay SDK integration for UPI; CASH skips to receipt
- `ScheduledListScreen` — cancel dialog + reason code picker
- Rating prompt — delay 30s or require manual dismiss after CASH ride completion
- Notification deep-link — tapping a notification navigates to the relevant ride screen

### Near-Term Improvements (Post-Launch)

- Hindi localization (`res/values-hi/strings.xml` + `languagePref = "hi"`)
- Multiple notification channels (ride_updates / payments / safety / promotions)
- Offline / network error recovery on `HomeScreen` (ConnectivityManager callback)
- In-app masked phone number proxy (Twilio or plain phone intent in V1)
- Analytics taxonomy (Firebase Analytics or Mixpanel — funnel from `fare_estimate_viewed` to `ride_completed`)
- OpenAPI spec generation from Zod schemas (`zod-to-openapi`)
- Automated driver settlement via Razorpay Route (when driver count > 50)
- In-app wallet (V2 — Prisma migration required)
- Promo / referral engine (V2)

---

## 17. Design Decisions and Principles

### Why Firebase custom token flow (not Firebase Phone Auth)?

Firebase Phone Auth locks OTP delivery to Firebase. The custom token flow lets the backend control OTP generation and delivery (MSG91 for reliability, cost control, language support). The backend mints a custom token after verifying the OTP, and the app signs in normally. Firebase SDK then manages ID token lifecycle identically.

### Why PostgreSQL + PostGIS instead of a geo-native DB?

PostgreSQL is the safe default for a transactional system. PostGIS adds full geospatial capability (ST_DWithin, GiST indexes) without sacrificing ACID guarantees. Driver search uses `$queryRaw` with `ST_DWithin` for accurate spherical distance — not an application-layer bounding box.

### Why BullMQ instead of cron or setInterval?

`setInterval` does not survive server restarts and breaks under horizontal scaling. BullMQ persists jobs in Redis, deduplicates repeating jobs by name (safe to re-register on startup), and supports delayed one-shot jobs (ride-offer-expired). It also integrates with OpenTelemetry for tracing across async boundaries.

### Why a three-tier config cache?

Platform config (fares, fees, thresholds) is read on every ride request. Hitting the DB every time would add ~5ms per request. The L1 in-process cache makes config reads sub-millisecond. Redis L2 provides consistency across instances within 60 seconds. DB L3 is the source of truth for admin updates.

### Why pluggable KYC provider?

KYC API availability and cost depend on scale and region. The `IKYCProvider` interface lets the system run with `ManualKYCProvider` during early operations (admin reviews docs manually) and switch to `SurepassKYCProvider` by setting one env variable — no code change, no migration.

### Why kotlinx.serialization instead of Gson?

Gson uses reflection for JSON mapping. R8 minification renames Kotlin property names in release builds, silently breaking deserialization. kotlinx.serialization generates code at compile time via KSP — minification-safe, faster, and type-safe. All 5 DTO files were migrated from `@SerializedName` to `@SerialName`.

### Why Hilt for DI?

Hilt is the Android-recommended DI solution. It uses KSP for compile-time code generation (not reflection), integrates with Jetpack ViewModel lifecycle, and produces compile-time errors on misconfigured injection — catching bugs before runtime.

### Principle: Platform config is the single source of truth for business rules

Fare rates, fee amounts, and thresholds are never hardcoded in the Android app or in service constants. They are seeded in `platform_config` and fetched via the three-tier cache. Changing a business rule requires no code deployment — only an admin panel PUT call.

### Principle: Every state transition is audit-logged

`RideEvent` records every lifecycle change with timestamp, GPS, and metadata JSON. This creates an immutable audit trail for dispute resolution, driver penalisation review, and operational debugging — without touching the `Ride` table for history queries.

---

## Quick Reference

| Action | Command |
|---|---|
| Start backend dev server | `cd chalo-backend && npm run dev` |
| Run backend tests | `cd chalo-backend && npm test` |
| Apply DB migrations | `DATABASE_URL="postgresql://postgres:luffy@localhost:5433/chalo_db?schema=public" npx prisma migrate deploy` |
| Seed platform config | `cd chalo-backend && npm run db:seed` |
| Type check backend | `npx tsc --noEmit` |
| Run Android unit tests | `gradle -p chalo-customer-app test` |
| Build debug APK | `gradle -p chalo-customer-app assembleDebug` |
| Backend health check | `curl http://localhost:3001/health` |
| Promote user to admin | `POST /api/v1/admin/promote` with `x-internal-api-key` header |

---

*For the authoritative implementation truth (endpoint shapes, DTO fields, migration list), see [docs/CODEBASE.md](CODEBASE.md). For pending work with priority, see [docs/development/NEXT_STEPS.md](development/NEXT_STEPS.md). For security findings and fixes, see [docs/reviews/SECURITY_PERFORMANCE_REVIEW.md](reviews/SECURITY_PERFORMANCE_REVIEW.md).*
