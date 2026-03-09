# Chalo Backend — Complete Flow Reference
> How every piece of the backend works, from registration to ride completion to payout.
> Last updated: March 2026 | Score: 8.5/10 | Endpoints: 58

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [How Auth Works](#2-how-auth-works)
3. [Customer Flow (full lifecycle)](#3-customer-flow)
4. [Driver Flow (full lifecycle)](#4-driver-flow)
5. [What Happens When a Ride is Created](#5-ride-creation--driver-search)
6. [Ride Lifecycle State Machine](#6-ride-lifecycle-state-machine)
7. [Fare Calculation](#7-fare-calculation)
8. [Cancellation Fee](#8-cancellation-fee)
9. [Payments](#9-payments)
10. [Driver Earnings & Settlement](#10-driver-earnings--settlement)
11. [Admin Panel](#11-admin-panel)
12. [Real-time (Firebase RTDB)](#12-real-time-firebase-rtdb)
13. [Push Notifications (FCM)](#13-push-notifications-fcm)
14. [SOS Safety System](#14-sos-safety-system)
15. [Background Jobs (BullMQ)](#15-background-jobs-bullmq)
16. [What's Still Left to Build](#16-whats-still-left-to-build)

---

## 1. System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT APPS                               │
│   Customer Android App          Driver Android App               │
└────────────┬────────────────────────────┬───────────────────────┘
             │  HTTP REST API             │  HTTP REST API
             ▼                            ▼
┌─────────────────────────────────────────────────────────────────┐
│               Express API  (port 3001 / 5000 in Docker)          │
│  auth ─ rides ─ driver ─ payment ─ notifications ─ admin         │
└──────┬───────┬──────────┬───────────────────────────────────────┘
       │       │          │
       ▼       ▼          ▼
  PostgreSQL  Redis    Firebase
  (PostGIS)  (cache,  (Auth OTP,
  chalo_db   queues,   FCM push,
  port 5433  offers)   RTDB live)
       │
  BullMQ workers (OTP cleanup, ride-offer expiry)
```

**Every request flows through:**
```
HTTP → requestId → helmet/cors/hpp → XSS sanitize → rateLimiter
     → authenticate (Firebase token → DB user lookup via Redis cache)
     → authorize(role) → validateBody/Query/Params (Zod)
     → idempotency (payment endpoints only)
     → Controller → Service → Prisma/Redis/Firebase
     → ApiResponse.success(res, data, message)
```

---

## 2. How Auth Works

### 2a. Customer Registration / Login (same flow)

```
1. Customer: POST /auth/otp/send   { phone: "+919876543210" }
   → Rate limit: max 3 OTPs per phone per 15 minutes
   → Generates 4-digit OTP
   → Stores SHA-256 hash of OTP in otp_verifications table (5 min expiry)
   → In DEV: prints OTP to server console
   → Returns: { message: "OTP sent", expiresIn: 300 }

2. Customer: POST /auth/otp/verify  { phone, otp }
   → Hashes incoming OTP, compares against DB (atomic transaction)
   → Marks OTP record as verified
   → If phone not in DB → creates User (CUSTOMER) + CustomerProfile atomically
   → Returns: { isNewUser: true/false, user: { id, phone, name, role } }
   → NOTE: No Firebase token here — client must create Firebase custom token
          from user.id if using Firebase Auth
```

### 2b. Driver Registration (NEW — no manual SQL)

```
1. Driver: POST /auth/otp/send   { phone }   (same endpoint as customer)
2. Driver: POST /auth/register-driver  { phone, otp, name }
   → Verifies OTP atomically (same hash check)
   → If phone already CUSTOMER → throws 409 (use different number)
   → If phone already DRIVER → returns existing driver (idempotent)
   → Creates User (DRIVER role) + DriverProfile atomically in one transaction
   → Returns: { isNewUser: true, user: { id, phone, name, role: "DRIVER" } }
```

### 2c. Admin Promotion (no SQL, no Firebase auth needed)

```
POST /admin/promote
Header: x-internal-api-key: chalo-internal-dev-key-change-in-prod
Body: { phone: "+91XXXXXXXXXX" }
→ Finds user by phone
→ Updates role to ADMIN
→ Idempotent (already ADMIN → returns without error)
→ User must re-login to get token with ADMIN role
```

### 2d. All Subsequent Requests

```
Header: Authorization: Bearer <firebase_id_token>

authenticate middleware:
  1. Calls firebase.getAuth().verifyIdToken(token)
  2. Gets Firebase UID from token
  3. Checks Redis cache: auth:user:{uid} (5 min TTL)
  4. If not cached → queries PostgreSQL for user
  5. Attaches user to req.user = { id, phone, role, isActive }

authorize('DRIVER') middleware:
  → Reads req.user.role — must match
  → Role is in PostgreSQL, NOT in Firebase token
  → Role change takes up to 5 min to propagate (Redis TTL)
```

---

## 3. Customer Flow

### Step 1 — Register and Complete Profile

```
POST /auth/otp/send       → get OTP
POST /auth/otp/verify     → creates account, role = CUSTOMER
PUT  /auth/profile        → set name, email, languagePref (hi/pa/en)
PUT  /auth/emergency-contact  → add emergency contact (for SOS)
PUT  /auth/saved-location → save home / work coordinates
PUT  /auth/device-token   → register FCM token for push notifications
```

### Step 2 — Estimate Fare Before Booking

```
POST /rides/fare-estimate
Body: { pickup: { lat, lng, address }, drop: { lat, lng, address } }

→ Calls Google Maps Directions API (or Haversine fallback if no API key)
→ Gets distanceKm + durationMins
→ Fetches platform_config from Redis cache (5 min TTL):
    base_fare_per_km = 12
    base_fare_per_min = 2
    min_fare = 30
    surge_multiplier (if surge_enabled = true)
→ Calculates: (distanceKm × 12) + (durationMins × 2) + 5 (booking fee)
→ Applies surge if enabled: total × surgeMultiplier
→ Applies min_fare floor: max(calculated, 30)

Returns: { distanceKm, durationMins, baseFare, surgeMultiplier, totalFare }
```

### Step 3 — Create a Ride

```
POST /rides
Body: { pickup, drop, paymentMethod: "CASH" | "UPI" }
Header: Idempotency-Key: <uuid>   ← prevents duplicate rides on retry

→ Idempotency check: key stored in Redis 24h
→ Checks customer has no active ride (atomic transaction)
→ Calculates fare again (locked price)
→ Creates Ride record with status = REQUESTED
→ Triggers background driver search (non-blocking)
→ Returns: { rideId, status: "REQUESTED", fare, pickup, drop }
```

### Step 4 — Wait for Driver (background process)

```
Customer app polls GET /rides/:rideId to check status
OR listens on Firebase RTDB: rides/{rideId}/status

Behind the scenes (see section 5 for full detail):
  → PostGIS finds nearby online VERIFIED drivers (5km radius)
  → Top 5 notified simultaneously via FCM
  → 60-second window for any of them to accept
  → If all decline/timeout → next batch of 5
  → If no drivers at all → status = NO_DRIVER after 2 minutes
```

### Step 5 — Ride in Progress

```
When driver accepts:
  → Customer gets FCM: "Driver Found! Ride OTP: 4821"
  → Customer sees OTP on screen — must show this to driver
  → status = DRIVER_ASSIGNED

When driver arrives at pickup:
  → Customer gets FCM: "Driver has arrived"
  → status = DRIVER_ARRIVED

When driver enters OTP correctly + starts ride:
  → Customer gets FCM: "Ride started — heading to [drop address]"
  → status = IN_PROGRESS

Track driver live location via Firebase RTDB:
  → GET /rides/:rideId/location  ← REST fallback
  → RTDB: rides/{rideId}/driverLat + driverLng  ← real-time
```

### Step 6 — After Ride

```
Ride completes → status = COMPLETED

Customer can:
  POST /rides/:rideId/rate     { rating: 1-5, comment: "..." }
  GET  /rides/:rideId/receipt  ← full fare breakdown
  GET  /rides/history          ← paginated list of past rides
```

### Step 7 — Cancel (if needed)

```
POST /rides/:rideId/cancel  { reason: "..." }

Cancellation fee rules:
  → If status = REQUESTED → no fee (no driver yet)
  → If status = DRIVER_ASSIGNED or DRIVER_ARRIVED:
      → Check time since driverAssignedAt
      → If < 120 seconds (free window) → no fee
      → If > 120 seconds → cancellationFee = ₹20
  → Response includes: { status: "CANCELLED", cancellationFee: 0 | 20 }
  → Driver gets FCM notification
  → Both free_cancel_window_secs and cancel_fee_amount are configurable
    via PUT /admin/config/:key
```

---

## 4. Driver Flow

### Step 1 — Register and Submit Documents

```
POST /auth/otp/send           → get OTP
POST /auth/register-driver    { phone, otp, name }  → creates account
PUT  /auth/device-token       → register FCM token

POST /driver/documents
Body: {
  licenseNumber, licenseUrl,
  rcNumber, rcUrl,
  aadharNumber, aadharUrl,
  vehicleNumber, vehicleModel,
  bikePhotoUrl (optional)
}
→ Stores document URLs in driver_profiles
→ Sets verificationStatus = PENDING (was already PENDING on creation)
→ Admin must review and approve before driver can go online
```

### Step 2 — Verification (via Admin)

```
verificationStatus flow:
  PENDING → UNDER_REVIEW → VERIFIED (can go online)
                         → REJECTED (must resubmit)

Admin can also: POST /admin/drivers/:driverId/auto-verify
  → Calls KYCProvider (Surepass if API key set, Manual otherwise)
  → If confidence >= 0.85 → auto-approves
  → If lower → leaves for human review
```

### Step 3 — Go Online / Offline

```
POST /driver/go-online   { lat, lng }
→ Checks verificationStatus = VERIFIED (throws 403 if not)
→ Sets isOnline = true, currentLat, currentLng in driver_profiles
→ Syncs to RTDB: drivers/{userId}/isOnline = true
→ Updates Prometheus gauge: onlineDriversGauge.inc()

POST /driver/go-offline
→ Sets isOnline = false in driver_profiles
→ Syncs to RTDB: drivers/{userId}/isOnline = false

POST /driver/location   { lat, lng }
→ Updates currentLat, currentLng, lastLocationUpdate in driver_profiles
→ If driver has active ride → syncs to RTDB: rides/{rideId}/driverLat, driverLng
  (customer app reads from here for live tracking)

GET /driver/status
→ Returns online state + active ride info
→ Also returns cancellationStats: total, today, lastCancelledAt, alertThreshold
```

### Step 4 — Receive and Accept/Decline a Ride Offer

```
Driver receives FCM push: "New Ride Request — ₹55 — 4.2km"
→ Driver app calls: GET /driver/rides/incoming
  → Reads Redis key ride:offer:{userId} → gets rideId
  → Returns ride details: pickup, drop, fare, customer name, customer cancellation count

Driver can:
  POST /driver/rides/:rideId/accept
  → Atomic compare-and-swap: sets driverId on ride only if status = REQUESTED + driverId = null
  → If another driver won the race → throws 409
  → Generates 4-digit ride OTP (e.g. "4821")
  → Stores OTP in ride.rideStartOtp
  → Notifies customer via FCM: "Driver Found! Ride OTP: 4821"
  → Syncs to RTDB: rides/{rideId}/status = DRIVER_ASSIGNED
  → Cleans up Redis: removes offer keys for all drivers in this batch

  POST /driver/rides/:rideId/decline  { reason: "..." }
  → Removes driver from batch list in Redis
  → BullMQ handles moving to next batch when batch expires
```

### Step 5 — Execute the Ride

```
POST /driver/rides/:rideId/arrived
→ Validates status = DRIVER_ASSIGNED + driverId matches
→ Transitions: DRIVER_ASSIGNED → DRIVER_ARRIVED
→ Records driverArrivedAt timestamp
→ Notifies customer: "Driver has arrived"

POST /driver/rides/:rideId/start  { otp: "4821" }
→ Validates OTP matches ride.rideStartOtp
→ If wrong OTP → throws 400 "Invalid OTP"
→ Transitions: DRIVER_ARRIVED → IN_PROGRESS
→ Records startedAt timestamp
→ Clears rideStartOtp from DB (used, can't reuse)
→ Notifies customer: "Ride started"

POST /driver/rides/:rideId/complete  { note: "..." }
→ Validates status = IN_PROGRESS + driverId matches
→ ATOMIC TRANSACTION:
    1. Ride: status = COMPLETED, completedAt = now, finalFare locked
    2. Commission calculation:
       → COMMISSION plan: commissionAmount = finalFare × (commission_percentage / 100)
       → SUBSCRIPTION plan: commissionAmount = 0 (flat weekly fee instead)
    3. driverEarning = finalFare - commissionAmount
    4. Create Earning record:
       { grossAmount: finalFare, commissionAmount, netAmount: driverEarning,
         settlementStatus: PENDING, settlementDueDate: now + 2 days }
    5. Update DriverProfile: totalRides++, totalEarnings += driverEarning
    6. Update CustomerProfile: totalRides++
→ Notifies customer: "Ride completed — ₹55 charged"
→ Syncs to RTDB: rides/{rideId}/status = COMPLETED
```

### Step 6 — Rate the Customer

```
POST /driver/rides/:rideId/rate-customer  { rating: 1-5, comment: "..." }
→ Only for COMPLETED rides
→ One-time per ride (409 if already rated)
→ Stores driverRating + driverComment on ride
→ (Customer's rating average is NOT updated — driver ratings are for customer trust score V2)
```

### Step 7 — Earnings and Payouts

```
GET /driver/earnings/summary?period=week
→ Aggregate: totalRides, grossEarnings, commission, netEarnings, avgPerRide
→ All-time: allTimeRides, allTimeEarnings, ratingAvg

GET /driver/earnings?period=today|week|month&page=1&limit=20
→ Paginated per-trip breakdown

GET /driver/earnings/settlement
→ Amounts by status: PENDING / PROCESSING / SETTLED / FAILED

POST /driver/withdrawals
Body: { amount: 2000, method: "BANK_TRANSFER", upiId: "ravi@paytm" }
→ Creates withdrawal request (status = REQUESTED)
→ Admin processes manually (no auto-transfer in V1)

GET /driver/withdrawals/:withdrawalId
→ Check status of a specific withdrawal request
```

---

## 5. Ride Creation & Driver Search

This is the most complex part of the backend. Here's exactly what happens when a customer creates a ride:

```
POST /rides  (customer submits)
         │
         ▼
  1. Idempotency check (Redis) — same Idempotency-Key? Return cached response.
         │
         ▼
  2. Check no active ride for this customer (atomic Prisma transaction)
         │
         ▼
  3. Calculate fare (or re-use estimate)
         │
         ▼
  4. CREATE ride record:
     { status: REQUESTED, customerId, pickup, drop, fare, paymentMethod }
         │
         ▼ (non-blocking — returns ride to customer immediately)
  5. rideService.searchAndNotifyDrivers(rideId, pickup, fare)
         │
         ▼
  6. PostGIS spatial query:
     SELECT drivers within 5km radius of pickup WHERE isOnline=true AND verified
     ORDER BY distance ASC (PostGIS ST_DWithin on GIST index)
     LIMIT 10 candidates
         │
         ▼
  7. Score each driver (0.0–1.0):
     → distanceScore = 1 - (distanceKm / 5)        weight: 50%
     → ratingScore   = driver.ratingAvg / 5         weight: 30%
     → idleTimeScore = 0.5 (placeholder)            weight: 20%
     totalScore = (dist × 0.5) + (rating × 0.3) + (idle × 0.2)
         │
         ▼
  8. Store top-10 in Redis: ride:candidates:{rideId} (10 min TTL)
         │
         ▼
  9. Take first batch of 5 (top-scored)
         │
         ▼
 10. For each of the 5 drivers simultaneously:
     → Set Redis: ride:offer:{driverId} = rideId (65s TTL)
     → Send FCM: "New ride — ₹55 — 4.2km from Sector 16A"
         │
         ▼
 11. Set Redis: ride:active_batch:{rideId} = [userId1, ..., userId5]
         │
         ▼
 12. Schedule BullMQ delayed job: ride-offer-expired fires in 65s
         │
         ▼
     ─────────────── WAIT 60 SECONDS ───────────────
         │
         │ If a driver accepts within 60s:
         │   → Atomic CAS update (REQUESTED + driverId=null → DRIVER_ASSIGNED)
         │   → Clean up Redis offer keys for the other 4 drivers
         │   → BullMQ job fires but finds ride no longer REQUESTED → noop
         │
         │ If all 5 decline or 60s passes:
         ▼
 13. BullMQ fires: ride-offer-expired
         │
         ▼
 14. Check: is ride still REQUESTED? If not → stop.
         │
         ▼
 15. Read ride:candidates:{rideId} from Redis
     Slice next batch (indices 5–9)
         │
         ▼
 16. If candidates remain → dispatchBatch (steps 9–12 for next 5)
     If no candidates → UPDATE ride SET status = NO_DRIVER
                     → Notify customer: "No drivers available"
```

---

## 6. Ride Lifecycle State Machine

```
                    ┌─────────────┐
                    │  SCHEDULED  │ ← scheduled future rides
                    └──────┬──────┘
                           │ at scheduledAt time
                           ▼
         ┌─────────── REQUESTED ──────────────┐
         │    Customer just booked            │
         │    Searching for driver            │
         └─────┬──────────────────────────────┘
               │                              │
      Driver   │                    2min with │ no driver
      accepts  │                    no accept │
               ▼                              ▼
      DRIVER_ASSIGNED                     NO_DRIVER
      (driver en route)                  (terminal)
               │
               │ Driver arrives at pickup
               ▼
      DRIVER_ARRIVED
      (customer shows OTP)
               │
               │ Driver enters OTP + taps Start
               ▼
         IN_PROGRESS
         (ride underway)
               │
               │ Driver taps Complete
               ▼
          COMPLETED ──────────────────────────┐
          (terminal)                          │
                                              │ Customer or
         CANCELLED ◄──────────────────────────┘ Driver cancels
         (terminal)    before ride starts
```

**Who can cancel:**
- Customer: any status except IN_PROGRESS and COMPLETED
- Driver: only DRIVER_ASSIGNED or DRIVER_ARRIVED
- System: REQUESTED → NO_DRIVER after timeout

---

## 7. Fare Calculation

### Formula

```
Step 1: Get route details
  → Google Maps Directions API (real distance + time)
  → Fallback: Haversine distance × 1.2 (road correction factor), avgSpeed=30kmh

Step 2: Fetch config from Redis cache (5 min TTL)
  base_fare_per_km  = 12   (₹12/km)
  base_fare_per_min = 2    (₹2/min)
  booking_fee       = 5    (₹5 flat)
  min_fare          = 30   (₹30 minimum)

Step 3: Calculate base fare
  rawFare = (distanceKm × 12) + (durationMins × 2) + 5
  baseFare = max(rawFare, 30)

Step 4: Apply surge
  surgeMultiplier = 1.0 (no surge) to 2.0 (max)
  finalFare = round(baseFare × surgeMultiplier)

Step 5: Compute GST (internal accounting only — not added on top)
  gst_percentage = 5  (from platform_config, default 5%)
  gstAmount = round(finalFare × 5 / 100)
  → Stored in rides.gstAmount for tax accounting
  → NOT shown to customer or driver — the fare they see already includes it
  → NOT added on top of finalFare (baked in, not extra)

Step 6: Surge conditions (time-based, manual toggle)
  Morning peak (7–10am): 1.3x
  Evening peak (5–8pm): 1.5x
  Late night (10pm–5am): 1.3x
  → All overridden if surge_enabled=false in platform_config
  → Admin can set manual surge_multiplier via config endpoint
```

### Example Ride

```
Distance: 4.2km | Duration: 18 minutes | No surge

rawFare = (4.2 × 12) + (18 × 2) + 5
        = 50.40 + 36 + 5
        = 91.40

With surge 1.5x: 91.40 × 1.5 = ₹137.10

min_fare check: max(91.40, 30) = ₹91.40 ✓
```

### Commission Split

```
COMMISSION plan driver (default):
  commission_percentage = 15%
  commissionAmount = finalFare × 0.15 = ₹13.71
  driverEarning    = finalFare - commissionAmount = ₹77.69

SUBSCRIPTION plan driver:
  commissionAmount = 0
  driverEarning    = finalFare = ₹91.40
  (They paid ₹199/week flat to the platform instead)
```

---

## 8. Cancellation Fee

```
Customer cancels ride AFTER driver assignment:

Timeline check:
  driverAssignedAt = ride record (set when driver accepts)
  now = cancellation time
  elapsed = (now - driverAssignedAt) in seconds

  free_cancel_window_secs = 120 (from platform_config, default 2 minutes)
  cancel_fee_amount        = 20  (from platform_config, default ₹20)

  if elapsed <= 120s  → cancellationFee = 0   (free cancellation)
  if elapsed  > 120s  → cancellationFee = 20  (₹20 fee)

Applies to:
  → status = DRIVER_ASSIGNED: driver en route, not arrived yet
  → status = DRIVER_ARRIVED: driver at pickup, waiting

Does NOT apply to:
  → status = REQUESTED: no driver yet, always free
  → status = SCHEDULED: future rides, always free

Fee collection (V1):
  → Fee is returned in the API response
  → Cash rides: customer pays driver the full fare + fee amount
  → UPI rides: Razorpay charge (not yet implemented — V2)

Config admin commands:
  PUT /admin/config/free_cancel_window_secs  { "value": "180" }  → 3 min window
  PUT /admin/config/cancel_fee_amount        { "value": "30" }   → ₹30 fee
```

---

## 9. Payments

### Cash Rides (default, V1)

```
No backend processing needed.
→ Customer pays driver cash at end of ride.
→ Ride paymentStatus stays PENDING (no online payment).
→ Driver's earning is still recorded (netAmount = finalFare - commission).
```

### UPI / Razorpay (V1 skeleton, not production-ready)

```
Flow:
  1. POST /payments/order
     → Creates Razorpay order for the ride fare
     → Returns: { razorpayOrderId, amount, currency }

  2. Client completes payment on Razorpay SDK (outside our backend)

  3. POST /payments/verify
     → Verifies Razorpay signature (HMAC-SHA256)
     → Updates ride.paymentStatus = COMPLETED
     → Updates ride.razorpayPaymentId

  4. Razorpay sends webhook: POST /payments/webhook
     → Verifies webhook signature
     → Handles: payment.captured → mark ride paid
     → Handles: payment.failed → mark as failed

Circuit breaker on Razorpay:
  → opossum library wraps all Razorpay calls
  → If Razorpay fails 50% of calls → circuit OPENS
  → Fallback: throw "Payment service temporarily unavailable"
  → Resets after 30s
```

---

## 10. Driver Earnings & Settlement

```
When ride completes:
  1. Earning record created:
     { grossAmount, commissionAmount, netAmount,
       settlementStatus: PENDING,
       settlementDueDate: now + 2 days (T+2) }

  2. DriverProfile updated:
     totalRides++, totalEarnings += netAmount

Settlement flow (T+2 — manual in V1):
  Day 0: Ride completed → Earning.settlementStatus = PENDING
  Day 2: Admin reviews → changes to PROCESSING → SETTLED

  GET /driver/earnings/settlement
  → Returns: { pending: ₹1200, processing: ₹400, settled: ₹8500 }

Withdrawal:
  POST /driver/withdrawals  { amount, method, upiId | bankAccountNumber + bankIfsc }
  → Creates Withdrawal record (status = REQUESTED)
  → Admin manually processes (bank transfer or field agent cash delivery)
  → Admin updates status: REQUESTED → PROCESSING → COMPLETED
```

---

## 11. Admin Panel

All admin endpoints require `Authorization: Bearer <admin_firebase_token>` with ADMIN role.

### Driver Management

```
GET  /admin/drivers/pending              → List drivers awaiting review (FIFO)
GET  /admin/drivers/:driverId            → Full driver profile + docs + stats
POST /admin/drivers/:driverId/approve   { note: "Docs OK" }
POST /admin/drivers/:driverId/reject    { reason: "License unclear" }
POST /admin/drivers/:driverId/auto-verify
     → Calls KYC provider (Surepass or Manual)
     → confidence >= 0.85 → auto-approves
     → lower → leaves UNDER_REVIEW for human
```

### Live Monitoring

```
GET /admin/rides/live
→ All rides currently in DRIVER_ASSIGNED / DRIVER_ARRIVED / IN_PROGRESS
→ With driver + customer names, pickup/drop addresses, fare
→ Paginated (page, limit query params)
```

### Platform Config (all fees/rates are configurable here)

```
GET /admin/config
→ Returns all key-value config rows

PUT /admin/config/:key   { "value": "18" }
→ Updates a single config value
→ Clears fare:config Redis cache so next fare calc uses new value

Configurable keys:
  commission_percentage     → 15    (% per ride for COMMISSION drivers)
  subscription_fee_weekly   → 199   (₹199/week for SUBSCRIPTION drivers)
  min_fare                  → 30    (₹30 minimum fare)
  base_fare_per_km          → 12    (₹12/km)
  base_fare_per_min         → 2     (₹2/min)
  surge_enabled             → false (turn surge on/off)
  surge_multiplier          → 1.0   (manual surge override)
  settlement_days           → 2     (T+2 payout window)
  free_cancel_window_secs   → 120   (2-min free cancel window)
  cancel_fee_amount         → 20    (₹20 after free window)
```

### Bootstrap (no admin token needed — INTERNAL_API_KEY instead)

```
POST /admin/promote
Header: x-internal-api-key: chalo-internal-dev-key-change-in-prod
Body: { phone: "+91XXXXXXXXXX" }
→ Promotes any user to ADMIN
→ Idempotent — safe to call multiple times
→ User must re-login to get ADMIN token
```

---

## 12. Real-time (Firebase RTDB)

### Structure

```
Firebase Realtime Database:
  drivers/
    {userId}/
      isOnline: true
      lat: 28.4089
      lng: 77.3178
      heading: 45
      updatedAt: "2026-03-08T10:30:00.000Z"

  rides/
    {rideId}/
      status: "IN_PROGRESS"
      driverId: "clxxx..."
      driverLat: 28.4089
      driverLng: 77.3178
      updatedAt: "2026-03-08T10:30:00.000Z"
```

### Who writes what

```
Driver app writes:
  → POST /driver/location every 5s
    → Backend reads lat/lng
    → Updates driver_profiles in PostgreSQL
    → If active ride → syncs to RTDB: rides/{rideId}/driverLat + driverLng

Backend writes:
  → On ride status changes:
      acceptRide    → rides/{rideId}/status = "DRIVER_ASSIGNED"
      arrivedAtPickup → rides/{rideId}/status = "DRIVER_ARRIVED"
      startRide     → rides/{rideId}/status = "IN_PROGRESS"
      completeRide  → rides/{rideId}/status = "COMPLETED"
      cancelRide    → rides/{rideId}/status = "CANCELLED"
      noDriver      → rides/{rideId}/status = "NO_DRIVER"
```

### Customer reads

```
Customer app subscribes to:
  RTDB: rides/{rideId}/status          → live status updates
  RTDB: rides/{rideId}/driverLat + driverLng → live driver location on map

REST fallback (if RTDB connection drops on 2G/3G):
  GET /rides/:rideId/location → returns driverLat, driverLng from PostgreSQL
  → Poll every 5 seconds as fallback
```

---

## 13. Push Notifications (FCM)

Every notification is both sent via FCM AND stored in the `notifications` table.

### Customer notifications

| Trigger | Title | Body |
|---|---|---|
| Driver accepts | "Driver Found!" | "Your driver is on the way. Ride OTP: XXXX" |
| Driver arrives | "Driver Arrived" | "Your driver is waiting at the pickup point" |
| Ride starts | "Ride Started" | "Heading to [drop address]" |
| Ride completes | "Ride Completed" | "You've arrived. ₹55 charged." |
| Driver cancels | "Driver Cancelled" | "Your driver cancelled. Searching again." |
| No driver found | "No Drivers Available" | "Try again in a few minutes" |

### Driver notifications

| Trigger | Title | Body |
|---|---|---|
| New ride batch | "New Ride Request" | "₹55 — 4.2km — from Sector 16A" |
| Customer cancels | "Ride Cancelled" | "The customer has cancelled the ride" |
| Offer expired | (Redis TTL expires, no FCM) | Driver app sees offer gone |

### How FCM token registration works

```
PUT /auth/device-token  { fcmToken: "..." }
→ Stores in users.fcmToken + users.fcmTokenUpdatedAt

On send: if FCM returns "messaging/registration-token-not-registered"
  → Automatically clears the stale token from DB
  → Next login, app registers new token
```

---

## 14. SOS Safety System

```
Customer triggers SOS during active ride:
  POST /rides/:rideId/sos
  Body: { lat, lng, message: "optional" }

Backend:
  1. Validates ride is IN_PROGRESS and customer is participant
  2. Fetches emergency contact from customer_profiles
  3. Sends SMS via MSG91 to emergency contact:
     "SAFETY ALERT: [CustomerName] is in a ride from [pickup] to [drop].
      Location: lat,lng. Driver: [driverName] Vehicle: [vehicleNumber]"
  4. Creates SOSAlert record { status: ACTIVE, alertSentTo: [...] }
  5. Notifies operations team (SMS to admin number if configured)

Resolve SOS:
  POST /rides/sos/:sosAlertId/resolve
  → Sets status = RESOLVED
  → (Only admin or customer themselves can resolve)

Auto-resolve:
  → SOSAlert auto-resolves when ride completes (status = AUTO_RESOLVED)
```

---

## 15. Background Jobs (BullMQ)

Two queues, two workers.

### Queue 1: chalo-maintenance

```
Job: otp-cleanup
  → Runs every hour (or triggered manually)
  → Deletes from otp_verifications WHERE:
      expiresAt < now (expired)
      OR (verified = true AND createdAt < 24h ago)
  → Keeps DB clean
```

### Queue 2: chalo-rides

```
Job: ride-offer-expired
  → Scheduled when a driver batch is dispatched
  → Fires 65 seconds later (60s window + 5s grace)

  Logic:
    1. Fetch ride — if not REQUESTED → noop (already resolved)
    2. Read ride:candidates:{rideId} from Redis
    3. Compute nextBatchStart (e.g., 5, 10, ...)
    4. If no candidates at nextBatchStart → mark NO_DRIVER + notify customer
    5. If candidates remain → call dispatchBatch (next 5 drivers)
       → This reschedules another ride-offer-expired job for the new batch

  Result: fully automatic driver search with cascading batches until
          someone accepts or all candidates exhausted.
```

---

## 16. What's Still Left to Build

> Backend score: **8.5/10** (March 2026). The full ride lifecycle is implemented and tested.
> Everything below is either a P1 enhancement, a paid integration, or a post-launch growth feature.

---

### ✅ P0 — All Done

All original P0 critical features are implemented and tested:

| Feature | Status |
|---|---|
| OTP-based ride start (driver enters 4-digit OTP) | ✅ Done |
| Customer rates driver (`POST /rides/:rideId/rate`) | ✅ Done |
| Driver rates customer (`POST /driver/rides/:rideId/rate-customer`) | ✅ Done |
| Cancellation fee logic (time-based, platform_config-driven) | ✅ Done |
| Driver registration endpoint with atomic DriverProfile creation | ✅ Done |
| Admin promote endpoint (INTERNAL_API_KEY, no SQL needed) | ✅ Done |
| Ride receipt endpoint (`GET /rides/:rideId/receipt`) | ✅ Done |
| Driver earnings summary (`GET /driver/earnings/summary?period=week\|month`) | ✅ Done |
| GST (5%) on ride fare — stored in `rides.gstAmount`, internal accounting only | ✅ Done |
| Rating window skip endpoint (`POST /rides/:rideId/skip-rating`) + `rides.ratingSkippedAt` field | ✅ Done |
| Trip share endpoint (`POST /rides/:rideId/share`) + public tracking endpoint (`GET /track/:token`) | ✅ Done |
| Driver cancellation tracking (`driverCancellationCount*`) + admin threshold alert at 3/day | ✅ Done |

---

### 🟠 P1 — Build These Before or During Android Development

Small to medium items. All decision context documented below.

| # | Feature | What to build | Effort |
|---|---|---|---|
| 1 | **Notification badge dot** | No dedicated count endpoint needed for V1. App calls `GET /notifications?limit=1` and checks if top item is unread. When promo notifications launch (P2), add `GET /notifications/unread-count`. | — |
| 2 | **SMS receipt** | Decision: no SMS receipts. All receipts available in-app via `GET /rides/:rideId/receipt`. MSG91 costs ₹0.18–0.22/SMS — not worth it for info already in the app. | — |
| 3 | **Cancellation fee charging** | Decision: information-only for V1. Fee shown to customer before confirming cancel. Driver notified to collect cash. V2: deduct from wallet when wallet is built. Auto-charging without saved payment method (UPI mandate) is not supported by RBI regulations. | — |

---

### 🟡 P2 — Growth Features (post-launch, based on usage)

| Feature | What it is | New DB tables |
|---|---|---|
| **Promo codes** | `POST /rides` accepts `promoCode`, fare discount applied before charge. Admin creates/expires codes. | `promotions`, `promo_usages` |
| **Referral system** | Register with referral code → ₹50 credit. Referrer gets credit when referred completes first ride. | `referrals` |
| **Surge automation** | BullMQ job every 5 min: if `activeRideRequests / onlineDrivers > 2.0` → auto-set surge. Currently manual via admin config. | None |
| **Driver incentive bonuses** | "Complete 10 rides before 10am → earn ₹200 extra." Admin configures targets. BullMQ midnight job evaluates. | `incentives`, `driver_incentive_progress` |
| **Customer wallet** | In-app balance: Razorpay topup, pay-from-wallet on ride creation, receive referral credit. | `wallets`, `wallet_transactions` |
| **Subscription renewal** | SUBSCRIPTION drivers pay ₹199/week. Auto-charge via Razorpay, FCM reminder 24h before expiry. | None — `subscriptionExpiresAt` exists |

---

### 🔵 P3 — Operational / Scale (needed at 1,000+ daily rides)

| Feature | What it is |
|---|---|
| **Admin analytics endpoints** | `GET /admin/analytics/rides`, `/revenue`, `/drivers`, `/customers` — aggregated daily/weekly stats |
| **Driver suspension system** | `POST /admin/drivers/:id/suspend` with duration + reason. Strike counter (3 strikes → auto-suspend 7 days). |
| **Dispute resolution** | Customer files dispute on a ride (FARE_ISSUE, WRONG_ROUTE, DRIVER_BEHAVIOUR). Admin resolves with optional refund. New `disputes` table. |
| **Document expiry alerts** | Add `licenseExpiry`, `rcExpiry` to `driver_profiles`. BullMQ daily cron: warn 7 days before, auto-suspend on expiry date. |
| **Auto-settlement (T+2)** | BullMQ daily job: find Earnings with `settlementDueDate <= today` → trigger Razorpay Payout → mark SETTLED. Currently manual. |
| **Scheduled ride dispatch** | BullMQ job every minute: find `SCHEDULED` rides due in 15 min → trigger normal driver search. Field `scheduledFor` already exists on Ride. |
| **Driver bank verification** | Validate IFSC + account number via Razorpay Route API before first payout to prevent failed transfers. |

---

### 🔧 Known Shortcuts in Current Code

These work fine for V1 but should be replaced before scaling:

| File | What it does now | What it should do |
|---|---|---|
| `services/fare.service.ts` | `// TODO V2: demand/supply surge calc` | Query `onlineDrivers / activeRequests` ratio, auto-set surge |
| `services/driver.service.ts` | `idleTimeScore = 0.5` hardcoded | Calculate actual idle time since driver's last completed ride |
| `services/ride.service.ts` → `cancelRide` | Returns `cancellationFee` in response only | Trigger Razorpay charge for UPI rides |
| `services/driver.service.ts` → `rateCustomer` | Saves `driverRating` on ride row only | Roll up into `CustomerProfile.ratingAvg` (like driver ratings) |
| `prisma/schema.prisma` → `Ride.driverRating` | Stored per-ride | Should aggregate into `CustomerProfile.ratingAvg` |

---

### 📋 One-Time Setup Still Pending

```bash
# 1. Seed platform config (fares, commission %, cancellation window, GST)
cd chalo-backend && npm run db:seed

# 2. Apply any pending migrations
npx prisma migrate deploy
```

---

## Appendix — All 58 Endpoints

### Auth (8 endpoints)
```
POST /auth/otp/send
POST /auth/otp/verify
POST /auth/register-driver     ← NEW
GET  /auth/profile
PUT  /auth/profile
PUT  /auth/emergency-contact
PUT  /auth/saved-location
PUT  /auth/device-token
```

### Rides (14 endpoints)
```
POST /rides/fare-estimate
POST /rides
POST /rides/schedule
GET  /rides/history
GET  /rides/scheduled
GET  /rides/:rideId/receipt    ← NEW
GET  /rides/:rideId
GET  /rides/:rideId/location
POST /rides/:rideId/cancel
POST /rides/:rideId/share      ← NEW
POST /rides/:rideId/skip-rating
POST /rides/:rideId/rate
POST /rides/:rideId/sos
POST /rides/sos/:sosAlertId/resolve
```

### Driver (19 endpoints)
```
POST /driver/documents
POST /driver/go-online
POST /driver/go-offline
GET  /driver/status
POST /driver/location
GET  /driver/rides/incoming
POST /driver/rides/:rideId/accept
POST /driver/rides/:rideId/decline
POST /driver/rides/:rideId/arrived
POST /driver/rides/:rideId/start          ← UPDATED (now needs OTP)
POST /driver/rides/:rideId/complete
POST /driver/rides/:rideId/cancel
POST /driver/rides/:rideId/rate-customer  ← NEW
GET  /driver/trips
GET  /driver/earnings/summary             ← NEW
GET  /driver/earnings/settlement
GET  /driver/earnings
POST /driver/withdrawals
GET  /driver/withdrawals/:withdrawalId
```

### Payments (3 endpoints)
```
POST /payments/order
POST /payments/verify
POST /payments/webhook
```

### Notifications (4 endpoints)
```
GET  /notifications
GET  /notifications/unread-count
PATCH /notifications/:notificationId/read
PATCH /notifications/read-all
```

### Admin (9 endpoints)
```
POST /admin/promote                        ← NEW (INTERNAL_API_KEY)
GET  /admin/drivers/pending
GET  /admin/drivers/:driverId
POST /admin/drivers/:driverId/approve
POST /admin/drivers/:driverId/reject
POST /admin/drivers/:driverId/auto-verify
GET  /admin/rides/live
GET  /admin/config
PUT  /admin/config/:key
```

### Public Tracking (1 endpoint)
```
GET  /track/:token                        ← NEW
```
