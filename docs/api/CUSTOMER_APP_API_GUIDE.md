# Chalo — Customer App API Guide

This guide documents every API call the Android customer app makes, in the exact order it makes them. Use it to understand the mobile data flow, debug app issues in Postman, or verify backend behaviour before testing on device.

**Base URL (local dev):** `http://localhost:3001/api/v1`
**Auth:** All protected endpoints require `Authorization: Bearer <Firebase ID Token>`

---

## How the App Gets a Token

The app does NOT use Postman-style username/password auth. It uses Firebase custom tokens:

1. App calls `POST /auth/otp/send`
2. App calls `POST /auth/otp/verify` → backend returns a **Firebase custom token**
3. App calls `FirebaseAuth.signInWithCustomToken(token)` internally
4. Firebase SDK issues an **ID token** (valid 1 hour)
5. Every subsequent API call sends `Authorization: Bearer <ID token>`

**To get a token for Postman testing:**
See [token-exchange-guide.md](token-exchange-guide.md) — use the Firebase REST API to exchange a custom token for an ID token. Or use the POSTMAN_GUIDE.md Collection which handles this automatically.

---

## Screen-by-Screen API Calls

### 1. Phone Entry Screen (`PhoneEntryScreen`)

**What happens:** User types phone number, taps "Send OTP".

```
POST /auth/otp/send
Body: { "phone": "+919876543210" }

Success response:
{
  "success": true,
  "data": { "message": "OTP sent successfully", "expiresIn": 300 }
}

Error responses:
429 — Too many OTP requests (3 OTPs per phone per 15 minutes)
400 — Invalid phone format (must be +91XXXXXXXXXX)
```

**App behaviour:** Navigates to OtpVerifyScreen, starts 60-second resend countdown.

---

### 2. OTP Verify Screen (`OtpVerifyScreen`)

**What happens:** User enters 6-digit OTP (auto-filled via SMS Retriever if SMS arrives).

```
POST /auth/otp/verify
Body: { "phone": "+919876543210", "otp": "123456" }

Success response:
{
  "success": true,
  "data": {
    "isNewUser": true,
    "token": "eyJhbGciOiJSUzI1...",   ← Firebase custom token
    "user": {
      "id": "clx...",
      "phone": "+919876543210",
      "name": null,
      "role": "CUSTOMER"
    }
  }
}

Error responses:
400 — Invalid OTP / OTP expired
429 — Max OTP attempts exceeded (3 attempts per OTP record)
```

**App behaviour after success:**
1. Calls `FirebaseAuth.signInWithCustomToken(token)` — get ID token from Firebase
2. Saves `userId`, `userPhone`, `profileComplete=false` in DataStore
3. If `isNewUser=true` → navigate to ProfileSetupScreen
4. If `isNewUser=false` → navigate to HomeScreen

**Resend OTP (same endpoint):**
```
POST /auth/otp/send
Body: { "phone": "+919876543210" }
```
Resend button enabled after 60-second countdown.

---

### 3. Profile Setup Screen (`ProfileSetupScreen`)

**What happens:** New user enters name (and optional email). Only shown on first login.

```
PUT /auth/profile
Authorization: Bearer <token>
Body: {
  "name": "Priya Sharma",
  "email": "priya@example.com",   ← optional
  "languagePref": "pa"            ← "pa" = Punjabi (default), "en" = English
}

Success response:
{
  "success": true,
  "data": {
    "id": "clx...",
    "phone": "+919876543210",
    "name": "Priya Sharma",
    "email": "priya@example.com",
    "role": "CUSTOMER",
    "languagePref": "pa"
  }
}

Error responses:
400 — Name too short (< 2 chars) or too long (> 100 chars)
401 — Token expired or missing
```

**App behaviour:** Saves `userName`, `profileComplete=true` in DataStore, navigates to HomeScreen.

---

### 4. Home Screen (`HomeScreen`)

**What happens on load:** Check for existing active ride so returning users resume their ride.

```
GET /rides/history?page=1&limit=1
Authorization: Bearer <token>

Note: The app checks for any in-progress ride by looking at the most
recent ride's status. If DRIVER_ASSIGNED / DRIVER_ARRIVED / IN_PROGRESS,
navigate directly to ActiveRideScreen.
```

**What happens when user taps "Where to?" search bar:**
Places autocomplete calls fire as user types — these hit Google Places API directly from the Android client (not through the backend). The app uses `Places.createClient(context).findAutocompletePredictions()`.

---

### 5. Fare Estimate Screen (`FareEstimateScreen`)

**What happens:** User selected a destination. App shows fare breakdown before booking.

```
POST /rides/fare-estimate
Authorization: Bearer <token>
Body: {
  "pickup": {
    "lat": 30.9010,
    "lng": 75.8573,
    "address": "Sadar Bazaar, Ludhiana"
  },
  "drop": {
    "lat": 31.3260,
    "lng": 75.5762,
    "address": "Civil Lines, Jalandhar"
  }
}

Success response:
{
  "success": true,
  "data": {
    "baseFare": 85,
    "distanceFare": 72,
    "timeFare": 8,
    "surgeMultiplier": 1.3,
    "surgeAmount": 25,
    "totalFare": 110,
    "distanceKm": 6.2,
    "durationMins": 18,
    "currency": "INR",
    "gstAmount": 5,
    "minimumFareApplied": false,
    "minimumFare": 30,
    "routePolyline": "yzlmC_ahrM..."   ← Google encoded polyline (may be empty if Maps API unavailable)
  }
}

Surge note: surgeMultiplier > 1.0 means dynamic or time-based surge is active.
GST note: gstAmount is included in totalFare — not added on top.

Error responses:
400 — Pickup outside Punjab service area (lat/lng bounds check)
400 — Invalid coordinates
```

**App behaviour:** Displays fare breakdown card, payment method selector (CASH / UPI). User taps "Book".

---

### 6. Booking a Ride (`FareEstimateScreen → onBookRide`)

**What happens:** User taps "Book Now".

```
POST /rides
Authorization: Bearer <token>
Idempotency-Key: <UUID>    ← Android generates random UUID per booking attempt
Body: {
  "pickup": {
    "lat": 30.9010,
    "lng": 75.8573,
    "address": "Sadar Bazaar, Ludhiana"
  },
  "drop": {
    "lat": 31.3260,
    "lng": 75.5762,
    "address": "Civil Lines, Jalandhar"
  },
  "paymentMethod": "CASH"   ← or "UPI"
}

Success response:
{
  "success": true,
  "data": {
    "rideId": "clx...",
    "status": "REQUESTED",
    "fare": {
      "baseFare": 85,
      "totalFare": 110,
      "surgeMultiplier": 1.3,
      "distanceKm": 6.2,
      "durationMins": 18,
      "currency": "INR"
    },
    "pickup": { "lat": 30.9010, "lng": 75.8573, "address": "Sadar Bazaar, Ludhiana" },
    "drop":   { "lat": 31.3260, "lng": 75.5762, "address": "Civil Lines, Jalandhar" },
    "paymentMethod": "CASH"
  }
}

Error responses:
409 — Customer already has an active ride (RIDE_ALREADY_ACTIVE)
400 — Pickup outside Punjab service area
429 — Rate limit exceeded
```

**Idempotency:** If the same `Idempotency-Key` is sent twice (network retry), the second call returns the same response without creating a duplicate ride.

**App behaviour:** Navigates to ActiveRideScreen with `rideId`.

---

### 7. Active Ride Screen (`ActiveRideScreen`)

This screen uses **two data sources simultaneously**:

#### 7a. REST — Initial ride details

```
GET /rides/:rideId
Authorization: Bearer <token>

Success response:
{
  "success": true,
  "data": {
    "id": "clx...",
    "status": "DRIVER_ASSIGNED",
    "pickupLat": 30.9010,
    "pickupLng": 75.8573,
    "pickupAddress": "Sadar Bazaar, Ludhiana",
    "dropLat": 31.3260,
    "dropLng": 75.5762,
    "dropAddress": "Civil Lines, Jalandhar",
    "distanceKm": 6.2,
    "durationMins": 18,
    "finalFare": 110,
    "paymentMethod": "CASH",
    "paymentStatus": "PENDING",
    "rideStartOtp": "4821",        ← 4-digit OTP customer shows to driver to start ride
    "driver": {
      "id": "clx...",
      "name": "Ramesh Kumar",
      "phone": "+919812345678",
      "driverProfile": {
        "vehicleNumber": "PB10AB1234",
        "vehicleModel": "Honda Activa",
        "ratingAvg": 4.3,
        "bikePhotoUrl": "https://..."
      }
    }
  }
}
```

#### 7b. Firebase RTDB — Real-time updates (no REST call)

The app observes two RTDB paths directly via Firebase SDK:

```
/rides/{rideId}/status          → string: "REQUESTED" | "DRIVER_ASSIGNED" | "DRIVER_ARRIVED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED"
/rides/{rideId}/driver_location → { lat: number, lng: number }
```

**When status changes to COMPLETED:**
- Payment method = CASH → navigate to RatingScreen
- Payment method = UPI + paymentStatus = PENDING → navigate to PaymentScreen

**When status changes to CANCELLED or NO_DRIVER:**
- Navigate back to HomeScreen

#### 7c. ETA calculation (client-side)

ETA is calculated in `ActiveRideViewModel.calculateEtaMins()` using Haversine distance from driver's current RTDB location to pickup point ÷ 20 km/h. Displayed as "ETA: X min" on the status card.

---

### 8. Cancel Ride (`CancelRideSheet`)

**What happens:** User taps Cancel, selects reason from bottom sheet.

```
DELETE /rides/:rideId/cancel
Authorization: Bearer <token>
Body: {
  "cancellationReasonCode": "DRIVER_NOT_MOVING",
  "cancellationReason": null    ← optional free-text (used with OTHER code only)
}

Cancellation reason codes:
  DRIVER_ASKED_TO_CANCEL  — fee waived (driver fault)
  DRIVER_NOT_MOVING       — fee waived (driver fault)
  DRIVER_WRONG_VEHICLE    — fee waived (driver fault)
  DRIVER_BEHAVIOUR        — fee waived (driver fault)
  CHANGED_MIND            — fee applies
  BOOKED_BY_MISTAKE       — fee applies
  OTHER                   — fee applies (free text allowed)

Cancellation fee rules:
  • Within 120s of driver assignment  → no fee (free window)
  • After 120s, before driver arrived  → ₹20 fee
  • After driver arrived              → ₹40 fee
  • Driver-fault reason codes         → fee always waived

Success response:
{
  "success": true,
  "data": {
    "rideId": "clx...",
    "status": "CANCELLED",
    "cancellationFee": 20,       ← 0 if waived
    "warning": null              ← "You have cancelled 3 rides in the last hour..." if serial canceller
  }
}

Error responses:
404 — Ride not found or not yours
409 — Ride already completed or cancelled
```

---

### 9. Share Ride (`ActiveRideScreen → Share button`)

```
POST /rides/:rideId/share
Authorization: Bearer <token>

Success response:
{
  "success": true,
  "data": {
    "shareUrl": "http://localhost:3001/api/v1/track/abc123xyz",
    "expiresAt": "2026-03-22T14:30:00.000Z"
  }
}
```

**App behaviour:** Opens Android share sheet with "Track my Chalo ride: {url}".
The share URL is public (no auth required) — family members can open it in a browser to see the ride on a map.

---

### 10. SOS Alert (`SosConfirmDialog`)

**What happens:** User presses SOS button → confirmation dialog → confirms.
`ActiveRideViewModel.getCurrentLocation()` fetches the real GPS coordinates via `FusedLocationProviderClient` before sending.

```
POST /rides/:rideId/sos
Authorization: Bearer <token>
Body: {
  "lat": 30.9010,
  "lng": 75.8573
}

Success response:
{
  "success": true,
  "data": {
    "alertId": "clx...",
    "message": "SOS alert sent to 1 contacts",
    "sentTo": [
      { "name": "Mom", "phone": "+919812345678", "channel": "SMS" }
    ]
  }
}

Error responses:
400 — No emergency contact set (set one in profile first)
404 — Ride not found
```

**SMS content sent to emergency contact:**
`EMERGENCY: Priya Sharma triggered an SOS during a ride. Driver: Ramesh Kumar (PB10AB1234). Live location: https://maps.google.com/?q=30.9010,75.8573. Time: 22/03/2026, 2:30:00 PM. Please contact them immediately.`

---

### 11. UPI Payment Screen (`PaymentScreen`)

Shown only when ride completes with `paymentMethod = UPI`.

#### Step 1 — Create Razorpay order

```
POST /payment/order
Authorization: Bearer <token>
Idempotency-Key: <UUID>
Body: { "rideId": "clx..." }

Success response:
{
  "success": true,
  "data": {
    "orderId": "order_ABC123",
    "amount": 11000,        ← in paise (₹110 × 100)
    "currency": "INR",
    "keyId": "rzp_test_..."
  }
}
```

#### Step 2 — Open Razorpay checkout (Android SDK)

The app opens Razorpay's checkout UI. User pays → Razorpay returns:
- `razorpay_payment_id`
- `razorpay_order_id`
- `razorpay_signature`

#### Step 3 — Verify payment

```
POST /payment/verify
Authorization: Bearer <token>
Body: {
  "razorpayPaymentId": "pay_ABC123",
  "razorpayOrderId": "order_ABC123",
  "razorpaySignature": "sha256_hash",
  "rideId": "clx..."
}

Success response:
{
  "success": true,
  "data": { "message": "Payment verified successfully" }
}

Error responses:
400 — Signature verification failed (PAYMENT_SIGNATURE_INVALID)
409 — Payment already processed for this ride
```

**App behaviour:** On success, navigate to RatingScreen.

---

### 12. Rating Screen (`RatingScreen`)

**What happens:** Customer rates the driver 1–5 stars after ride completion.

```
POST /rides/:rideId/rate
Authorization: Bearer <token>
Body: {
  "rating": 4,
  "comment": "Good driver, arrived on time"   ← optional
}

Success response:
{
  "success": true,
  "data": { "message": "Rating submitted" }
}

Error responses:
400 — Rating must be 1–5
409 — Already rated this ride
404 — Ride not found
```

**Skip rating:**
```
POST /rides/:rideId/skip-rating
Authorization: Bearer <token>

Success response:
{
  "success": true,
  "data": { "message": "Rating skipped" }
}
```

After rating or skip → navigate to HomeScreen. DataStore clears `pendingRatingRideId`.

**Pending rating persistence:** If the app is closed before rating, DataStore stores `pendingRatingRideId`. On next app open, `HomeViewModel` checks this key and shows the rating screen first.

---

### 13. Ride Receipt Screen (`ReceiptScreen`)

```
GET /rides/:rideId/receipt
Authorization: Bearer <token>

Success response:
{
  "success": true,
  "data": {
    "rideId": "clx...",
    "pickupAddress": "Sadar Bazaar, Ludhiana",
    "dropAddress": "Civil Lines, Jalandhar",
    "distanceKm": 6.2,
    "durationMins": 18,
    "baseFare": 85,
    "surgeMultiplier": 1.3,
    "surgeAmount": 25,
    "totalFare": 110,
    "gstAmount": 5,
    "paymentMethod": "CASH",
    "paymentStatus": "COMPLETED",
    "completedAt": "2026-03-22T14:28:00.000Z",
    "driver": {
      "name": "Ramesh Kumar",
      "vehicleNumber": "PB10AB1234"
    }
  }
}
```

---

### 14. Ride History Screen (`HistoryScreen`)

```
GET /rides/history?page=1&limit=10
Authorization: Bearer <token>

Success response:
{
  "success": true,
  "data": {
    "rides": [
      {
        "id": "clx...",
        "pickupAddress": "Sadar Bazaar, Ludhiana",
        "dropAddress": "Civil Lines, Jalandhar",
        "finalFare": 110,
        "status": "COMPLETED",
        "paymentMethod": "CASH",
        "distanceKm": 6.2,
        "customerRating": 4,
        "createdAt": "2026-03-22T14:00:00.000Z",
        "completedAt": "2026-03-22T14:28:00.000Z",
        "driver": {
          "name": "Ramesh Kumar",
          "driverProfile": { "vehicleNumber": "PB10AB1234" }
        }
      }
    ],
    "pagination": {
      "page": 1,
      "limit": 10,
      "total": 3,
      "totalPages": 1
    }
  }
}
```

Completed, cancelled, and no-driver rides all appear in history.

---

### 15. Scheduled Ride Screen (`ScheduleRideScreen`)

```
POST /rides/schedule
Authorization: Bearer <token>
Idempotency-Key: <UUID>
Body: {
  "pickup": { "lat": 30.9010, "lng": 75.8573, "address": "Sadar Bazaar, Ludhiana" },
  "drop":   { "lat": 31.3260, "lng": 75.8573, "address": "Civil Lines, Jalandhar" },
  "paymentMethod": "CASH",
  "scheduledAt": "2026-03-23T07:30:00.000Z"   ← ISO 8601, min 1 min ahead, max 7 days ahead
}

Success response:
{
  "success": true,
  "data": {
    "rideId": "clx...",
    "status": "SCHEDULED",
    "scheduledAt": "2026-03-23T07:30:00.000Z",
    "estimatedFare": { "baseFare": 85, "totalFare": 85, "surgeMultiplier": 1.0, ... }
  }
}

Note: Scheduled rides use surgeMultiplier=1.0 at booking time.
Surge is recalculated when the ride dispatches (15 minutes before scheduledAt).
```

**View scheduled rides:**
```
GET /rides/scheduled
Authorization: Bearer <token>

Returns all SCHEDULED rides for this customer.
```

---

### 16. Notifications Screen (`NotificationsScreen`)

```
GET /notifications?page=1&limit=20
Authorization: Bearer <token>

Success response:
{
  "success": true,
  "data": {
    "notifications": [
      {
        "id": "clx...",
        "type": "RIDE_STATUS",
        "title": "Driver has arrived!",
        "body": "Ramesh Kumar is waiting at your pickup point.",
        "data": { "rideId": "clx...", "type": "DRIVER_ARRIVED" },
        "isRead": false,
        "sentAt": "2026-03-22T14:20:00.000Z"
      }
    ],
    "pagination": { ... }
  }
}
```

**Unread count (shown as badge on bottom nav):**
```
GET /notifications/unread-count
Authorization: Bearer <token>

Response: { "success": true, "data": { "unreadCount": 3 } }
```

**Mark one read:**
```
PATCH /notifications/:notificationId/read
Authorization: Bearer <token>
```

**Mark all read:**
```
PATCH /notifications/mark-all-read
Authorization: Bearer <token>
```

---

### 17. Profile Screen (`ProfileScreen`)

```
GET /auth/profile
Authorization: Bearer <token>

Success response:
{
  "success": true,
  "data": {
    "id": "clx...",
    "phone": "+919876543210",
    "name": "Priya Sharma",
    "email": "priya@example.com",
    "role": "CUSTOMER",
    "languagePref": "pa",
    "emergencyContact": {
      "name": "Mom",
      "phone": "+919812345678"
    },
    "savedLocations": {
      "home": { "lat": 30.9010, "lng": 75.8573, "address": "Sadar Bazaar, Ludhiana" },
      "work": null
    },
    "createdAt": "2026-03-01T10:00:00.000Z"
  }
}
```

**Update emergency contact:**
```
PUT /auth/emergency-contact
Authorization: Bearer <token>
Body: {
  "emergencyContactName": "Mom",
  "emergencyContactPhone": "+919812345678"
}
```

**Save home/work location:**
```
PUT /auth/saved-location
Authorization: Bearer <token>
Body: {
  "type": "home",
  "lat": 30.9010,
  "lng": 75.8573,
  "address": "Sadar Bazaar, Ludhiana"
}
```

---

## Device Token Registration (App Startup)

When the app starts and Firebase Messaging provides an FCM token (or token refreshes), the app sends it to the backend. This enables push notifications.

```
POST /auth/device-token
Authorization: Bearer <token>
Body: { "fcmToken": "dJYkr8..." }

Success response:
{ "success": true, "data": { "message": "Device token registered" } }
```

This is called from `MainActivity.onCreate()` via `FirebaseMessaging.getInstance().token.await()`.

---

## FCM Push Notification Payloads

The app receives these FCM data payloads and handles them in `MyFirebaseMessagingService`:

| Event | `type` field | Additional fields |
|---|---|---|
| Driver assigned | `DRIVER_ASSIGNED` | `rideId`, `driverName`, `vehicleNumber` |
| Driver arrived | `DRIVER_ARRIVED` | `rideId` |
| Ride started | `RIDE_STARTED` | `rideId` |
| Ride completed | `RIDE_COMPLETED` | `rideId` |
| No driver found | `NO_DRIVER` | `rideId` |
| Payment required | `PAYMENT_REQUIRED` | `rideId`, `amount` |
| Ride cancelled | `RIDE_CANCELLED` | `rideId`, `cancelledBy` |
| SOS confirmation | `SOS_TRIGGERED` | `alertId` |

All FCM notifications deep-link to the relevant screen when tapped.

---

## Common Error Response Format

All API errors follow this structure:

```json
{
  "success": false,
  "message": "Human-readable error message",
  "code": "ERROR_CODE",
  "statusCode": 400
}
```

| HTTP Status | When |
|---|---|
| 400 | Bad request — validation failed, business rule violated |
| 401 | Token missing, expired, or invalid |
| 403 | Authenticated but wrong role |
| 404 | Resource not found |
| 409 | Conflict — duplicate booking, already rated, etc. |
| 429 | Rate limit exceeded — retry after `Retry-After` header seconds |
| 500 | Server error — check backend logs |

---

## End-to-End Test Sequence (Postman / curl)

Run these in order to simulate a complete customer journey from scratch:

```
1.  POST /auth/otp/send          { "phone": "+919876543210" }
2.  [Check backend logs for OTP in dev mode]
3.  POST /auth/otp/verify        { "phone": "+919876543210", "otp": "<from logs>" }
4.  [Exchange custom token for ID token — see token-exchange-guide.md]
5.  PUT  /auth/profile           { "name": "Test User", "languagePref": "en" }
6.  POST /auth/device-token      { "fcmToken": "test-token-123" }
7.  POST /rides/fare-estimate    { pickup: Sector15, drop: BataChowk }
8.  POST /rides                  { pickup, drop, paymentMethod: "CASH" }
9.  [Note the rideId from response]
10. GET  /rides/:rideId          [Check status — should be REQUESTED]
11. [Watch RTDB at /rides/:rideId/status for changes as driver accepts]
12. DELETE /rides/:rideId/cancel { "cancellationReasonCode": "BOOKED_BY_MISTAKE" }
    — OR —
12. [Wait for COMPLETED status, then:]
13. POST /rides/:rideId/rate     { "rating": 5, "comment": "Great ride!" }
14. GET  /rides/history          [Verify ride appears in history]
15. GET  /notifications          [Verify notifications were created]
```
