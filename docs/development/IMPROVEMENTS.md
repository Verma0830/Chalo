# Chalo — Improvements Backlog

Last updated: 2026-03-21
Source: derived from reading all backend services, Android source, schema, validators, and CI configuration.

Items are grouped by theme and ordered within each group by impact. Do not add items here that are already tracked in NEXT_STEPS.md (those are active in-sprint work). This file is for planned improvements beyond the immediate sprint.

---

## Group 1 — Critical Bugs (must fix before any user testing)

### 1.1 SOS sends (0.0, 0.0) coordinates
**File:** `ActiveRideScreen.kt` line 88
**Bug:** `onConfirm = { viewModel.onSosConfirm(0.0, 0.0) }` — hardcoded coordinates.
**Impact:** Emergency contacts and safety team receive a location in the Gulf of Guinea. The SOS feature is completely non-functional for its intended purpose.
**Fix:** In `ActiveRideViewModel.onSosConfirm()`, use `FusedLocationProviderClient.lastLocation.await()` to get the real GPS fix before calling the API. Pass `null, null` if location is unavailable and let the backend handle gracefully.

### 1.2 `isMyLocationEnabled = true` without permission guard
**File:** `HomeScreen.kt` line 69
**Bug:** `MapProperties(isMyLocationEnabled = true)` is passed unconditionally. If `ACCESS_FINE_LOCATION` is not granted (fresh install, user denied), this throws `SecurityException` and crashes.
**Fix:** Wrap with `accompanist-permissions`' `rememberPermissionState(ACCESS_FINE_LOCATION)` before the GoogleMap composable renders. `accompanist-permissions` is already in the dependency list.

### 1.3 No SMS gateway wired for OTP delivery
**File:** `sms.service.ts`
**Bug:** OTP is logged to console in dev and silently dropped in production. There is no SMS provider configured.
**Fix:** Wire MSG91 (recommended for India — cheapest bulk rate) or Fast2SMS. The `SmsService` interface is already defined — only the provider implementation is missing. Requires: `MSG91_API_KEY` env variable, one HTTP call to MSG91's transactional API per OTP.

---

## Group 2 — Android Product Gaps (blocks real user adoption)

### 2.1 Google Places Autocomplete — address search is missing
Without this, users cannot book a ride. The destination field on `HomeScreen` accepts text input but shows no suggestions. Users cannot find pickup/drop locations unless they manually drop a pin.

**What to build:**
1. Backend: `GET /places/autocomplete?input=&sessionToken=` — proxy to Google Places API, return top 5 predictions (place_id, description).
2. Backend: `GET /places/details?placeId=` — return lat/lng + formatted address.
3. Android: Replace the current text field with a `PlacesAutoCompleteField` composable showing a dropdown. On selection, call `/places/details` and set the location marker.

**Estimated impact:** Without this, 100% of users cannot complete their first booking.

### 2.2 Route polyline on active ride map
`ActiveRideScreen` shows three markers (pickup, driver, drop) on an otherwise empty map. No route is drawn.

**What to build:**
1. Fetch encoded polyline from `GET /rides/:rideId` (the `polyline` field is already in the schema — `Ride.polyline String?`).
2. Decode with Google Maps Android SDK `PolyUtil.decode()`.
3. Render as `Polyline` composable in the `GoogleMap` block.

### 2.3 Driver marker animation (smooth tracking)
Driver location updates from Firebase RTDB cause the driver marker to teleport. Each GPS update (every 3–5 seconds) snaps the marker with no transition.

**What to build:**
Use `ValueAnimator` with a `LatLngInterpolator` to interpolate marker position between consecutive coordinates. Standard implementation is ~50 lines of Kotlin. Also: replace the default red pin with a bike icon `BitmapDescriptor` drawn from a vector drawable.

### 2.4 ETA display (pickup and destination)
There is no ETA shown anywhere in the app. Users see "Driver assigned" with no indication of how long until arrival.

**What to build:**
1. Backend: Add `etaToPickupMins` and `etaToDestinationMins` fields to the `GET /rides/:rideId` response, calculated via Google Maps Distance Matrix API from driver's current location.
2. Android: Display ETA pill on `ActiveRideScreen` below the driver info card. Update from RTDB location events.

### 2.5 SMS Retriever for OTP auto-fill
Users must manually read the SMS and type the OTP. The Indian market expects auto-fill.

**What to build:**
Use `SmsRetriever` API (no `READ_SMS` permission required). The OTP SMS format must include an 11-character app hash. Wire on `OtpVerifyScreen`: start retriever before sending OTP request, listen for `SmsRetriever.SMS_RETRIEVED_ACTION` broadcast, parse OTP from message, fill `OtpTextField`.

### 2.6 Foreground service for background ride tracking
On Android 12+, RTDB listeners stop working when the app is backgrounded for more than ~10 minutes. A customer who locks their screen during a ride loses real-time driver location.

**What to build:**
`RideTrackingService : LifecycleService` with `foregroundServiceType="location"`. Start when ride status becomes `DRIVER_ASSIGNED`, stop when `COMPLETED` or `CANCELLED`. Show a persistent notification: "Your ride is in progress — tap to view". Wire to `ActiveRideViewModel` via the same RTDB flows.

---

## Group 3 — Backend Product Gaps

### 3.1 Dynamic surge pricing
Currently `surgeMultiplier` is a single global config key set by an admin. There is no algorithm.

**What to build (V2):**
1. Every 60 seconds, for each geohash cell (precision 6, ~1.2km × 0.6km), count `onlineDrivers` and `requestedRides` in the last 10 minutes.
2. Surge = `max(1.0, min(2.0, requestedRides / onlineDrivers))` — capped at 2x.
3. Store per-cell surge in Redis with 60-second TTL.
4. `fare.service.ts` looks up the pickup cell's surge instead of the global config key.
5. Show surge zones as colored polygons on the `HomeScreen` map.

**Impact:** Critical for driver retention during peak hours (8–10am, 5–8pm). Without surge incentive, drivers log off at peak demand.

### 3.2 Google Maps Distance Matrix for accurate fares
Haversine straight-line distance consistently underpredicts on-road distance by 25–40% in Faridabad's grid+old-town mix. Customers are being undercharged.

**What to build:**
Replace the Haversine primary calculation in `fare.service.ts` with `Google Maps Distance Matrix API` (already partially integrated — circuit breaker is present). The Maps API is already called for the Directions API in some paths. Unified: one `distanceMatrix` call gives both distance and duration.

**Cost:** Google Maps Distance Matrix is ~$5 per 1,000 elements. At 200 rides/day, cost is ~$1/day — negligible at MVP scale.

### 3.3 In-app wallet and refund mechanism
There is no wallet schema. CASH and UPI are the only payment methods. When a refund is needed (driver cancelled, customer overcharged), there is no way to credit the customer in-app.

**What to build (V2 schema additions):**
```prisma
model Wallet {
  id           String   @id @default(cuid())
  userId       String   @unique
  balance      Float    @default(0)
  currency     String   @default("INR")
  user         User     @relation(...)
  transactions WalletTransaction[]
}

model WalletTransaction {
  id          String   @id @default(cuid())
  walletId    String
  amount      Float
  type        WalletTxType  // CREDIT, DEBIT, REFUND, PROMO
  referenceId String?  // rideId or promoId
  description String
  createdAt   DateTime @default(now())
}
```

PaymentMethod enum: add `WALLET`. Backend: deduct from wallet balance atomically in a transaction when ride completes.

### 3.4 Geofencing and service area enforcement
A customer outside Faridabad (e.g., Delhi, Noida) can request a ride and the system will try to dispatch. This wastes driver capacity and creates a licensing issue (Haryana CNG auto permits are state-specific).

**What to build:**
Define a GeoJSON polygon for the Faridabad service area. In `ride.service.ts` `createRide()`, check `ST_Within(pickup_geography, service_area_polygon)` before creating the ride. Return `400 SERVICE_AREA_OUT_OF_BOUNDS` if outside. Store the polygon in `platform_config` as a GeoJSON string.

### 3.5 Promo code and referral engine
The first 1,000 users in a new market come from referrals. There is no schema or logic for this.

**What to build (minimal V1):**
```prisma
model PromoCode {
  id              String   @id @default(cuid())
  code            String   @unique
  discountAmount  Float
  maxUses         Int
  usedCount       Int      @default(0)
  expiresAt       DateTime?
  isActive        Boolean  @default(true)
}

model PromoRedemption {
  id          String   @id @default(cuid())
  promoCodeId String
  userId      String
  rideId      String
  createdAt   DateTime @default(now())
}
```

Backend: `POST /rides/apply-promo` validates code, checks one-use-per-user, deducts from `finalFare`. Android: promo code input field on `FareEstimateScreen`.

### 3.6 Automated driver settlement via Razorpay Route
Manual withdrawal processing does not scale beyond ~50 drivers. Razorpay Route (split payments) settles driver earnings automatically at transaction time.

**What to build:**
At ride completion, instead of creating an `Earning` record to be settled later, call Razorpay Route API to split the payment: platform keeps commission %, driver receives remainder as a direct bank transfer. Removes the entire manual withdrawal flow for UPI rides. CASH rides still need the manual withdrawal path.

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
The app is targeted at Faridabad, Haryana. The majority of users are Hindi or Punjabi speakers.

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
