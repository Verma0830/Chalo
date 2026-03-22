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

### 5.1 PostGIS driver search (replace bounding box with ST_DWithin)
Current driver search uses a lat/lng bounding box (`gte`/`lte`) filter via Prisma. This uses the B-tree composite index on `(currentLat, currentLng)` — correct but less accurate than a true radius check.

**Fix:**
```typescript
const nearbyDrivers = await prisma.$queryRaw<{userId: string; distanceKm: number}[]>`
  SELECT dp."userId",
         ST_Distance(
           ST_SetSRID(ST_MakePoint(dp."currentLng", dp."currentLat"), 4326)::geography,
           ST_SetSRID(ST_MakePoint(${pickupLng}, ${pickupLat}), 4326)::geography
         ) / 1000 AS "distanceKm"
  FROM driver_profiles dp
  WHERE dp."isOnline" = true
    AND dp."verificationStatus" = 'VERIFIED'
    AND ST_DWithin(
      ST_SetSRID(ST_MakePoint(dp."currentLng", dp."currentLat"), 4326)::geography,
      ST_SetSRID(ST_MakePoint(${pickupLng}, ${pickupLat}), 4326)::geography,
      ${radiusKm * 1000}
    )
  ORDER BY "distanceKm"
  LIMIT 5
`;
```
This uses the GiST spatial index created in the `postgis_indexes` migration and performs a correct spherical distance check.

### 5.2 OpenTelemetry distributed tracing
Request IDs are logged but not correlated across BullMQ jobs. When `ride-offer-expired` fires, its log context is disconnected from the original `createRide` request.

**What to build:**
1. Add `@opentelemetry/sdk-node` and `@opentelemetry/auto-instrumentations-node`.
2. Propagate trace context into BullMQ job data as `traceParent`.
3. Resume the span in the job worker using `propagation.extract()`.
4. Export spans to Jaeger (self-hosted, free) or Grafana Tempo.

### 5.3 OTP cleanup cron
`authService.cleanupExpiredOTPs()` is defined but never called. OTP records accumulate forever.

**Fix:** Add to `chalo-maintenance` queue in `queue.ts`:
```typescript
new CronJob('0 3 * * *', async () => {  // 3am daily
  await authService.cleanupExpiredOTPs();
});
```

### 5.4 Upgrade OTP to 6 digits
4-digit OTP has 10,000 combinations. Change to 6 digits:
- `CONSTANTS.OTP_DIGITS = 6`
- Zod validator: `z.string().length(6)`
- Android: `OtpTextField` max length → 6
- All contract tests updated
- Auth service tests updated

---

## Group 6 — Infrastructure & Operations

### 6.1 Backup and disaster recovery
There is no documented backup strategy. The PostgreSQL container on Docker Desktop has no volume snapshot policy.

**What to implement:**
- Daily `pg_dump` to an S3 bucket (or Firebase Storage bucket).
- Point-in-time recovery via PostgreSQL WAL archiving if using a managed DB (Supabase, Neon, or AWS RDS).
- Document RTO (Recovery Time Objective) and RPO (Recovery Point Objective) targets.
- Test restore procedure monthly.

### 6.2 Incident runbooks
There are no runbooks for the most likely production incidents:

**Runbooks to write:**
1. **Payment webhook failure** — Razorpay sends webhook, backend returns 5xx, Razorpay retries. If retries exhaust, payment status is stuck at `PENDING`. Resolution: check `razorpay_payment_id` in DB, manually trigger verification endpoint.
2. **SOS not delivered** — FCM send succeeded but customer's phone is offline. Resolution: check `sos_alerts` table, call emergency contact directly via the `alertSentTo` JSON field.
3. **Driver stuck in REQUESTED** — `ride-offer-expired` job ran but no driver found, `NO_DRIVER` status not set. Resolution: BullMQ UI, check queue, manually trigger re-dispatch or cancel ride.
4. **Redis down** — Rate limiter fails open (configured with `skip` on Redis error — confirm this is the case). Auth cache misses fall back to Firebase verify. Monitor for elevated Firebase API calls.

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

### 6.4 Feature flags
There is no feature flag system. Risky changes (new surge algorithm, new payment flow) cannot be rolled out to a percentage of users before full launch.

**What to build:**
Firebase Remote Config is already available (Firebase project is set up). Use it as a lightweight feature flag store:
- `enable_dynamic_surge: false` → `true` when ready
- `enable_wallet: false`
- `enable_places_autocomplete: false` → `true` after testing

Android: read flags in `AppModule` at startup, inject into ViewModels. Backend: read flags from `platform_config` table (already exists).

---

## Group 7 — Code Quality

### 7.1 Replace Gson with kotlinx.serialization
Gson uses reflection and is not null-safe — missing JSON fields silently become `null` on non-null Kotlin properties. `kotlinx.serialization` uses KSP codegen, is null-safe, and is not affected by R8 minification.

**Migration scope:** All files in `data/remote/dto/` — add `@Serializable` annotation, replace `@SerializedName` with `@SerialName`, replace `GsonConverterFactory` with `kotlinx.serialization.json.Json` + `KotlinxSerializationConverterFactory` in `NetworkModule`. Update contract tests to use the serialization library instead of Gson.

**Risk:** All DTO mappings must be verified after migration. The contract tests written in `AuthDtoContractTest` and `RideDtoContractTest` serve as the regression suite for this migration.

### 7.2 Room migration strategy
`AppDatabase` is version 1 with no migration defined. Any schema change will use destructive migration (data wipe on update).

**Fix:** Define `Migration(1, 2)` objects for every schema change. Enable `addMigrations()` on the `Room.databaseBuilder()` call. Add `exportSchema = true` and commit schema JSON files to track Room schema history.

### 7.3 ProGuard keep rules for DTOs (short-term fix)
Until Gson is replaced with kotlinx.serialization, add to `proguard-rules.pro`:

```proguard
-keep class com.chalo.customer.data.remote.dto.** { *; }
-keepclassmembers class com.chalo.customer.data.remote.dto.** { *; }
```

Or add `@Keep` to the top of each DTO file. This is a stopgap — the real fix is Group 7.1.

### 7.4 Consolidate duplicate Timber/logger setup
The Android app initialises Timber in `ChaloApplication.onCreate()`. Some ViewModels also use `Log.d()` directly. Standardise: all logging goes through `Timber` so tags and log levels are consistent and can be disabled in release builds via `Timber.plant()` (already done in Application class).

---

## Improvement Principles

- Fix safety bugs first, then product gaps, then optimisations.
- Every backend change to a response shape must update the corresponding contract test.
- Do not add new screens without unit tests for the ViewModel.
- Prefer PostGIS-native queries over application-layer geo math for any driver-proximity logic.
- Keep `platform_config` as the single source of truth for business rules — do not hardcode fare constants in Android.
