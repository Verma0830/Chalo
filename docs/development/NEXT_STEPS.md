# Chalo Next Steps

Last updated: 2026-03-21

This file contains all pending work. Completed backend milestones are in CODEBASE.md section 4.

---

## 1. Android customer app completion

### Validate screens against backend contracts

Every screen that makes API calls needs to be manually walked through end-to-end against the running backend to confirm:

- The correct endpoint is called with the correct field names and types
- All possible response states (loading, success, error, empty) are handled and shown to the user
- Navigation after each action goes to the right screen

Priority screens (most likely to have gaps):

- `ActiveRideScreen` — polls ride status, polls driver location, shows OTP to customer, handles SOS
- `ScheduleRideScreen` — `scheduledAt` must be sent as ISO 8601 UTC, not local time
- `PaymentScreen` — UPI payment flow involves Razorpay SDK integration; CASH payment should skip to receipt directly
- `ScheduledListScreen` — cancellation from this screen needs the same reason code as on-demand rides

### Error and empty states

Every screen that loads data from the network must handle:

- **Loading**: spinner or shimmer while fetching
- **Success with data**: normal UI
- **Empty**: "no rides yet", "no notifications" type placeholder — not a blank screen
- **Network error**: message + retry button that re-triggers the same fetch
- **Auth error (401)**: should navigate back to phone input, not crash or hang

### Partial interactions to finish

- Scheduled ride cancellation from `ScheduledListScreen` — confirm cancel dialog + reason code picker
- Notification deep-link: tapping a notification should navigate to the relevant ride screen
- Rating prompt after ride completion — currently tracks a pending rating in DataStore (`pendingRatingRideId`); ensure it surfaces automatically on next app open

---

## 2. Android instrumentation testing (remaining high-priority gap)

The Android module now has 53 JVM unit tests in `app/src/test/`, and those run in CI. The biggest remaining risk is missing instrumentation coverage (`app/src/androidTest/`) for end-to-end UI/navigation/device behavior.

### What to write: unit tests

Location: `app/src/test/` — these run on JVM, no emulator needed, fast to run.

Framework: JUnit 4 + MockK + Kotlin coroutines test (`kotlinx-coroutines-test`).

**OtpVerifyViewModel tests:**
- Entering 6 digits triggers `onVerify()` automatically (and SMS Retriever auto-fills + submits)
- On successful OTP verify: `FirebaseAuth.signInWithCustomToken` is called with the token from the response
- On Firebase sign-in failure: `errorMessage` in UI state is set to "Authentication failed"
- On wrong OTP (API returns error): `errorMessage` in UI state shows the API error message
- Resend OTP resets the countdown timer

**AuthRepositoryImpl mapper tests:**
- `ProfileDto.toDomain()` maps every field correctly including optional `customerProfile`
- `customerProfile = null` → `User.customerProfile = null` (no crash)
- All numeric nullables (`totalRides`, `cancellationCount`) default to 0 when null

**RideRepositoryImpl mapper tests:**
- `RideDto` with all optional fields null maps cleanly to a `Ride` domain model
- `CancelRideRequest` is constructed with the correct `reasonCode` and optional `note`
- `CreateRideRequest` correctly encodes pickup/drop `LocationDto` and `paymentMethod`

**FareEstimateViewModel tests:**
- Fare estimate request is built from the entered pickup and drop coordinates
- Success: UI state contains estimated fare, distance, duration
- Error: UI state contains error message, not a crash

**ActiveRideViewModel tests:**
- Status polling interval triggers `getRideDetails` repeatedly
- When status changes to `COMPLETED`, polling stops and navigation event is emitted
- When status changes to `CANCELLED`, a cancellation event is emitted with fee amount

**UserPreferences tests (DataStore):**
- `saveUser()` stores all four fields
- `clearAll()` removes all keys
- `savePendingRating()` + `markPendingRatingShown()` produce correct flow values
- Use `androidx.datastore:datastore-preferences` test artifact with in-memory store

### What to write: integration-style tests

Location: `app/src/test/` — mock the Retrofit service, test repository + ViewModel together.

**Retrofit DTO mapping tests:**
- Parse a known JSON response string through Gson into the DTO and assert every field
- Ensures no `@SerializedName` typos and no missing fields
- Write one test per DTO: `VerifyOtpResponseDto`, `RideDto`, `FareEstimateDto`, `RideReceiptDto`, `CancelRideResponseDto`
- These tests catch backend field renames before they silently produce nulls in the app

**Error handling tests:**
- `ApiResponse(success=false, message="...")` is treated as a failure by the repository
- HTTP 422 is surfaced as a meaningful error message, not an unhandled exception
- HTTP 401 triggers token refresh via `AuthInterceptor` before failing

### What to write: instrumentation tests

Location: `app/src/androidTest/` — run on emulator or device, test full UI flows.

Framework: Compose testing (`androidx.compose.ui:ui-test-junit4`) + Hilt test (`hilt-android-testing`).

Use fake/mock repositories so tests do not hit the real backend.

**OTP flow:**
1. Launch app with no Firebase session
2. Assert `PhoneInputScreen` is shown
3. Enter a phone number, tap Send OTP
4. Assert navigation to `OtpVerifyScreen`
5. Enter 6 digits — assert `onVerify()` is triggered
6. Fake repository returns success → fake Firebase signs in
7. Assert navigation to `CompleteProfileScreen` (isNewUser=true) or `HomeScreen` (returning user)

**Book ride flow:**
1. Start on `HomeScreen` with a mocked authenticated user
2. Navigate to fare estimate, enter pickup + drop
3. Assert fare estimate result is displayed
4. Tap Book → assert ride creation request is made with correct body
5. Assert navigation to `ActiveRideScreen` with the returned ride ID

**Active ride status updates:**
1. Start `ActiveRideScreen` with a ride in `DRIVER_ASSIGNED` state
2. Fake status polling returns `DRIVER_ARRIVED` → assert UI updates
3. Fake status returns `IN_PROGRESS` → assert OTP display changes
4. Fake status returns `COMPLETED` → assert navigation to `PaymentScreen` or `RatingScreen`

**Post-ride payment and rating:**
1. Start `PaymentScreen` with a CASH ride → assert "no payment needed" UI and redirect to rating
2. Start `RatingScreen` → tap a star rating → assert `rateRide` is called with correct rating integer
3. Tap Skip → assert `skipRating` is called and navigation proceeds to `ReceiptScreen`
4. Start `ReceiptScreen` → assert fare, distance, and payment method are displayed correctly

---

## 3. Backend verification and cleanup

### Endpoint contract matrix

Produce a table of all 60 endpoints with: method, path, required fields, optional fields, role required, and example valid/invalid request. This exists implicitly in the validator files and POSTMAN_GUIDE.md but not in a single scannable sheet. Useful for:

- Catching drift between Android DTOs and backend validators
- Onboarding new contributors
- QA sign-off checklist before release

### Postman examples for newer endpoints

The following endpoints were added after the original Postman guide and may lack example collections:

- `POST /rides/sos/:sosAlertId/resolve`
- `GET /admin/rides` (filtered)
- `POST /admin/drivers/:driverId/auto-verify`
- `GET /driver/earnings/settlement`

Add example request bodies and expected responses to `docs/api/POSTMAN_GUIDE.md` or a linked Postman collection export.

### Webhook hardening verification

`POST /payments/webhook` uses Razorpay signature validation (`x-razorpay-signature` header, HMAC-SHA256). Verify in a production-like environment:

- A correctly signed webhook payload is accepted
- A tampered payload is rejected with 400
- Missing signature header is rejected
- Replay protection behavior (if any)

---

## 4. Mobile release readiness

### Release signing

- Generate a release keystore: `keytool -genkey -v -keystore chalo-release.jks -keyAlias chalo -keyalg RSA -keysize 2048 -validity 10000`
- Store securely — never commit to git
- Add signing config to `build.gradle.kts` under `signingConfigs`
- Confirm release APK is signed and not debuggable: `apksigner verify --print-certs app-release.apk`

### CI/CD for Android

Current pipeline (already implemented in `.github/workflows/ci.yml`):

```yaml
on: [push, pull_request]
jobs:
  android:
    steps:
      - lint                    # gradle -p chalo-customer-app lint
      - unit tests              # gradle -p chalo-customer-app test
      - debug build             # gradle -p chalo-customer-app assembleDebug
```

This catches build breaks and lint regressions before they reach the main branch. Keep Gradle and AGP versions compatible when Android tooling is upgraded.

### Crashlytics and FCM in release

- Crashlytics is in the dependencies (`firebase-crashlytics`). Verify it initialises in release builds and reports crashes to Firebase console.
- FCM: test with a real device in release variant — emulator FCM delivery can be unreliable.
- Confirm the notification channel `chalo_rides` is created on first launch (Android 8+).

### Network security config for release

Current config blocks all cleartext in release builds (`cleartextTrafficPermitted="false"` on `base-config`). Confirm:
- Production API base URL (`https://api.chalo.in/api/v1`) uses HTTPS
- SSL certificate is valid and not self-signed
- Certificate pinning is not needed for MVP but should be evaluated

### APK size and cold-start

Target metrics for mid-range device (3GB RAM, Snapdragon 680-class):
- APK size: under 15MB (after minification and resource shrinking in release)
- Cold start to interactive: under 2 seconds
- Measure with Android Studio Profiler or Baseline Profiles

---

## 5. Product and operations readiness

### Reconcile business policy docs

The implemented cancellation policy (reason codes, fee tiers, serial canceller threshold) and rating policy (1–5 stars, skip option, driver neutral rating 3.5) should be verified against the product documentation in `docs/product/chalo-product-documentation.md`. Update whichever is wrong.

### Launch runbook

Status: Implemented in `docs/development/INCIDENT_RUNBOOK.md`.

The runbook now covers:

- **Driver no-show**: what support does, how to trigger refund/credit
- **Payment failure**: how to identify via admin `/rides` filter (`paymentStatus=FAILED`), how to trigger manual resolution
- **SOS alert**: who gets notified server-side, what support does, how to resolve via `/rides/sos/:id/resolve`
- **Ride stuck in REQUESTED**: phantom driver job cleans up stale online drivers — explain TTL and how to manually reset

### Release notes process

Before each release, update:
1. `versionCode` and `versionName` in `app/build.gradle.kts`
2. A `CHANGELOG.md` entry summarising changes for customers and drivers
3. Backend version tag in git

---

## 6. Optional near-term improvements

### OpenAPI generation

The Zod validators in `src/validators/` contain the full schema for every request. A tool like `zod-to-openapi` can generate an OpenAPI 3.0 spec automatically. Benefits:
- API documentation that stays in sync with code
- Can generate client SDKs or mock servers from the spec
- Enables contract testing between backend and mobile

### Feature flags

Backend feature flags are now seeded in `platform_config` (`enable_dynamic_surge`, `enable_wallet`, `enable_places_autocomplete`) and dynamic surge rollout uses the flag.

Remaining: add Firebase Remote Config read path in Android startup and inject flags into ViewModels.

### Analytics taxonomy

Define a shared event taxonomy before launch. Events should be emitted by both backend (server-side, for ride lifecycle) and app (client-side, for UX actions). Examples:
- `ride_requested`, `ride_completed`, `ride_cancelled_by_customer`
- `otp_requested`, `otp_verified`, `profile_completed`
- `fare_estimate_viewed`, `payment_method_selected`

---

## Known gaps (honest summary)

| Gap | Impact | Priority |
|---|---|---|
| No Android instrumentation tests | UI/navigation regressions can ship despite JVM unit tests | High |
| DTO contract not enforced | Backend field rename silently produces null in app | High |
| Android CI depends on configured Gradle version (no wrapper committed) | Tooling mismatch can break CI after AGP upgrades | Medium |
| network_security_config IP entries wrong | Physical device testing with wrong IP fails silently | Medium |
| No release signing config | Can't ship to Play Store | High (before release) |
| Android still uses Gson DTO parsing | Reflection/null-safety risk until kotlinx.serialization migration | Medium |
