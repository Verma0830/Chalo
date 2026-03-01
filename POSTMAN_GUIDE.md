# Chalo Backend — Postman Testing Guide

## Setup

### 1. Create a Postman Environment

In Postman: Environments → New → name it `Chalo Dev`

Add these variables:

| Variable         | Value                          |
|-----------------|-------------------------------|
| `base_url`      | `http://localhost:3001/api/v1` |
| `customer_token`| *(empty — filled by Flow 1)*  |
| `driver_token`  | *(empty — filled by Flow 4)*  |
| `admin_token`   | *(empty — filled manually)*   |
| `ride_id`       | *(empty — filled by Flow 3)*  |

Use `{{base_url}}` in every request URL.
Set `Content-Type: application/json` as a default header.

### 2. Start the server

```bash
docker start chalo-db
cd chalo-backend && npm run dev
```

Check: `GET http://localhost:3001/health` → should return `{ "status": "ok" }`

---

## Flow 1 — Customer Registration

### 1a. Send OTP

```
POST {{base_url}}/auth/otp/send
Body:
{
  "phone": "+919876543210"
}
```

Expected: `200 OK`
```json
{ "success": true, "data": { "expiresAt": "..." } }
```

> The OTP is printed in the terminal log:
> `INFO: OTP sent to +91987****3210`

### 1b. Verify OTP

```
POST {{base_url}}/auth/otp/verify
Body:
{
  "phone": "+919876543210",
  "otp": "1234"
}
```

Expected: `200 OK`
```json
{
  "data": {
    "token": "eyJ...",
    "isNewUser": true
  }
}
```

**Copy the `token` value → paste into `customer_token` environment variable.**

### 1c. Complete Profile (new users only)

```
POST {{base_url}}/auth/profile/complete
Headers: Authorization: Bearer {{customer_token}}
Body:
{
  "name": "Rahul Kumar",
  "email": "rahul@example.com"
}
```

Expected: `200 OK`

---

## Flow 2 — Fare Estimate (Customer)

```
POST {{base_url}}/rides/fare-estimate
Headers: Authorization: Bearer {{customer_token}}
Body:
{
  "pickup": {
    "lat": 28.4089,
    "lng": 77.3178,
    "address": "Sector 15, Faridabad"
  },
  "drop": {
    "lat": 28.4595,
    "lng": 77.3023,
    "address": "NIT Faridabad"
  }
}
```

Expected: `200 OK`
```json
{
  "data": {
    "estimatedFare": 85,
    "distanceKm": 6.2,
    "estimatedMins": 18,
    "breakdown": {
      "distanceFare": 74,
      "timeFare": 36,
      "bookingFee": 5,
      "surgeMultiplier": 1
    }
  }
}
```

---

## Flow 3 — Book a Ride (Customer)

### 3a. Create Ride

```
POST {{base_url}}/rides
Headers: Authorization: Bearer {{customer_token}}
Body:
{
  "pickup": {
    "lat": 28.4089,
    "lng": 77.3178,
    "address": "Sector 15, Faridabad"
  },
  "drop": {
    "lat": 28.4595,
    "lng": 77.3023,
    "address": "NIT Faridabad"
  },
  "paymentMethod": "CASH"
}
```

Expected: `201 Created`
```json
{
  "data": {
    "rideId": "clxxx...",
    "status": "REQUESTED",
    "estimatedFare": 85
  }
}
```

**Copy `rideId` → paste into `ride_id` environment variable.**

### 3b. Get Ride Details

```
GET {{base_url}}/rides/{{ride_id}}
Headers: Authorization: Bearer {{customer_token}}
```

### 3c. Cancel Ride (if needed)

```
POST {{base_url}}/rides/{{ride_id}}/cancel
Headers: Authorization: Bearer {{customer_token}}
Body:
{
  "reason": "Changed my mind"
}
```

---

## Flow 4 — Driver Registration

Use a **different phone number** from the customer.

### 4a. Send OTP

```
POST {{base_url}}/auth/otp/send
Body: { "phone": "+919999999999" }
```

### 4b. Verify OTP

```
POST {{base_url}}/auth/otp/verify
Body: { "phone": "+919999999999", "otp": "1234" }
```

**Copy the token → paste into `driver_token`.**

### 4c. Complete Profile

```
POST {{base_url}}/auth/profile/complete
Headers: Authorization: Bearer {{driver_token}}
Body: { "name": "Suresh Driver" }
```

### 4d. Submit Driver Documents

```
POST {{base_url}}/driver/documents
Headers: Authorization: Bearer {{driver_token}}
Body:
{
  "licenseNumber": "HR1120230012345",
  "licenseUrl": "https://example.com/license.jpg",
  "rcNumber": "HR11AB1234",
  "rcUrl": "https://example.com/rc.jpg",
  "aadharNumber": "123412341234",
  "aadharUrl": "https://example.com/aadhar.jpg",
  "vehicleNumber": "HR 11 AB 1234",
  "vehicleModel": "Honda Activa 6G",
  "bikePhotoUrl": "https://example.com/bike.jpg"
}
```

### 4e. Admin Approves Driver

*(See Flow 6 below — approve via admin endpoints, or approve directly in DB for quick testing)*

**Quick DB shortcut for testing only:**
```sql
UPDATE driver_profiles
SET "verificationStatus" = 'VERIFIED', "verifiedAt" = NOW()
WHERE "userId" = '<driver-user-id>';
```

---

## Flow 5 — Complete End-to-End Ride

Run these in order. The driver and customer must be **different users** (different browser tabs or Postman windows, each with their own token).

### 5a. Driver goes online

```
POST {{base_url}}/driver/online
Headers: Authorization: Bearer {{driver_token}}
Body:
{
  "lat": 28.4089,
  "lng": 77.3178
}
```

### 5b. Customer creates ride (see Flow 3a)

The driver will receive an FCM notification. In tests without a real device, just poll:

### 5c. Driver gets incoming ride

```
GET {{base_url}}/driver/rides/incoming
Headers: Authorization: Bearer {{driver_token}}
```

Expected: returns the `rideId` if an offer is pending.

### 5d. Driver accepts ride

```
POST {{base_url}}/driver/rides/{{ride_id}}/accept
Headers: Authorization: Bearer {{driver_token}}
```

Expected: `200 OK`, status becomes `DRIVER_ASSIGNED`

### 5e. Driver arrives at pickup

```
POST {{base_url}}/driver/rides/{{ride_id}}/arrived
Headers: Authorization: Bearer {{driver_token}}
Body:
{
  "lat": 28.4089,
  "lng": 77.3178
}
```

### 5f. Driver starts ride

```
POST {{base_url}}/driver/rides/{{ride_id}}/start
Headers: Authorization: Bearer {{driver_token}}
```

### 5g. Driver completes ride

```
POST {{base_url}}/driver/rides/{{ride_id}}/complete
Headers: Authorization: Bearer {{driver_token}}
Body:
{
  "note": "Smooth ride"
}
```

Expected: status becomes `COMPLETED`, final fare calculated.

### 5h. Customer rates the ride

```
POST {{base_url}}/rides/{{ride_id}}/rate
Headers: Authorization: Bearer {{customer_token}}
Body:
{
  "rating": 5,
  "comment": "Great driver!"
}
```

---

## Flow 6 — Admin Panel

You need an ADMIN-role token. Get it by:

1. Creating a user normally (Flow 1)
2. Running this SQL to promote them:
   ```sql
   UPDATE users SET role = 'ADMIN' WHERE phone = '+919876543210';
   ```
3. Re-verify OTP → copy the new token → paste into `admin_token`

### 6a. List pending drivers

```
GET {{base_url}}/admin/drivers/pending?page=1&limit=20
Headers: Authorization: Bearer {{admin_token}}
```

### 6b. Get driver detail

```
GET {{base_url}}/admin/drivers/{{driver_user_id}}
Headers: Authorization: Bearer {{admin_token}}
```

### 6c. Approve a driver

```
POST {{base_url}}/admin/drivers/{{driver_user_id}}/approve
Headers: Authorization: Bearer {{admin_token}}
Body:
{
  "note": "Documents look good"
}
```

### 6d. Reject a driver

```
POST {{base_url}}/admin/drivers/{{driver_user_id}}/reject
Headers: Authorization: Bearer {{admin_token}}
Body:
{
  "reason": "License image is blurry. Please resubmit."
}
```

### 6e. Auto-verify via KYC (once SUREPASS_API_KEY is set)

```
POST {{base_url}}/admin/drivers/{{driver_user_id}}/auto-verify
Headers: Authorization: Bearer {{admin_token}}
```

### 6f. View live rides

```
GET {{base_url}}/admin/rides/live
Headers: Authorization: Bearer {{admin_token}}
```

### 6g. Get platform config

```
GET {{base_url}}/admin/config
Headers: Authorization: Bearer {{admin_token}}
```

### 6h. Update platform config

```
PUT {{base_url}}/admin/config/commission_percentage
Headers: Authorization: Bearer {{admin_token}}
Body:
{
  "value": "18"
}
```

Valid config keys: `commission_percentage`, `subscription_fee_weekly`, `surge_enabled`,
`surge_multiplier`, `min_fare`, `base_fare_per_km`, `base_fare_per_min`, `settlement_days`

---

## Flow 7 — Scheduled Ride

```
POST {{base_url}}/rides/schedule
Headers: Authorization: Bearer {{customer_token}}
Body:
{
  "pickup": {
    "lat": 28.4089,
    "lng": 77.3178,
    "address": "Sector 15, Faridabad"
  },
  "drop": {
    "lat": 28.4595,
    "lng": 77.3023,
    "address": "NIT Faridabad"
  },
  "paymentMethod": "CASH",
  "scheduledAt": "2026-03-02T10:00:00.000Z"
}
```

---

## Flow 8 — SOS

```
POST {{base_url}}/rides/{{ride_id}}/sos
Headers: Authorization: Bearer {{customer_token}}
```

Expected: `200 OK` — SOS alert created, SMS sent to emergency contacts.

---

## Common Errors

| Status | Code | Meaning |
|--------|------|---------|
| 400 | `VALIDATION_ERROR` | Bad request body — check field names/types |
| 401 | `UNAUTHORIZED` | Missing or expired token |
| 403 | `FORBIDDEN` | Wrong role (e.g. customer hitting driver endpoint) |
| 404 | `RIDE_NOT_FOUND` | Wrong rideId |
| 409 | `RIDE_ALREADY_ACTIVE` | Customer already has an active ride |
| 429 | `TOO_MANY_REQUESTS` | OTP rate limit hit |

---

## What to verify before starting the customer app

- [ ] Flow 1 works (OTP → token)
- [ ] Flow 2 returns a fare (Haversine fallback is fine without Google Maps key)
- [ ] Flow 3 creates a ride with status `REQUESTED`
- [ ] Flow 4 + 5 complete a full ride end-to-end
- [ ] Flow 6a returns an empty list (no drivers yet — that's correct)
- [ ] DB seeded: `npm run db:seed` (populates platform_config)
- [ ] Migration applied: `npx prisma migrate deploy`
