# Chalo Backend Flow Reference

Last updated: 2026-03-21

## 1) Request pipeline

Every API request follows this high-level chain:

1. request id middleware
2. security middleware (helmet/cors/hpp/sanitization)
3. rate limiting
4. auth + role checks (where required)
5. Zod validation wrappers
6. controller
7. service
8. data/external integrations (Prisma, Redis, Firebase, Razorpay)
9. standardized API response or global error handler

## 2) Route groups and counts

Mounted at /api/v1:

- /auth: 8
- /rides: 14
- /driver: 19
- /payments: 3
- /notifications: 4
- /admin: 10
- /track: 1

Total routed endpoints: 59

## 3) Auth flow

- /auth/otp/send: OTP request.
- /auth/otp/verify: OTP verification + token/user payload.
- /auth/register-driver: driver onboarding entry.
- protected profile/settings routes under /auth for profile, emergency contact, saved locations, device token.

## 4) Customer ride flow

1. Fare estimate via /rides/fare-estimate.
2. Ride creation via /rides or scheduling via /rides/schedule.
3. Track state through /rides/:rideId and /rides/:rideId/location.
4. Optional actions:
   - cancel
   - share link creation
   - SOS trigger/resolve
   - rate or skip rating
5. Receipt and history via /rides/:rideId/receipt and /rides/history.

## 5) Driver flow

1. Submit documents.
2. Go online/offline and send location updates.
3. Consume incoming rides.
4. Ride lifecycle actions:
   - accept/decline
   - arrived
   - start
   - complete
   - cancel
   - rate customer
5. Earnings and withdrawal endpoints for operational payout workflow.

## 6) Payment flow

- /payments/order: create payment order.
- /payments/verify: payment verification.
- /payments/webhook: signed webhook ingestion (raw body parsing in route).

## 7) Admin flow

- /admin/promote protected by internal API key middleware.
- authenticated admin APIs for driver approval/rejection/auto-verify, live rides, ride filtering, and platform config management.

## 8) Realtime and background jobs

- Firebase RTDB used for live status/location synchronization.
- BullMQ jobs initialized at server start for maintenance and ride lifecycle support.

## 9) Data model highlights

Core models in schema include:

- User, CustomerProfile, DriverProfile
- Ride, RideEvent, RideShareLink
- Earning, Withdrawal
- SOSAlert, OTPVerification, Notification, PlatformConfig

## 10) Known implementation gaps

- Backend is broad and mature, but docs and tests around newer endpoints should continue to be tightened.
- Mobile-side robust test coverage is still the biggest delivery risk for launch quality.