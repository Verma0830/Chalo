# Chalo — What's Left To Do

> Last updated: March 2026
> Backend: ✅ Complete — 48 endpoints, 8.5/10 score
> Android apps: ⬜ Not started — start here next
> Deployment: ⬜ Not started

This document covers everything that is still pending, in priority order.

---

## Table of Contents

1. [Backend — P1 Features (small, build during Android dev)](#1-backend--p1-features)
2. [Third-Party Integrations to Set Up](#2-third-party-integrations-to-set-up)
3. [Razorpay Payment Testing Guide](#3-razorpay-payment-testing-guide)
4. [One-Time DB Setup (not done yet)](#4-one-time-db-setup)
5. [Customer Android App](#5-customer-android-app)
6. [Driver Android App](#6-driver-android-app)
7. [Deployment](#7-deployment)
8. [Post-Launch (P2 / P3)](#8-post-launch-p2--p3)

---

## 1. Backend — P1 Features

These are all small. Build them as the Android team hits each flow, rather than all upfront.

| # | Endpoint / Change | What | Effort | Needed for |
|---|---|---|---|---|
| 1 | Fare calculation update | Add 5% GST line. New `platform_config` key: `gst_percentage`. | 2 hrs | Receipt screen |
| 2 | Rating window check | Block rating if `completedAt > 24 hrs ago`. One condition in `rateRide()` and `rateCustomer()`. | 1 hr | Rating screens |
| 3 | `GET /notifications/unread-count` | Returns `{ count: N }`. Android needs this for the notification badge. | 1 hr | All screens |
| 4 | `POST /rides/:rideId/share` + `GET /track/:token` | Trip share link with 24h token. Public, no auth needed. | 4 hrs | Active ride screen |
| 5 | SMS receipt on ride completion | Call `smsService.send()` inside `completeRide()`. Needs `MSG91_API_KEY`. | 2 hrs | Ride completion |
| 6 | Razorpay cancellation fee charge | Trigger Razorpay order when `cancellationFee > 0` on UPI rides. Cash: driver collects. | 3 hrs | Cancel flow (UPI) |

**Total for all P1 backend work: ~13 hours.**

---

## 2. Third-Party Integrations to Set Up

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

**When to set up:** Before testing UPI payment flows. See Section 3 for the full test guide.

---

### MSG91 (SMS — OTP + Receipt + SOS)

**Used for:** SOS emergency SMS to emergency contact, ride receipt SMS.
**Without it:** SOS alert is saved in DB but not sent. OTP works via Firebase (already set up).

```
1. Go to https://msg91.com
2. Sign up → get API key + sender ID
3. Create a template for:
   - SOS alert: "SAFETY ALERT: [Name] is in a ride. Driver: [Name], Vehicle: [Number]. Location: [link]"
   - Ride receipt: "Your Chalo ride is complete. Distance: [X]km. Fare: ₹[X]. Driver: [Name]"

Add to chalo-backend/.env:
  MSG91_API_KEY=xxxxxxxxxxxxxxxxxxxxxxxxxx
  MSG91_SENDER_ID=CHALOM
  MSG91_TEMPLATE_ID_SOS=xxxxxxxxxx
  MSG91_TEMPLATE_ID_RECEIPT=xxxxxxxxxx
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

## 3. Razorpay Payment Testing Guide

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
4. POST /payment/create-order            → get razorpayOrderId
5. [Razorpay SDK completes payment using test UPI ID: success@razorpay]
6. POST /payment/verify  { razorpayOrderId, razorpayPaymentId, razorpaySignature }
   → ride.paymentStatus should be COMPLETED
7. GET  /rides/:rideId/receipt           → check fare breakdown
```

**Flow 2 — Payment failure**
```
1–4. Same as above
5. Use UPI ID: failure@razorpay
6. POST /payment/verify → should return 400 PAYMENT_VERIFICATION_FAILED
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

**Flow 4 — Cancellation fee charge (after P1 is built)**
```
1. POST /rides  { paymentMethod: "UPI" }   → create ride
2. [driver accepts → wait 3 minutes]
3. POST /rides/:rideId/cancel
   → response should include cancellationFee: 20
   → should auto-trigger a ₹20 Razorpay order
4. POST /payment/verify for the cancellation fee order
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

## 4. One-Time DB Setup

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

## 5. Customer Android App

**Tech stack:** Kotlin · Jetpack Compose · MVVM + Hilt · Retrofit · Firebase Auth + RTDB + FCM · Google Maps SDK

**Prompt file:** [docs/prompts/chalo-android-customer-prompt.md](../prompts/chalo-android-customer-prompt.md)

### Sprint order

| Sprint | Screens | Backend endpoints used | Time |
|---|---|---|---|
| 1 — Auth | Splash, phone entry, OTP, complete profile | `POST /auth/otp/send`, `POST /auth/otp/verify`, `PUT /auth/profile` | 1 week |
| 2 — Home + booking | Map, destination search, fare estimate, book ride | `POST /rides/fare-estimate`, `POST /rides` | 1–2 weeks |
| 3 — Active ride | Driver location on map, status updates, cancel, SOS | RTDB listener, `POST /rides/:rideId/cancel`, `POST /rides/:rideId/sos` | 1 week |
| 4 — Post-ride | Payment screen, rating, receipt | `POST /rides/:rideId/rate`, `GET /rides/:rideId/receipt` | 3–4 days |
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
```

---

## 6. Driver Android App

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

## 7. Deployment

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

## 8. Post-Launch (P2 / P3)

Build these after V1 is live and generating real rides. Priority based on user feedback.

### P2 — Growth (first 3 months post-launch)

| Feature | Why | Effort |
|---|---|---|
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
