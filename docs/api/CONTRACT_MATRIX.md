# Chalo API — Android Contract Matrix

> **Base URL:** `http://localhost:3001/api/v1` (dev) · `https://api.chalo.in/api/v1` (prod)
> **Auth:** All endpoints except `POST auth/otp/send` and `POST auth/otp/verify` require `Authorization: Bearer <firebase-id-token>`
> **Response envelope:** Every response is `ApiResponse<T>` (`success`, `message`, `data`, `meta?`, `code?`)

---

## Auth — `AuthApiService`

| Method | Path | Request DTO | Response `data` DTO | Auth |
|--------|------|-------------|---------------------|------|
| POST | `auth/otp/send` | `SendOtpRequest` | `SendOtpResponseDto` | No |
| POST | `auth/otp/verify` | `VerifyOtpRequest` | `VerifyOtpResponseDto` | No |
| GET | `auth/profile` | — | `ProfileDto` | Yes |
| PUT | `auth/profile` | `CompleteProfileRequest` | `ProfileDto` | Yes |
| PUT | `auth/emergency-contact` | `UpdateEmergencyContactRequest` | `ProfileDto` | Yes |
| PUT | `auth/saved-location` | `SavedLocationRequest` | `ProfileDto` | Yes |
| PUT | `auth/device-token` | `RegisterDeviceTokenRequest` | `Unit` | Yes |

### Key DTOs

**`SendOtpRequest`**
```json
{ "phone": "+919876543210" }
```

**`VerifyOtpResponseDto`**
```json
{
  "isNewUser": true,
  "token": "<firebase-custom-token>",
  "user": { "id": "...", "phone": "...", "name": null, "role": "CUSTOMER" }
}
```

**`ProfileDto`** — see `AuthDtos.kt` for full shape including `customerProfile`.

---

## Rides — `RideApiService`

| Method | Path | Request DTO | Response `data` DTO | Auth | Notes |
|--------|------|-------------|---------------------|------|-------|
| POST | `rides/fare-estimate` | `FareEstimateRequest` | `FareEstimateDto` | Yes | Returns `routePolyline` (empty string if Maps API unavailable) |
| POST | `rides` | `CreateRideRequest` | `RideDto` | Yes | Requires `Idempotency-Key` header |
| POST | `rides/schedule` | `ScheduleRideRequest` | `RideDto` | Yes | Requires `Idempotency-Key` header |
| GET | `rides/history` | Query: `page`, `limit`, `status` | `List<RideHistoryItemDto>` | Yes | `status` default `ALL` |
| GET | `rides/scheduled` | — | `List<RideDto>` | Yes | Pending/upcoming scheduled rides |
| GET | `rides/{rideId}` | — | `RideDto` | Yes | |
| GET | `rides/{rideId}/location` | — | `RideLocationDto` | Yes | Poll for driver lat/lng |
| GET | `rides/{rideId}/receipt` | — | `RideReceiptDto` | Yes | |
| POST | `rides/{rideId}/cancel` | `CancelRideRequest` | `CancelRideResponseDto` | Yes | `reasonCode` required |
| POST | `rides/{rideId}/share` | — | `ShareRideResponseDto` | Yes | |
| POST | `rides/{rideId}/rate` | `RateRideRequest` | `Unit` | Yes | `rating` 1–5 |
| POST | `rides/{rideId}/skip-rating` | — | `Unit` | Yes | |
| POST | `rides/{rideId}/sos` | `TriggerSosRequest` | `Unit` | Yes | |

### Key DTOs

**`FareEstimateDto`**
```json
{
  "estimatedFare": 85,
  "distanceKm": 4.2,
  "durationMins": 18,
  "baseFare": 75,
  "bookingFee": 10,
  "surgeMultiplier": 1.0,
  "minimumFareApplied": false,
  "minimumFare": 50,
  "routePolyline": "encoded_polyline_string"
}
```

**`RideDto`** — full shape including nested `DriverSummaryDto` → `DriverProfileSummaryDto`.

**`CancelRideRequest`**
```json
{ "reasonCode": "DRIVER_TOO_FAR", "note": "optional text" }
```

---

## Payments — `PaymentApiService`

| Method | Path | Request DTO | Response `data` DTO | Auth |
|--------|------|-------------|---------------------|------|
| POST | `payments/order` | `CreatePaymentOrderRequest` | `PaymentOrderDto` | Yes |
| POST | `payments/verify` | `VerifyPaymentRequest` | `PaymentVerifyResponseDto` | Yes |

### Key DTOs

**`PaymentOrderDto`**
```json
{ "razorpayOrderId": "order_xxx", "amount": 8500, "currency": "INR", "rideId": "..." }
```
> `amount` is in paise (₹85 = 8500).

**`VerifyPaymentRequest`**
```json
{
  "razorpayOrderId": "order_xxx",
  "razorpayPaymentId": "pay_xxx",
  "razorpaySignature": "hmac_sha256_signature"
}
```

---

## Notifications — `NotificationApiService`

| Method | Path | Request | Response `data` DTO | Auth |
|--------|------|---------|---------------------|------|
| GET | `notifications` | Query: `page`, `limit` | `List<NotificationDto>` | Yes |
| GET | `notifications/unread-count` | — | `UnreadCountDto` | Yes |
| PATCH | `notifications/{id}/read` | — | `Unit` | Yes |
| PATCH | `notifications/read-all` | — | `Unit` | Yes |

---

## Response Envelope

```kotlin
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T?,
    val meta: PaginationMetaDto?,  // present on paginated list responses
    val code: String?,             // machine-readable error code on failures
)
```

### Paginated responses

`GET rides/history` and `GET notifications` include `meta`:
```json
{ "page": 1, "limit": 20, "total": 47, "totalPages": 3 }
```

---

## Real-time (Firebase RTDB, not REST)

Driver location and ride status updates are pushed via Firebase Realtime Database, not polled via REST:

| RTDB Path | Written by | Read by | Shape |
|-----------|-----------|---------|-------|
| `driver_locations/{driverId}` | Driver app | Customer app (`ActiveRideViewModel`) | `{ lat, lng, updatedAt }` |
| `ride_status/{rideId}` | Backend | Customer app | `{ status, driverId? }` |

---

## Error Codes (`code` field)

| Code | HTTP | Meaning |
|------|------|---------|
| `OTP_EXPIRED` | 400 | OTP has expired — resend |
| `OTP_INVALID` | 400 | Wrong OTP |
| `RIDE_NOT_FOUND` | 404 | Ride ID doesn't exist or not owned by caller |
| `OUTSIDE_SERVICE_AREA` | 400 | Pickup coordinates outside Punjab bounding box |
| `DRIVER_NOT_FOUND` | 404 | No drivers available after radius expansion |
| `CANCEL_FEE_APPLIED` | 200 | Ride cancelled with a ₹40 cancellation fee |
| `COOLDOWN_ACTIVE` | 429 | Serial canceller — customer in 30-min cooldown |
