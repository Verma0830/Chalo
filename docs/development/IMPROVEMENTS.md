# Chalo — Improvements Backlog

Last updated: 2026-03-22
Source: derived from reading all backend services, Android source, schema, validators, and CI configuration.

Items are grouped by theme and ordered within each group by impact. Do not add items here that are already tracked in NEXT_STEPS.md (those are active in-sprint work). This file is for planned improvements beyond the immediate sprint.

---

## Group 1 — Critical Bugs — RESOLVED

All Group 1 bugs have been fixed:

| Bug | File | Status |
|---|---|---|
| SOS sent (0.0, 0.0) | `ActiveRideViewModel.kt` | Fixed — uses `FusedLocationProviderClient.lastLocation.await()`, falls back to ride pickup coords |
| `isMyLocationEnabled` without permission check | `HomeScreen.kt` | Fixed — uses `rememberPermissionState(ACCESS_FINE_LOCATION)`, passes `locationGranted` to `MapProperties` |
| No SMS gateway for OTP delivery | `sms.service.ts` | Fixed — `sendOTPSms()` calls MSG91 API. Set `MSG91_AUTH_KEY` env variable to enable. |
| OTP was 4 digits (brute-force risk + SMS Retriever mismatch) | `constants.ts`, `auth.validator.ts`, `OtpVerifyScreen.kt`, `OtpVerifyViewModel.kt` | Fixed — upgraded to 6 digits across backend + Android + all tests |

---

## Group 2 — Android Product Gaps — RESOLVED

All Group 2 items have been implemented:

| Feature | File | Status |
|---|---|---|
| Google Places Autocomplete | `HomeScreen.kt` → `DestinationPickerSheet` | Done — `Places.createClient()`, `FindAutocompletePredictionsRequest` with Punjab bounding bias, 300ms debounce, popular places fallback |
| Route polyline on active ride map | `ActiveRideScreen.kt` | Done — `PolyUtil.decode(routePolyline)` + `Polyline` composable, blue (#1A73E8) 8dp line |
| Driver marker animation | `ActiveRideScreen.kt` | Done — `Animatable` (lat + lng) with `tween(800ms)`, smooth interpolation between RTDB updates |
| ETA display | `ActiveRideViewModel.kt` + `ActiveRideScreen.kt` | Done — Haversine × 1.3 road factor ÷ 20 km/h, shown as "ETA: X min" pill on status card |
| SMS Retriever for OTP auto-fill | `OtpVerifyScreen.kt` + `OtpVerifyViewModel.kt` | Done — `SmsRetriever.getClient().startSmsRetriever()`, `BroadcastReceiver` on `SMS_RETRIEVED_ACTION`, auto-fills and submits |
| Foreground service for background tracking | Pending — see Group 4 below | Not yet implemented |

---

## Group 3 — Backend Product Gaps

### 3.1 Dynamic surge pricing — IMPLEMENTED
`fare.service.ts` → `calculateSurgeMultiplier()` now runs a two-layer algorithm:
- **Layer 1 (always active):** Time-of-day baseline (1.3× morning rush 8–10am, 1.5× evening 5–8pm, 1.3× late night)
- **Layer 2 (demand/supply, Redis-backed, 60s TTL):** Counts online drivers vs active ride requests in a ±0.05° bounding box (~5.5km) around the pickup. `surge = clamp(activeRides/onlineDrivers, 1.0, 2.0)` rounded to nearest 0.1. Higher of time-based or demand-based is used.
- Falls back to time-based surge if Redis unavailable.

### 3.2 Google Maps Directions API for accurate fares — IMPLEMENTED
`fare.service.ts` → `getRouteDetails()` calls Google Maps Directions API and returns `distanceKm`, `durationMins`, and encoded `routePolyline`. Circuit breaker falls back to Haversine × 1.3 road factor at 25 km/h when Maps API is unavailable.

Set `GOOGLE_MAPS_API_KEY` in `.env` to enable. Without it, Haversine fallback is used for all requests.

### 3.3 In-app wallet and refund mechanism — PENDING
No wallet schema yet. Requires a Prisma migration + new endpoints. Planned for V2 after launch validation.

**Schema when ready:** `Wallet`, `WalletTransaction` models. `PaymentMethod` enum: add `WALLET`.

### 3.4 Geofencing and service area enforcement — IMPLEMENTED
`ride.service.ts` → `enforceServiceArea()` checks pickup coordinates against Punjab state bounding box:
- SW corner: (29.50°N, 73.85°E)
- NE corner: (32.60°N, 76.95°E)
- Returns HTTP 400 with "outside service area" message if pickup is out of bounds
- Called before fare calculation in both `createRide()` and (extend to) `createScheduledRide()` — prevents dispatch to drivers outside Punjab

### 3.5 Promo code and referral engine — PENDING
No schema yet. Planned for V2.

### 3.6 Automated driver settlement via Razorpay Route — PENDING
Manual withdrawal path remains. Razorpay Route requires additional KYC on driver accounts (Razorpay linked accounts). Planned for when driver count exceeds 50.

---

## Group 4 — Android UX Layer

### 4.1 In-app call with masked number
Customers and drivers need to contact each other for pickup coordination. Exposing real phone numbers is a privacy issue. Ola and Uber use DTMF proxy numbers (a masked intermediary number).

**Minimal alternative:** Firebase Dynamic Links + a deep link to a phone call intent where both parties call a temporary Twilio proxy number. This requires a Twilio account (~₹1 per call minute).

**Even simpler V1:** Show the driver's phone number in the app and allow the customer to initiate a regular call. Not masked, but functional. Real masking is a V2 feature.

### 4.2 Multiple notification channels
All notifications go to the `chalo_rides` channel. Users cannot mute promos without muting ride updates.

**What to build:** Define channels in `MyFirebaseMessagingService.onCreate()`:
- `ride_updates` (high importance) — driver assigned, arrived, ride started, completed
- `payments` (high importance) — payment confirmed, receipt
- `safety` (max importance) — SOS confirmation
- `promotions` (low importance) — promo codes, referral rewards

Send the appropriate `channelId` in FCM data payload from `notification.service.ts`.

### 4.3 Offline / network error recovery on HomeScreen
If the network is unavailable at app open, `HomeScreen` shows a blank map with no error state. There is no retry mechanism and no cached state check.

**What to build:**
1. Use `ConnectivityManager.NetworkCallback` in `HomeViewModel` to observe connectivity.
2. Show an inline error banner "No internet connection" when offline.
3. On reconnect, automatically reload active ride check and driver availability.
4. Cache the last known active ride ID in DataStore so a returning user sees their in-progress ride immediately.

### 4.4 Hindi localization
The app is targeted at Punjab. The majority of users are Punjabi or Hindi speakers.

**What to build:**
1. Add `res/values-hi/strings.xml` with Hindi translations of all UI strings.
2. Use `LocaleList` in the `Activity` to respect the user's `languagePref` from their profile.
3. The backend already has `languagePref` enum (`pa`, `en`) — add `hi` to this enum.

**Impact:** Significant improvement in user comfort for ~80% of the target market.

### 4.5 Rating prompt timing
Currently, when a ride is COMPLETED with CASH payment, `ActiveRideViewModel` emits a `RideCompleted` event immediately and the app navigates to the rating screen.

**Issue:** The rating screen appears before the customer has stepped out of the vehicle. The customer is still settling the fare in cash.

**Fix:** Delay the `RideCompleted` event by 30 seconds, or wait for the customer to manually dismiss a "Your ride is complete — tap to rate" card on the `ActiveRideScreen` instead of auto-navigating.

---

## Group 5 — Backend Architecture

### 5.1 PostGIS driver search — IMPLEMENTED
`ride.service.ts` already uses `ST_DWithin` + `ST_Distance` in `queryNearbyDrivers()` with geography casts, and ranks candidates by computed distance.

### 5.2 OpenTelemetry distributed tracing — IMPLEMENTED (baseline)
Implemented in backend:
1. Added OpenTelemetry SDK and auto-instrumentation dependencies.
2. Added telemetry bootstrap/shutdown in server lifecycle (`src/telemetry/index.ts`, `server.ts`).
3. Propagated trace carrier through BullMQ payload (`traceContext`) and extracted context in worker before processing.

Remaining hardening (optional): route spans to Jaeger/Tempo in deployment infra and add dashboards/alerts around trace sampling and exporter health.

### 5.3 OTP cleanup scheduling — IMPLEMENTED
`authService.cleanupExpiredOTPs()` is now executed via the BullMQ maintenance queue (`otp-cleanup` repeating job + startup run).

### 5.4 Upgrade OTP to 6 digits — IMPLEMENTED
Auth OTP is 6 digits across validators, service logic, and tests.

---

## Group 6 — Infrastructure & Operations

### 6.1 Backup and disaster recovery — IMPLEMENTED (runbook + scripts)
Added:
- `chalo-backend/scripts/backup-db.sh`
- `chalo-backend/scripts/backup-db.ps1`
- `docs/development/BACKUP_DISASTER_RECOVERY.md`

Includes backup cadence, retention, restore drill, and RPO/RTO targets.

### 6.2 Incident runbooks — IMPLEMENTED
Added `docs/development/INCIDENT_RUNBOOK.md` with procedures for:
1. Payment webhook failure
2. SOS not delivered
3. Ride stuck in REQUESTED
4. Redis outage

### 6.3 Analytics and funnel tracking
There is no analytics integration. The engineering team cannot see where users drop off in the booking funnel.

**Key events to track:**
- `app_open`
- `home_screen_loaded`
- `destination_selected`
- `fare_estimate_shown`
- `book_ride_tapped`
- `ride_created`
- `driver_assigned`
- `ride_completed`
- `rating_submitted` / `rating_skipped`
- `payment_completed` / `payment_failed`

**Tool:** Mixpanel (free up to 20M events/month) or Firebase Analytics (free, Google ecosystem). A/B testing in V2 can use Firebase Remote Config + Analytics.

### 6.4 Feature flags — IMPLEMENTED
Backend `platform_config` keys seeded. Android Remote Config wired:
- `FeatureFlagRepository` interface in `domain/repository/`
- `FeatureFlagRepositoryImpl` fetches from Firebase Remote Config at startup with 1-hour TTL (60s in debug)
- Defaults: `enable_dynamic_surge=true`, `enable_wallet=false`, `enable_places_autocomplete=true`
- Injected into `ChaloApplication` via Hilt; `featureFlags` singleton available in any ViewModel via `@Inject`

---

## Group 7 — Code Quality

### 7.1 Replace Gson with kotlinx.serialization — IMPLEMENTED
All 5 DTO files migrated: `@SerializedName` → `@SerialName`, `@Serializable` added to every class.
`NetworkModule` uses `json.asConverterFactory(...)` with `ignoreUnknownKeys = true` and `coerceInputValues = true`.
Kotlin upgraded to **2.0.21** (KSP `2.0.21-1.0.27`) — the `org.jetbrains.kotlin.plugin.compose` and `org.jetbrains.kotlin.plugin.serialization` plugins are wired in both `build.gradle.kts` files.
Gson dependency removed; replaced with `converter-kotlinx-serialization` (built into Retrofit 2.11).

### 7.2 Room migration strategy
PARTIALLY IMPLEMENTED:
- `exportSchema = true` enabled in `AppDatabase`.
- Added centralized migration registry (`DatabaseMigrations`) and wired `addMigrations(*DatabaseMigrations.ALL)`.
- Removed `fallbackToDestructiveMigration()` from DB builder.

Remaining: add concrete migration objects when schema version changes (e.g., `MIGRATION_1_2`).

### 7.3 ProGuard keep rules for DTOs (short-term fix)
IMPLEMENTED in `app/proguard-rules.pro` with both class and class-member keep rules for `data.remote.dto`.

### 7.4 Consolidate duplicate Timber/logger setup
IMPLEMENTED baseline: Timber is initialized in `ChaloApplication` and no `android.util.Log` usages remain in the main Android source set.

---

## Improvement Principles

- Fix safety bugs first, then product gaps, then optimisations.
- Every backend change to a response shape must update the corresponding contract test.
- Do not add new screens without unit tests for the ViewModel.
- Prefer PostGIS-native queries over application-layer geo math for any driver-proximity logic.
- Keep `platform_config` as the single source of truth for business rules — do not hardcode fare constants in Android.
