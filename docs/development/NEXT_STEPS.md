# Chalo — What's Left To Do

> Last updated: March 2026
> Backend: ✅ Complete — 58 endpoints, 8.5/10 score
> Android apps: ⬜ Not started — start here next
> Deployment: ⬜ Not started

This document covers everything that is still pending, in priority order.

---

## Table of Contents

1. [Backend — P1 Features (small, build during Android dev)](#1-backend--p1-features)
2. [Competitive Gap Analysis — What Other Apps Have That We Don't](#2-competitive-gap-analysis)
3. [Third-Party Integrations to Set Up](#3-third-party-integrations-to-set-up)
4. [Razorpay Payment Testing Guide](#4-razorpay-payment-testing-guide)
5. [One-Time DB Setup (not done yet)](#5-one-time-db-setup)
6. [Customer Android App](#6-customer-android-app)
7. [Driver Android App](#7-driver-android-app)
8. [Deployment](#8-deployment)
9. [Post-Launch (P2 / P3)](#9-post-launch-p2--p3)

---

## 1. Backend — P1 Features

### Already done (March 2026)

| Feature | Status | Notes |
|---|---|---|
| GST (5%) on ride fare | ✅ Done | Stored in `rides.gstAmount`. Not shown to customer/driver — internal accounting only. Config: `gst_percentage = 5` in platform_config. |
| Rating window (48h + skip) | ✅ Done | `rides.ratingSkippedAt` field + `POST /rides/:rideId/skip-rating` endpoint. 48h/2-prompt logic is Android-only (SharedPreferences). |
| Trip share / tracking link | ✅ Done | `POST /rides/:rideId/share` returns a 24h share URL. Public `GET /track/:token` returns ride status + live driver coordinates without auth. |
| Driver cancellation tracking | ✅ Done | `driver_profiles.driverCancellationCount*` fields track totals and daily counts. `POST /driver/rides/:rideId/cancel` now returns `cancellationStats` and alerts admins at 3 cancellations/day. |

### Remaining launch-critical backend work

None for this gap set. The only meaningful customer-facing backend launch blocker here was trip sharing, and it is now implemented.

### Decided against (and why)

| Feature | Decision | Reason |
|---|---|---|
| Notification badge count endpoint | ❌ Not needed for V1 | All critical ride notifications show live on ride screen via RTDB. App shows a red dot using existing `GET /notifications?limit=1`. Add count endpoint in P2 when promo notifications launch. |
| SMS receipt on completion | ❌ Not sending SMS | MSG91 costs ₹0.18–0.22/SMS (transactional tier). ₹600/month at 100 rides/day, ₹6,000/month at 1,000 rides/day. Full receipt already available in-app via `GET /rides/:rideId/receipt`. No SMS needed. |
| Razorpay auto-charge cancellation fee | ❌ Not for V1 | 90% of Faridabad rides are cash — cannot auto-charge. For UPI: RBI regulations require explicit user approval per debit. Fee shown clearly before cancel. Driver collects cash. V2: deduct from wallet. |

### Rating UX design (implemented)

**Flow (48-hour window):**
1. Ride completes → bottom sheet: [★★★★★ Rate] + [Rate Later]
2. "Rate Later" → Android stores `pendingRatingRideId` in SharedPreferences. No backend call.
3. Next app open (within **48h**) → shows once more: [Rate] + [Skip]
4. "Skip" → Android calls `POST /rides/:rideId/skip-rating` + clears SharedPreferences
5. After 48h → Android auto-clears — no backend call needed

**Total remaining backend work for these launch-critical gaps: ~0 hours.**

---

## 2. Competitive Gap Analysis

What Rapido, Ola, and Uber have that Chalo V1 does not. Sorted by how much it would hurt the app in production.

### 🔴 Missing — would directly hurt user trust or safety

| Feature | Rapido | Ola | Uber | Chalo | Impact |
|---|---|---|---|---|---|
| **Live trip share link** | ✅ | ✅ | ✅ | ✅ | Implemented in backend. Customer creates a 24h share link and family tracks via a public endpoint. |
| **In-app call / masked number** | ✅ | ✅ | ✅ | ❌ | Driver and customer can't contact each other without exposing real numbers. At pickup, driver needs to locate customer. Without masked calling, driver either calls with real number (privacy issue) or can't reach customer at all. **Plan: backend stores no phone numbers in responses. App-layer solution needed (Exotel masked calling or just show driver's name + vehicle prominently and expect them to call). Short-term: acceptable. Medium-term: add Exotel masked calling.** |

### 🟡 Missing — noticeable gap, not a blocker for launch

| Feature | Rapido | Ola | Uber | Chalo | Notes |
|---|---|---|---|---|---|
| **Driver ETA updates** | ✅ | ✅ | ✅ | Partial | Backend stores `durationMins` at booking, but once driver accepts, no live ETA update. Driver location updates come via RTDB. Android can compute ETA from current driver location + Google Maps Directions API directly — no backend needed. |
| **Driver cancellation penalty** | ✅ | ✅ | ✅ | ✅ Tracking only | Backend now tracks `driverCancellationCount`, `driverCancellationCountDaily`, and `driverCancellationLastAt`, and auto-alerts admins when a driver hits 3 cancellations in a day. Actual suspensions and penalties remain a P2 ops policy feature. |
| **Tips for driver** | ❌ | ✅ | ✅ | ❌ | Post-ride, customer can add ₹10/₹20/₹50 tip. Good driver income boost. Skip for V1. |
| **Feedback categories** | ✅ | ✅ | ✅ | ❌ | After rating: "Safe driving", "Clean vehicle", "Punctual" etc. Star + comment is fine for V1. |
| **Favourite places** | ✅ | ✅ | ✅ | ✅ | Already in schema: `savedHomeLat`, `savedWorkLat` etc. Backend has it — just needs Android UI. |
| **Ride again (repeat route)** | ✅ | ✅ | ✅ | ❌ | One tap to re-book a previous ride. All data is in ride history. Android-only feature, no backend needed. |

### 🟢 Not needed for V1 (out of scope)

| Feature | Why not for V1 |
|---|---|
| Ride pooling / shared rides | Complex matching, not relevant for bike rides |
| Corporate accounts | Need billing module — P3 |
| iOS app | Faridabad is Android-dominant — iOS post-V1 |
| Automatic refunds | Razorpay refund webhook not handled — P3 |
| Driver incentive bonuses (auto) | Manual admin ops fine for V1 — P2 |

### What this means for the app launch

The launch-critical backend safety gap is now closed: trip sharing is implemented and testable.

Masked calling is still the main noticeable gap that remains. ETA does not need backend work because Android can derive it from live driver coordinates. Driver cancellation quality is now measurable on the backend, even though automated suspension policy stays post-launch.

---

## 3. Third-Party Integrations to Set Up

These require accounts and API keys. None block local development — fallbacks exist for all.

### Google Maps API Key

**Used for:** Real route distance and duration in fare calculation.
**Without it:** Haversine fallback (×1.2 road correction factor) — works fine for dev and early testing.

```
1. Go to https://console.cloud.google.com
2. Create a project (or use an existing one)
3. Enable: "Directions API" + "Places API"
4. APIs & Services → Credentials → Create API Key
5. Restrict key: HTTP referrers (for web) or Android apps (for the app)
6. Google gives $200/month free credit — covers ~40,000 direction requests/month

Add to chalo-backend/.env:
  GOOGLE_MAPS_API_KEY=AIzaxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

**When to set up:** Before real fare accuracy matters. Not needed for MVP testing.

---

### Razorpay (UPI + Card Payments)

**Used for:** UPI payments from customers, cancellation fee charging, driver payouts (P3).
**Without it:** Cash rides work 100% — no Razorpay needed at all.

```
1. Go to https://dashboard.razorpay.com
2. Sign up (free, KYC required for live mode)
3. Settings → API Keys → Generate Key (TEST mode first)
4. Settings → Webhooks → Add webhook URL → select events

Add to chalo-backend/.env:
  RAZORPAY_KEY_ID=rzp_test_xxxxxxxxxxxx
  RAZORPAY_KEY_SECRET=xxxxxxxxxxxxxxxxxxxx
  RAZORPAY_WEBHOOK_SECRET=your_webhook_secret

Webhook URL (local dev):
  Use ngrok: ngrok http 3001
  Then: http://your-ngrok-url/api/v1/payments/webhook

Switch to live:
  RAZORPAY_KEY_ID=rzp_live_xxxxxxxxxxxx  (starts with rzp_live_, not rzp_test_)
```

**When to set up:** Before testing UPI payment flows. See Section 4 for the full test guide.

---

### MSG91 (SMS — SOS only)

**Used for:** SOS emergency SMS to emergency contact.
**Without it:** SOS alert is saved in DB but SMS not sent. OTP works via Firebase (already set up).
**Note:** Receipt SMS is not needed — full receipt is in-app. MSG91 is ₹0.18–0.22/SMS transactional.

```
1. Go to https://msg91.com
2. Sign up → get API key + sender ID
3. Create a template for:
   - SOS alert: "SAFETY ALERT: [Name] is in a ride. Driver: [Name], Vehicle: [Number]. Location: [link]"

Add to chalo-backend/.env:
  MSG91_API_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxx
  MSG91_SENDER_ID=CHALOM
  MSG91_TEMPLATE_ID_SOS=xxxxxxxxxx
```

**When to set up:** Before SOS feature is tested end-to-end.

---

### Firebase Storage (Driver Document Uploads)

**Used for:** Storing driver KYC photos (DL, RC, Aadhaar, bike photo).
**Without it:** Documents are stored as plain URLs — driver app must upload elsewhere and pass URL.

```
Firebase Storage is already enabled on your project (Spark plan: 5 GB free).
No extra setup needed on the backend — the driver app uploads directly to Firebase Storage
using the Firebase Storage SDK, then passes the download URL to POST /driver/documents.

CORS rules (set in Firebase console → Storage → Rules):
  Allow read/write only to authenticated users.
```

**When to set up:** When building the driver app document upload screen.

---

## 4. Razorpay Payment Testing Guide

### Test mode vs Live mode

```
rzp_test_ keys → test mode  → no real money moves, use test cards
rzp_live_ keys → live mode  → real money, real bank accounts

Always test thoroughly in test mode before switching to live.
```

### Test card numbers (always work in test mode)

| Scenario | Card number | CVV | Expiry |
|---|---|---|---|
| Success | 4111 1111 1111 1111 | Any 3 digits | Any future date |
| Success (Indian) | 5267 3181 8797 5449 | 123 | 10/25 |
| Payment failed | 4000 0000 0000 0002 | Any | Any future |
| Insufficient funds | 4000 0000 0000 9995 | Any | Any future |

### Test UPI IDs (Razorpay test mode)

```
success@razorpay  → always succeeds
failure@razorpay  → always fails
```

### What to test (Postman flows)

**Flow 1 — UPI ride payment (happy path)**
```
1. POST /rides/fare-estimate              → get fare amount
2. POST /rides   { paymentMethod: "UPI" }  → create ride
3. [simulate driver accepting + completing ride via driver endpoints]
4. POST /payments/order                  → get razorpayOrderId
5. [Razorpay SDK completes payment using test UPI ID: success@razorpay]
6. POST /payments/verify  { razorpayOrderId, razorpayPaymentId, razorpaySignature }
   → ride.paymentStatus should be COMPLETED
7. GET  /rides/:rideId/receipt           → check fare breakdown
```

**Flow 2 — Payment failure**
```
1–4. Same as above
5. Use UPI ID: failure@razorpay
6. POST /payments/verify → should return 400 PAYMENT_VERIFICATION_FAILED
   → ride.paymentStatus should stay PENDING
```

**Flow 3 — Webhook testing**
```
1. Start ngrok: ngrok http 3001
2. Set webhook URL in Razorpay dashboard: https://your-ngrok/api/v1/payments/webhook
3. Trigger a test payment through the Razorpay dashboard (Test → Simulate payment)
4. Check server logs — should see "Webhook received: payment.captured"
5. Check ride in DB — paymentStatus should update to COMPLETED
```

**Flow 4 — Cancellation fee (informational)**
```
1. POST /rides  { paymentMethod: "UPI" }   → create ride
2. [driver accepts → wait 3 minutes]
3. POST /rides/:rideId/cancel
   → response includes cancellationFee: 20
   → fee shown on screen — driver collects cash (or deducted from wallet in V2)
```

**Flow 5 — Refund testing**
```
Via Razorpay dashboard (test mode):
  Payments → select a payment → Refund
  → Razorpay sends refund webhook
  → Backend should handle (currently not implemented — P3)
```

### Webhook events to handle (current + future)

| Event | Current support | Future |
|---|---|---|
| `payment.captured` | ✅ Handled — marks ride PAID | |
| `payment.failed` | ✅ Handled — marks ride FAILED | |
| `refund.created` | ❌ Not handled | P3: mark dispute as refunded |
| `payout.processed` | ❌ Not handled | P3: mark driver withdrawal COMPLETED |
| `payout.failed` | ❌ Not handled | P3: notify driver, retry |

### Switching to live mode (production checklist)

```
[ ] Replace rzp_test_ keys with rzp_live_ keys in production .env
[ ] KYC completed on Razorpay account (required for live)
[ ] Webhook URL points to production server (not ngrok)
[ ] Webhook secret updated to production value
[ ] Test one live payment with a real card before launch
[ ] Enable Razorpay fraud protection (dashboard → Settings → Risk)
```

---

## 5. One-Time DB Setup

These need to be run once against the production (and dev) database:

```bash
# Run inside chalo-backend/

# 1. Apply all pending migrations
npx prisma migrate deploy

# 2. Seed platform config (fares, commission %, cancellation window)
#    Without this, fare calculations use hardcoded CONSTANTS defaults — still works,
#    but admin config endpoints won't have any rows to read from.
npm run db:seed

# 3. Promote first admin (replace phone number)
curl -X POST http://localhost:3001/api/v1/admin/promote \
  -H "Content-Type: application/json" \
  -H "x-internal-api-key: chalo-internal-dev-key-change-in-prod" \
  -d '{ "phone": "+91XXXXXXXXXX" }'
```

---

## 6. Customer Android App

**Tech stack:** Kotlin · Jetpack Compose · MVVM + Hilt · Retrofit · Firebase Auth + RTDB + FCM · Google Maps SDK

**Prompt file:** [docs/prompts/chalo-android-customer-prompt.md](../prompts/chalo-android-customer-prompt.md)

### Sprint order

| Sprint | Screens | Backend endpoints used | Time |
|---|---|---|---|
| 1 — Auth | Splash, phone entry, OTP, complete profile | `POST /auth/otp/send`, `POST /auth/otp/verify`, `PUT /auth/profile` | 1 week |
| 2 — Home + booking | Map, destination search, fare estimate, book ride | `POST /rides/fare-estimate`, `POST /rides` | 1–2 weeks |
| 3 — Active ride | Driver location on map, status updates, cancel, SOS, share ride | RTDB listener, `POST /rides/:rideId/cancel`, `POST /rides/:rideId/sos`, `POST /rides/:rideId/share` | 1 week |
| 4 — Post-ride | Payment screen, rating, receipt | `POST /rides/:rideId/rate`, `POST /rides/:rideId/skip-rating`, `GET /rides/:rideId/receipt` | 3–4 days |
| 5 — History + profile | Past rides, profile, emergency contact | `GET /rides/history`, `PUT /auth/emergency-contact` | 3–4 days |
| 6 — Scheduled rides | Date/time picker, scheduled list | `POST /rides/schedule`, `GET /rides/scheduled` | 2–3 days |
| 7 — Notifications | FCM foreground/background, notification list | `GET /notifications`, `PUT /notifications/:id/read` | 2 days |
| 8 — Polish | Hindi strings, offline state, loading states, retry | n/a | 1 week |

### Key Android implementation notes

```
RTDB listener for live ride status:
  db.getReference("rides/{rideId}").addValueEventListener(...)
  → Listen for status, driverLat, driverLng changes

RTDB fallback (on 2G/3G connection loss):
  Start polling GET /rides/:rideId/location every 5s
  in onCancelled callback of the ValueEventListener

OTP display (after driver accepts):
  FCM notification data payload includes "otp" field
  → Show it prominently on the active ride screen
  → Customer reads OTP to driver when they arrive

SOS button:
  Press-and-hold 2 seconds → confirm dialog → POST /rides/:rideId/sos
  SOS_HOLD_DURATION_MS = 2000 (from constants)

Share ride flow:
  Tap "Share Ride" → POST /rides/:rideId/share
  Backend returns full public URL: /api/v1/track/:token
  Share via WhatsApp or SMS — recipient opens the public link, no auth needed

Rating skip flow:
  After ride: show rate bottom sheet + "Rate Later" button
  "Rate Later" → store rideId in SharedPreferences
  Next app open (within 48h): show once more with "Skip" button
  "Skip" → POST /rides/:rideId/skip-rating → clear SharedPreferences
  After 48h: auto-clear SharedPreferences, no API call needed
```

---

## 7. Driver Android App

**Package name:** `com.chalo.driver` · Min SDK: API 28

### Sprint order

| Sprint | Screens | Backend endpoints used | Time |
|---|---|---|---|
| 1 — Auth + KYC | Phone OTP, registration form, document upload, pending screen | `POST /auth/register-driver`, `POST /driver/documents` | 1 week |
| 2 — Home + toggle | Earnings summary card, go online/offline, GPS foreground service | `POST /driver/go-online`, `POST /driver/go-offline`, `POST /driver/location` | 1 week |
| 3 — Ride request | Incoming ride card (60s countdown), accept/decline, navigate to pickup, arrived | `GET /driver/rides/incoming`, `POST /driver/rides/:id/accept`, `POST /driver/rides/:id/arrived` | 1 week |
| 4 — Ride in progress | OTP entry to start, navigation to drop, end ride, rate customer | `POST /driver/rides/:id/start`, `POST /driver/rides/:id/complete`, `POST /driver/rides/:id/rate-customer` | 3–4 days |
| 5 — Earnings | Earnings summary, trip history, withdrawal request | `GET /driver/earnings/summary`, `GET /driver/earnings`, `POST /driver/withdrawals` | 3–4 days |
| 6 — Profile + history | Document management, trip history, SOS | `GET /driver/trips`, `GET /driver/status` | 2–3 days |

### Key Android implementation notes

```
Location foreground service:
  Must run as a Foreground Service with FOREGROUND_SERVICE_LOCATION permission
  POST /driver/location every 5 seconds while online
  Stop when driver goes offline

OTP start flow:
  Customer reads OTP to driver → driver types it into the "Start Ride" screen
  POST /driver/rides/:rideId/start  { otp: "1234" }
  Backend validates and transitions DRIVER_ARRIVED → IN_PROGRESS

Arrived button:
  Only activates when driver is within 200m of pickup coordinates
  Use Haversine formula in the Android app to check distance
  DRIVER_ARRIVED_RADIUS_METERS = 200 (from constants)

Incoming ride timeout:
  Driver has 60 seconds to accept (RIDE_ACCEPT_WINDOW_SECS)
  Show countdown timer on the incoming ride card
  On timeout: card disappears, backend auto-reassigns
```

---

## 8. Deployment

### Recommended: Railway (simplest for V1)

```
1. Go to https://railway.app → New Project → Deploy from GitHub
2. Select the repo → set root directory to chalo-backend/
3. Add a PostgreSQL plugin (auto-provides DATABASE_URL with PostGIS support)
4. Add a Redis plugin (auto-provides REDIS_URL)
5. Set all environment variables in Railway dashboard (copy from .env)
6. Railway auto-detects Node.js, runs npm start
7. Custom domain available for free

Build command (Railway):  npm install && npm run build
Start command:            npm start
Post-deploy:              npx prisma migrate deploy (set as release command)
```

### Alternative: Render

```
1. https://render.com → New Web Service → connect GitHub repo
2. Root directory: chalo-backend
3. Build: npm install && npm run build && npx prisma migrate deploy
4. Start: npm start
5. Add Render PostgreSQL (paid tier has PostGIS)
6. Add Render Redis
```

### Pre-launch production checklist

```
Environment:
[ ] NODE_ENV=production
[ ] DATABASE_URL → production DB (Railway/Supabase/Neon)
[ ] REDIS_URL → production Redis
[ ] INTERNAL_API_KEY → change from dev value to a strong secret

Firebase:
[ ] Use production Firebase project (not dev)
[ ] Firebase service account JSON for production project
[ ] FIREBASE_DATABASE_URL → production RTDB URL

Payments:
[ ] RAZORPAY_KEY_ID → rzp_live_ key (not rzp_test_)
[ ] RAZORPAY_KEY_SECRET → live secret
[ ] RAZORPAY_WEBHOOK_SECRET → set in Razorpay dashboard with production URL
[ ] Webhook URL registered: https://your-domain/api/v1/payments/webhook

Maps & SMS:
[ ] GOOGLE_MAPS_API_KEY → restricted to your server IP
[ ] MSG91_API_KEY → production account with approved templates

Database:
[ ] npx prisma migrate deploy (run once on production DB)
[ ] npm run db:seed (seed platform config)
[ ] POST /admin/promote (promote first admin user)
[ ] CREATE EXTENSION IF NOT EXISTS postgis; (if hosting on non-Railway Postgres)

Security:
[ ] Verify CORS origins are production domains only
[ ] Verify rate limiter is active (already configured)
[ ] Run npm audit → fix any high-severity vulnerabilities
```

---

## 9. Post-Launch (P2 / P3)

Build these after V1 is live and generating real rides. Priority based on user feedback.

### P2 — Growth (first 3 months post-launch)

| Feature | Why | Effort |
|---|---|---|
| Exotel masked calling | Privacy — driver and customer can call each other without real numbers | Medium |
| Promo codes | New user acquisition — first ride discount | Large |
| Referral system | Low-cost growth — "share with friend, both get ₹50" | Medium |
| Surge automation | Revenue optimization — auto-price during peak demand | Medium |
| Customer wallet | Reduces payment friction for repeat users | Large |
| Driver incentive bonuses | Improve driver supply during peak hours | Large |
| Subscription renewal automation | Remove manual billing, reduce churn | Medium |

### P3 — Operational (when you have 100+ drivers)

| Feature | Why |
|---|---|
| Admin analytics dashboard | Understand where rides fail, where drivers are sparse |
| Driver suspension system | Handle bad drivers without manual DB edits |
| Dispute resolution | Handle fare disputes, wrong routes, lost items |
| Document expiry alerts | Legally required to ensure valid driver licenses |
| Auto-settlement (T+2) | Remove manual payout processing |
| Driver bank verification | Reduce failed payouts before they happen |

### Post-Launch Tech Debt to Clear

| Item | When |
|---|---|
| Replace hardcoded `idleTimeScore = 0.5` with real idle time calc | After 1 month of data |
| Roll up `driverRating` on ride → `CustomerProfile.ratingAvg` | Before driver incentives feature |
| GST invoice generation (PDF) | Before filing GST returns |
| iOS app | Low priority — Faridabad is Android-dominant |
| Web booking page | After Android app is stable |
