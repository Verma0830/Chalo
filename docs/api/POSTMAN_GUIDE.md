# Chalo API — Complete Postman Testing Guide

Last updated: 2026-03-21

This guide walks through every API endpoint from scratch. Follow section order — later sections depend on tokens and IDs captured in earlier ones.

---

## 1. Setup

### 1.1 Base URL

```
http://localhost:3001/api/v1
```

Start the server first: `cd chalo-backend && npm run dev`

### 1.2 Create a Postman Environment

Name it **Chalo Local**. Add these variables (leave values blank for now — they get filled during testing):

| Variable | Initial Value | Purpose |
|---|---|---|
| `base_url` | `http://localhost:3001/api/v1` | Base URL for all requests |
| `firebase_web_api_key` | *(your Firebase Web API Key)* | Token exchange |
| `customer_token` | | Firebase ID token for customer |
| `driver_token` | | Firebase ID token for driver |
| `admin_token` | | Firebase ID token for admin |
| `internal_api_key` | `chalo-internal-dev-key-change-in-prod` | Admin promote endpoint |
| `ride_id` | | Captured after ride creation |
| `scheduled_ride_id` | | Captured after scheduled ride creation |
| `share_token` | | Captured after share link creation |
| `sos_alert_id` | | Captured after SOS trigger |
| `withdrawal_id` | | Captured after withdrawal request |
| `notification_id` | | Captured from notifications list |

### 1.3 Default Headers (Collection-level)

Set these on the collection so every request inherits them:

```
Content-Type: application/json
Authorization: Bearer {{customer_token}}
```

Override `Authorization` per-request when using driver or admin tokens.

---

## 2. How Auth Works (Read This First)

The backend uses **Firebase ID tokens**, not the raw custom token from OTP verify.

**Flow:**

```
POST /auth/otp/send  →  OTP delivered (Firebase or console in dev)
POST /auth/otp/verify  →  { customToken: "..." }
POST Firebase Identity Toolkit  →  { idToken: "..." }   <-- this is your Bearer token
```

**Token exchange request** (do this after every OTP verify):

```
POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key={{firebase_web_api_key}}

Body:
{
  "token": "<customToken from otp/verify response>",
  "returnSecureToken": true
}
```

Copy `idToken` from the response → paste into `customer_token` (or `driver_token` / `admin_token`) in your environment.

ID tokens expire in ~1 hour. If you get `401`, re-do OTP verify + token exchange and update the environment variable.

---

## 3. Health Check

Verify the server is up before anything else.

```
GET http://localhost:3001/health
```

No auth required. Expected response:

```json
{ "status": "ok" }
```

---

## 4. Journey 1: OTP Auth Flow (Customer)

This covers the complete sign-up / sign-in flow for a customer.

### Step 1 — Send OTP

```
POST {{base_url}}/auth/otp/send
No auth required

Body:
{
  "phone": "+919876543210"
}
```

- Phone must match `+91[6-9]XXXXXXXXX` format.
- Rate limited: 5 requests per 15 minutes per IP.
- In dev mode the OTP is printed to the backend console — watch `npm run dev` output.

Expected: `200 { message: "OTP sent" }`

### Step 2 — Verify OTP

```
POST {{base_url}}/auth/otp/verify
No auth required

Body:
{
  "phone": "+919876543210",
  "otp": "1234"
}
```

- Use the 4-digit OTP from the console.
- OTP is exactly 4 digits, numeric only.

Expected response:

```json
{
  "customToken": "eyJhbGci...",
  "isNewUser": true,
  "role": "CUSTOMER"
}
```

Copy `customToken`.

### Step 3 — Exchange for Firebase ID Token

```
POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key={{firebase_web_api_key}}

Body:
{
  "token": "<paste customToken here>",
  "returnSecureToken": true
}
```

Copy `idToken` from response → set as `customer_token` in environment.

### Step 4 — Complete Profile (first-time user only)

```
PUT {{base_url}}/auth/profile
Authorization: Bearer {{customer_token}}

Body:
{
  "name": "Rahul Sharma",
  "email": "rahul@example.com",
  "languagePref": "hi"
}
```

- `email` is optional.
- `languagePref`: `"pa"` (Punjabi) or `"en"` (English). Default: `"pa"`.

Expected: `200 { user: { ... } }`

### Step 5 — Get Profile

```
GET {{base_url}}/auth/profile
Authorization: Bearer {{customer_token}}
```

Verify name, phone, role, profile completion status.

### Step 6 — Update Emergency Contact

```
PUT {{base_url}}/auth/emergency-contact
Authorization: Bearer {{customer_token}}

Body:
{
  "emergencyContactName": "Priya Sharma",
  "emergencyContactPhone": "+919876543211"
}
```

### Step 7 — Save Home / Work Location

```
PUT {{base_url}}/auth/saved-location
Authorization: Bearer {{customer_token}}

Body:
{
  "type": "home",
  "lat": 28.4089,
  "lng": 77.3178,
  "address": "Sector 14, Faridabad"
}
```

- `type`: `"home"` or `"work"`.

### Step 8 — Register FCM Device Token

```
PUT {{base_url}}/auth/device-token
Authorization: Bearer {{customer_token}}

Body:
{
  "fcmToken": "fake-fcm-token-for-testing-1234567890abcdef"
}
```

Used for push notifications. In testing use any non-empty string up to 512 chars.

**What to verify in this journey:**
- New user gets `isNewUser: true` on first verify, `false` on subsequent.
- `401` if Bearer token is missing or expired.
- `422` if phone format is wrong (e.g. `9876543210` without `+91`).
- `422` if OTP length is not exactly 4 digits.
- Rate limiter returns `429` after 5 OTP sends in 15 minutes.

---

## 5. Journey 2: Book a Ride (On-Demand)

Covers fare estimate → book → driver side acceptance → ride start → complete → rate.

### Step 1 — Fare Estimate

```
POST {{base_url}}/rides/fare-estimate
Authorization: Bearer {{customer_token}}

Body:
{
  "pickup": {
    "lat": 28.4089,
    "lng": 77.3178,
    "address": "Sector 14, Faridabad"
  },
  "drop": {
    "lat": 28.3852,
    "lng": 77.3126,
    "address": "NIT Faridabad"
  }
}
```

Expected response includes `fare`, `distance`, `duration`, `minimumFare`, `breakdown` (base + GST).

### Step 2 — Create Ride

```
POST {{base_url}}/rides
Authorization: Bearer {{customer_token}}
Idempotency-Key: <generate a UUID, e.g. 550e8400-e29b-41d4-a716-446655440000>

Body:
{
  "pickup": {
    "lat": 28.4089,
    "lng": 77.3178,
    "address": "Sector 14, Faridabad"
  },
  "drop": {
    "lat": 28.3852,
    "lng": 77.3126,
    "address": "NIT Faridabad"
  },
  "paymentMethod": "CASH"
}
```

- `paymentMethod`: `"CASH"` or `"UPI"`.
- `Idempotency-Key` header is required. Use a fresh UUID per booking attempt. Retrying with the same key returns the original ride instead of creating a duplicate.
- Role must be `CUSTOMER` — driver token returns `403`.

Expected response: `201 { ride: { id: "clxxx...", status: "REQUESTED", ... } }`

**Save `ride.id` as `ride_id` in your environment.**

### Step 3 — Get Ride Details

```
GET {{base_url}}/rides/{{ride_id}}
Authorization: Bearer {{customer_token}}
```

Check status is `REQUESTED`, then `DRIVER_ASSIGNED` once a driver accepts.

### Step 4 — Register Driver (separate user)

To test the driver side, use a different phone number.

4a. Send OTP for driver phone:
```
POST {{base_url}}/auth/otp/send
Body: { "phone": "+919999999999" }
```

4b. Register as driver (OTP + name in one call):
```
POST {{base_url}}/auth/register-driver
Body:
{
  "phone": "+919999999999",
  "otp": "1234",
  "name": "Ravi Kumar"
}
```

Expected: `{ customToken: "...", role: "DRIVER" }`

4c. Exchange custom token → set as `driver_token` in environment.

### Step 5 — Driver: Submit KYC Documents

```
POST {{base_url}}/driver/documents
Authorization: Bearer {{driver_token}}

Body:
{
  "licenseNumber": "HR-0120110012345",
  "licenseUrl": "https://example.com/license.jpg",
  "rcNumber": "HR01AB1234",
  "rcUrl": "https://example.com/rc.jpg",
  "aadharNumber": "123456789012",
  "aadharUrl": "https://example.com/aadhar.jpg",
  "vehicleNumber": "HR01AB1234",
  "vehicleModel": "Honda Activa 6G",
  "bikePhotoUrl": "https://example.com/bike.jpg"
}
```

- `aadharNumber` must be exactly 12 digits.
- All `*Url` fields must be valid URLs.
- `bikePhotoUrl` is optional.

After this the driver status is `PENDING_VERIFICATION`.

### Step 6 — Admin: Approve Driver

First you need an admin user. If no admin exists yet:

```
POST {{base_url}}/admin/promote
x-internal-api-key: {{internal_api_key}}

Body:
{
  "phone": "+918888888888"
}
```

That phone number must have already done OTP verify (have a user account). After promoting, do OTP verify + token exchange for that phone → set as `admin_token`.

Approve the driver:

```
POST {{base_url}}/admin/drivers/{{driver_user_id}}/approve
Authorization: Bearer {{admin_token}}

Body:
{
  "note": "Documents verified manually"
}
```

Get `driver_user_id` from `GET /admin/drivers/pending`.

### Step 7 — Driver: Go Online

```
POST {{base_url}}/driver/go-online
Authorization: Bearer {{driver_token}}

Body:
{
  "lat": 28.4089,
  "lng": 77.3178
}
```

Driver must be approved before going online (otherwise `403`).

Expected: `200 { status: "ONLINE" }`

### Step 8 — Driver: Check Incoming Ride

```
GET {{base_url}}/driver/rides/incoming
Authorization: Bearer {{driver_token}}
```

This returns the pending ride offer if the driver is within search radius of the pickup. If empty, the broadcast may have expired (30s window) — cancel and create a new ride.

### Step 9 — Driver: Accept Ride

```
POST {{base_url}}/driver/rides/{{ride_id}}/accept
Authorization: Bearer {{driver_token}}
```

No body needed. Expected: `200 { ride: { status: "DRIVER_ASSIGNED", ... } }`

### Step 10 — Customer: Verify Assignment

```
GET {{base_url}}/rides/{{ride_id}}
Authorization: Bearer {{customer_token}}
```

Status should now be `DRIVER_ASSIGNED`. Response includes driver name, vehicle info, ETA.

### Step 11 — Driver: Update Location

```
POST {{base_url}}/driver/location
Authorization: Bearer {{driver_token}}

Body:
{
  "lat": 28.4100,
  "lng": 77.3180
}
```

Send periodically to simulate driver moving. Customer can poll `GET /rides/:rideId/location` to see this.

### Step 12 — Customer: Poll Driver Location

```
GET {{base_url}}/rides/{{ride_id}}/location
Authorization: Bearer {{customer_token}}
```

Returns current driver lat/lng from Firebase RTDB.

### Step 13 — Driver: Arrived at Pickup

```
POST {{base_url}}/driver/rides/{{ride_id}}/arrived
Authorization: Bearer {{driver_token}}
```

No body. Status transitions to `DRIVER_ARRIVED`.

### Step 14 — Driver: Start Ride (OTP verification)

Customer sees a 4-digit OTP in the app (also in ride details response). Driver enters it to start.

```
POST {{base_url}}/driver/rides/{{ride_id}}/start
Authorization: Bearer {{driver_token}}

Body:
{
  "otp": "5678"
}
```

- OTP must match what the backend generated for this ride.
- Wrong OTP returns `400`.

Status transitions to `IN_PROGRESS`.

### Step 15 — Driver: Complete Ride

```
POST {{base_url}}/driver/rides/{{ride_id}}/complete
Authorization: Bearer {{driver_token}}

Body:
{
  "note": "Smooth ride"
}
```

- `note` is optional.

Status transitions to `COMPLETED`.

**What to verify in this journey:**
- Duplicate ride creation with same `Idempotency-Key` returns the original ride (not a new one).
- `GET /rides/:rideId` status progresses: `REQUESTED` → `DRIVER_ASSIGNED` → `DRIVER_ARRIVED` → `IN_PROGRESS` → `COMPLETED`.
- Driver token cannot access customer-only routes and vice versa.
- Invalid OTP on start returns `400`.

---

## 6. Journey 3: Post-Ride — Payment and Rating

### Step 1 — Get Ride Receipt

```
GET {{base_url}}/rides/{{ride_id}}/receipt
Authorization: Bearer {{customer_token}}
```

Only available for `COMPLETED` rides. Returns fare breakdown, GST, distance, payment status.

### Step 2a — Cash Payment (no action needed)

For `CASH` rides, payment is considered settled after ride completion. The receipt will show `paymentStatus: PAID`.

### Step 2b — UPI Payment Flow

If ride was booked with `"paymentMethod": "UPI"`:

**Create Razorpay order:**
```
POST {{base_url}}/payments/order
Authorization: Bearer {{customer_token}}

Body:
{
  "rideId": "{{ride_id}}"
}
```

Returns Razorpay `orderId`, `amount` (in paise), `currency`.

**Verify payment (after Razorpay checkout completes):**
```
POST {{base_url}}/payments/verify
Authorization: Bearer {{customer_token}}

Body:
{
  "razorpayPaymentId": "pay_xxxxxxxxxxxxx",
  "razorpayOrderId": "order_xxxxxxxxxxxxx",
  "razorpaySignature": "computed_hmac_sha256_signature",
  "rideId": "{{ride_id}}"
}
```

In testing without a real Razorpay transaction, this will fail signature validation — that is expected. To test the full UPI flow you need a Razorpay test mode key and to complete a test checkout.

### Step 3 — Customer Rates Driver

```
POST {{base_url}}/rides/{{ride_id}}/rate
Authorization: Bearer {{customer_token}}

Body:
{
  "rating": 5,
  "comment": "Very punctual and polite"
}
```

- `rating`: integer 1–5.
- `comment`: optional, max 500 chars.
- Can only rate a `COMPLETED` ride. Returns `409` if already rated.

### Step 4 — Customer Skips Rating

Alternative to rating — marks as permanently skipped for this ride:

```
POST {{base_url}}/rides/{{ride_id}}/skip-rating
Authorization: Bearer {{customer_token}}
```

### Step 5 — Driver Rates Customer

```
POST {{base_url}}/driver/rides/{{ride_id}}/rate-customer
Authorization: Bearer {{driver_token}}

Body:
{
  "rating": 4,
  "comment": "Good passenger"
}
```

**What to verify:**
- Receipt returns `404` for non-existent ride ID.
- Receipt returns `403` if requested by a non-customer or wrong customer.
- Duplicate rating returns `409`.
- Rating on non-completed ride returns `400` or `409`.

---

## 7. Journey 4: Active Ride Status Updates

This covers real-time status polling, share link, and SOS.

### Step 1 — Create Share Link

While ride is active (`IN_PROGRESS`):

```
POST {{base_url}}/rides/{{ride_id}}/share
Authorization: Bearer {{customer_token}}
```

Returns `{ token: "abc123..." }`. Save as `share_token`.

### Step 2 — Public Tracking (no auth)

Anyone with the token can poll:

```
GET {{base_url}}/track/{{share_token}}
```

No `Authorization` header needed. Returns pickup, drop, driver location, ride status.

### Step 3 — Trigger SOS

```
POST {{base_url}}/rides/{{ride_id}}/sos
Authorization: Bearer {{customer_token}}

Body:
{
  "lat": 28.4089,
  "lng": 77.3178
}
```

Returns `{ sosAlertId: "clxxx..." }`. Save as `sos_alert_id`.

### Step 4 — Resolve SOS

```
POST {{base_url}}/rides/sos/{{sos_alert_id}}/resolve
Authorization: Bearer {{customer_token}}
```

**What to verify:**
- Public track endpoint returns data without any `Authorization` header.
- Share token from a different ride's ID is rejected.
- SOS on a completed ride returns an error.

---

## 8. Journey 5: Cancel Ride

### Cancel Before Driver Assigned (no fee)

```
POST {{base_url}}/rides/{{ride_id}}/cancel
Authorization: Bearer {{customer_token}}

Body:
{
  "reasonCode": "BOOKED_BY_MISTAKE"
}
```

### Cancel After Driver Arrived (cancellation fee applies)

```
POST {{base_url}}/rides/{{ride_id}}/cancel
Authorization: Bearer {{customer_token}}

Body:
{
  "reasonCode": "CHANGED_MIND",
  "note": "Changed my mind about the destination"
}
```

- `note` is optional, max 500 chars.
- Fee of ₹40 applies when status is `DRIVER_ARRIVED` or later.

**Valid `reasonCode` values:**

| Code | Description | Fee |
|---|---|---|
| `DRIVER_ASKED_TO_CANCEL` | Driver fault | Waived |
| `DRIVER_NOT_MOVING` | Driver fault | Waived |
| `DRIVER_WRONG_VEHICLE` | Driver fault | Waived |
| `DRIVER_BEHAVIOUR` | Driver fault | Waived |
| `CHANGED_MIND` | Customer fault | Applied if arrived |
| `BOOKED_BY_MISTAKE` | Customer fault | Applied if arrived |
| `OTHER` | Generic | Applied if arrived |

### Driver Declines Ride

```
POST {{base_url}}/driver/rides/{{ride_id}}/decline
Authorization: Bearer {{driver_token}}

Body:
{
  "reason": "Going in opposite direction"
}
```

`reason` is optional.

### Driver Cancels Active Ride

```
POST {{base_url}}/driver/rides/{{ride_id}}/cancel
Authorization: Bearer {{driver_token}}

Body:
{
  "reason": "Vehicle breakdown"
}
```

**What to verify:**
- Invalid `reasonCode` returns `422`.
- Cancelling an already-completed ride returns `409` or `400`.
- Serial cancellers get blocked after threshold (Redis-based — hard to test manually but the policy is active).

---

## 9. Journey 6: Scheduled Ride

### Step 1 — Create Scheduled Ride

```
POST {{base_url}}/rides/schedule
Authorization: Bearer {{customer_token}}
Idempotency-Key: <new UUID>

Body:
{
  "pickup": {
    "lat": 28.4089,
    "lng": 77.3178,
    "address": "Sector 14, Faridabad"
  },
  "drop": {
    "lat": 28.3852,
    "lng": 77.3126,
    "address": "NIT Faridabad"
  },
  "paymentMethod": "CASH",
  "scheduledAt": "2026-03-22T08:00:00.000Z"
}
```

- `scheduledAt` must be a future ISO 8601 datetime.
- Cannot schedule more than 7 days ahead.
- Returns `201 { ride: { status: "SCHEDULED", scheduledAt: "..." } }`

Save `ride.id` as `scheduled_ride_id`.

### Step 2 — Get Scheduled Rides List

```
GET {{base_url}}/rides/scheduled
Authorization: Bearer {{customer_token}}
```

Returns all upcoming scheduled rides for this customer.

### Step 3 — Cancel Scheduled Ride

```
POST {{base_url}}/rides/{{scheduled_ride_id}}/cancel
Authorization: Bearer {{customer_token}}

Body:
{
  "reasonCode": "CHANGED_MIND"
}
```

**What to verify:**
- Past `scheduledAt` time returns `422`.
- More than 7 days ahead returns `422`.
- Scheduled ride appears in `GET /rides/scheduled` and not in active rides.

---

## 10. Journey 7: Notifications

### Get Notifications (paginated)

```
GET {{base_url}}/notifications?page=1&limit=20
Authorization: Bearer {{customer_token}}
```

Returns list of notifications. Save a `notificationId` from the response.

### Get Unread Count

```
GET {{base_url}}/notifications/unread-count
Authorization: Bearer {{customer_token}}
```

### Mark One as Read

```
PATCH {{base_url}}/notifications/{{notification_id}}/read
Authorization: Bearer {{customer_token}}
```

### Mark All as Read

```
PATCH {{base_url}}/notifications/read-all
Authorization: Bearer {{customer_token}}
```

**What to verify:**
- Unread count decreases after marking as read.
- Notifications from other users are not visible.

---

## 11. Journey 8: Driver Earnings and Withdrawals

### Get Driver Status

```
GET {{base_url}}/driver/status
Authorization: Bearer {{driver_token}}
```

Returns `ONLINE`/`OFFLINE`, current location, active ride if any.

### Go Offline

```
POST {{base_url}}/driver/go-offline
Authorization: Bearer {{driver_token}}
```

### Get Earnings (today/week/month)

```
GET {{base_url}}/driver/earnings?period=today&page=1&limit=20
Authorization: Bearer {{driver_token}}
```

`period`: `today`, `week`, or `month`.

### Get Earnings Summary

```
GET {{base_url}}/driver/earnings/summary?period=week
Authorization: Bearer {{driver_token}}
```

`period`: `week` or `month`. Returns total earnings, trip count, average per trip.

### Get Settlement Summary

```
GET {{base_url}}/driver/earnings/settlement
Authorization: Bearer {{driver_token}}
```

Returns settled vs unsettled balance.

### Get Trip History

```
GET {{base_url}}/driver/trips?page=1&limit=20
Authorization: Bearer {{driver_token}}
```

### Request Withdrawal — UPI

```
POST {{base_url}}/driver/withdrawals
Authorization: Bearer {{driver_token}}

Body:
{
  "amount": 500,
  "method": "BANK_TRANSFER",
  "upiId": "ravi@upi"
}
```

### Request Withdrawal — Bank Account

```
POST {{base_url}}/driver/withdrawals
Authorization: Bearer {{driver_token}}

Body:
{
  "amount": 500,
  "method": "BANK_TRANSFER",
  "bankAccountNumber": "12345678901",
  "bankIfsc": "HDFC0001234"
}
```

- `BANK_TRANSFER` requires either `upiId` OR both `bankAccountNumber` + `bankIfsc`.
- `CASH_AGENT` requires neither.
- Max single withdrawal: ₹50,000.

Save `withdrawalId` from response as `withdrawal_id`.

### Check Withdrawal Status

```
GET {{base_url}}/driver/withdrawals/{{withdrawal_id}}
Authorization: Bearer {{driver_token}}
```

**What to verify:**
- `BANK_TRANSFER` without UPI ID and without bank details returns `422`.
- IFSC must match format `HDFC0001234` (4 alpha + 0 + 6 alphanumeric).
- Amount over 50000 returns `422`.

---

## 12. Journey 9: Ride History

### Customer Ride History

```
GET {{base_url}}/rides/history?page=1&limit=10&status=ALL
Authorization: Bearer {{customer_token}}
```

`status` filter: `COMPLETED`, `CANCELLED`, or `ALL` (default).

---

## 13. Journey 10: Admin Panel

All admin routes require `Authorization: Bearer {{admin_token}}` except `/promote`.

### Promote a User to Admin

```
POST {{base_url}}/admin/promote
x-internal-api-key: {{internal_api_key}}

Body:
{
  "phone": "+918888888888"
}
```

No `Authorization` header — uses `x-internal-api-key` header instead.

### List Pending Drivers

```
GET {{base_url}}/admin/drivers/pending?page=1&limit=20
Authorization: Bearer {{admin_token}}
```

### Get Driver Detail

```
GET {{base_url}}/admin/drivers/{{driver_user_id}}
Authorization: Bearer {{admin_token}}
```

### Approve Driver

```
POST {{base_url}}/admin/drivers/{{driver_user_id}}/approve
Authorization: Bearer {{admin_token}}

Body:
{
  "note": "All documents verified"
}
```

`note` is optional.

### Reject Driver

```
POST {{base_url}}/admin/drivers/{{driver_user_id}}/reject
Authorization: Bearer {{admin_token}}

Body:
{
  "reason": "License number does not match records"
}
```

`reason` is required, min 5 chars.

### Auto-Verify Driver (KYC provider)

```
POST {{base_url}}/admin/drivers/{{driver_user_id}}/auto-verify
Authorization: Bearer {{admin_token}}
```

Triggers KYC provider verification. With `ManualKYCProvider` (default) this is a no-op. With `SurepassKYCProvider` (when `SUREPASS_API_KEY` is set) this calls the external API.

### Get Live Rides

```
GET {{base_url}}/admin/rides/live?page=1&limit=20
Authorization: Bearer {{admin_token}}
```

Returns all rides currently in `DRIVER_ASSIGNED`, `DRIVER_ARRIVED`, or `IN_PROGRESS` status.

### Get All Rides (filtered)

```
GET {{base_url}}/admin/rides?status=COMPLETED&paymentStatus=FAILED&page=1&limit=20
Authorization: Bearer {{admin_token}}
```

Both filters are optional. Valid `status` values:
`REQUESTED`, `DRIVER_ASSIGNED`, `DRIVER_ARRIVED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`, `NO_DRIVER`, `SCHEDULED`

Valid `paymentStatus` values: `PENDING`, `COMPLETED`, `FAILED`, `REFUNDED`

### Get Platform Config

```
GET {{base_url}}/admin/config
Authorization: Bearer {{admin_token}}
```

Returns all 13 platform config keys (fares, GST, radius, policies, etc.).

### Update Platform Config

```
PUT {{base_url}}/admin/config/cancel_fee_arrived_amount
Authorization: Bearer {{admin_token}}

Body:
{
  "value": "40"
}
```

`value` is always a string. Known config keys:

| Key | Default | Meaning |
|---|---|---|
| `cancel_fee_arrived_amount` | `40` | Cancellation fee (₹) after driver arrived |
| `gst_percentage` | `5` | GST on fare |
| `driver_search_radius_km_expanded` | `12` | Expanded radius after first pass |

**What to verify:**
- Customer or driver token on admin routes returns `403`.
- Wrong `x-internal-api-key` on `/admin/promote` returns `403`.
- Approving an already-approved driver — check idempotency behavior.

---

## 14. Error Reference

| HTTP Code | Cause | Fix |
|---|---|---|
| `400` | Business rule violation (wrong OTP, wrong state) | Check ride status, OTP value |
| `401` | Missing or expired Firebase ID token | Re-do OTP verify + token exchange |
| `403` | Wrong role or missing internal key | Use correct token for the route |
| `404` | Resource not found | Check IDs in environment |
| `409` | Conflict (duplicate rating, already cancelled) | State already set |
| `422` | Validation failed (bad field format/value) | Check request body against schema |
| `429` | Rate limit hit | Wait or use a different IP |
| `500` | Server error | Check backend console for stack trace |

---

## 15. Recommended Test Order (Quick Smoke Run)

Run these in order to confirm the whole system works end to end:

1. `GET /health` — server alive
2. `POST /auth/otp/send` — OTP delivered
3. `POST /auth/otp/verify` + token exchange — customer token obtained
4. `PUT /auth/profile` — profile complete
5. `POST /rides/fare-estimate` — pricing works
6. `POST /rides` (with Idempotency-Key) — ride created, save `ride_id`
7. Register + approve driver (steps 4–6 in Journey 2)
8. `POST /driver/go-online` — driver online
9. `GET /driver/rides/incoming` — ride offer visible
10. `POST /driver/rides/:rideId/accept` — ride assigned
11. `GET /rides/:rideId` — status = `DRIVER_ASSIGNED`
12. `POST /driver/rides/:rideId/arrived` — status = `DRIVER_ARRIVED`
13. `POST /driver/rides/:rideId/start` (with OTP) — status = `IN_PROGRESS`
14. `POST /driver/rides/:rideId/complete` — status = `COMPLETED`
15. `GET /rides/:rideId/receipt` — receipt returned
16. `POST /rides/:rideId/rate` — rating submitted
17. `GET /notifications` — notification for completed ride present

Total: ~17 requests. All critical paths covered.

---

## 16. Tips

- **Always use a fresh UUID** for `Idempotency-Key` when creating a new ride. Copy from `uuidgenerator.net` or use Postman's `{{$guid}}` variable.
- **Driver must be APPROVED** before going online. If `driver/go-online` returns `403`, check KYC status via admin panel.
- **OTP in dev** always prints to the backend console (`npm run dev` terminal). Watch that window.
- **Token expiry**: Firebase ID tokens last ~1 hour. If you start getting `401` mid-session, redo Steps 2–3 of Journey 1 and update the environment variable.
- **Postman Tests tab**: Add `pm.environment.set("ride_id", pm.response.json().ride.id)` in the Tests tab of the create-ride request to auto-capture `ride_id`.
