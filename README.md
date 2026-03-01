# Chalo 🏍️

> Faridabad's hyper-local **bike ride-hailing app** for customers and drivers.
> V1 — Bike rides only · Android (Kotlin + Jetpack Compose) · Hindi/Punjabi market

---

## Project Overview

| Item | Detail |
|---|---|
| Platform | Android (API 28+) |
| Language | Kotlin + Jetpack Compose |
| Market | Faridabad, Haryana |
| App Languages | Punjabi (`pa`) + English (`en`) |
| Payment | Razorpay (UPI) + Cash |
| Maps | Google Maps SDK |
| Auth | Firebase Phone OTP |
| V1 Scope | Bike rides only |

---

## Repository Structure

```
Chalo/
├── README.md               # This file
├── chalo-backend/          # Node.js + TypeScript + Express API
│   ├── prisma/             # Database schema (PostgreSQL) + seed + migrations
│   └── src/
│       ├── config/         # App config, logger, Firebase, database, Redis
│       ├── controllers/    # HTTP request handlers (auth, ride, driver, admin…)
│       ├── jobs/           # BullMQ background jobs (ride expiry, OTP cleanup)
│       ├── middleware/     # Auth, error handler, rate limiter, validator
│       ├── routes/         # Express route definitions
│       ├── services/       # Business logic (ride, fare, payment, SOS, KYC…)
│       ├── types/          # TypeScript type definitions
│       ├── utils/          # Helpers, constants, ApiError, ApiResponse, metrics
│       ├── validators/     # Zod request validators
│       └── __tests__/      # Unit + integration tests (Jest + ts-jest)
│
└── docs/
    ├── api/
    │   └── POSTMAN_GUIDE.md          # API testing guide (Postman flows)
    ├── design/                        # All UI/UX design files
    │   ├── chalo-prototype.html
    │   ├── chalo-customer-screens.html
    │   ├── chalo-driver-screens.html
    │   ├── chalo-design-system.html
    │   ├── chalo-component-library.html
    │   ├── chalo-spec-sheet.html
    │   ├── chalo-user-flows.html
    │   ├── chalo-wireframes.html
    │   └── chalo-design-tokens.json
    ├── development/
    │   ├── NEXT_STEPS.md             # Step-by-step development roadmap
    │   └── IMPLEMENTATION_ROADMAP.md # Detailed task breakdown (phases 1-3)
    ├── product/
    │   └── chalo-product-documentation.md
    └── reviews/
        ├── CODE_REVIEW.md
        ├── SECURITY_PERFORMANCE_REVIEW.md
        ├── chalo-backend-review.md
        └── chalo-master-document.docx
```

---

## Backend Stack

| Layer | Technology |
|---|---|
| Runtime | Node.js 20+ |
| Language | TypeScript 5.3 (strict) |
| Framework | Express 4 |
| ORM | Prisma 5 |
| Database | PostgreSQL (PostGIS) + 6 composite indexes |
| Cache | Redis (rate limiting, idempotency, auth tokens, config cache) |
| Auth | Firebase Admin SDK + phone OTP (SHA-256 hashed) |
| Push Notifications | FCM (Firebase Cloud Messaging) + in-app DB storage |
| Realtime Location | Firebase Realtime Database (ride status sync) |
| File Storage | Firebase Storage |
| Payments | Razorpay (UPI + webhooks) |
| Maps | Google Maps Directions + Places APIs |
| Validation | Zod (every endpoint) |
| Logging | Winston (JSON prod / colorized dev, Docker-aware) |
| Security | Helmet, CORS, HPP, per-endpoint rate limiting (Redis), request ID tracing, timing-safe comparisons |
| Testing | Jest + ts-jest (163 tests, 8 suites, 100% passing) |
| Circuit Breaker | opossum (Razorpay API protection) |
| Metrics | prom-client (Prometheus, custom business metrics) |
| Load Testing | k6 (smoke test with thresholds) |

---

## Business Rules (Locked)

| Config | Value | Changeable? |
|---|---|---|
| Commission (per ride) | 15% | Yes — via DB config |
| Weekly subscription | ₹199 | Yes — via DB config |
| Surge pricing | Enabled | Yes — via DB config |
| Min fare | ₹30 | Yes — via DB config |
| Base fare / km | ₹12 | Yes — via DB config |
| Base fare / min | ₹2 | Yes — via DB config |
| Booking fee | ₹5 | Yes — via DB config |
| Settlement | T+2 days | Yes — via DB config |
| Payment gateway | Razorpay | No |

All configs live in the `PlatformConfig` DB table — change without redeployment.

---

## Backend Quick Start

### Prerequisites
- Node.js ≥ 20
- Docker Desktop (for PostgreSQL + Redis)
- Firebase project (already configured — `firebase-service-account.json` present)

### Local Dev Setup (first time)

```bash
# 1. Start the PostGIS database container
docker start chalo-db
# (First time only: docker run -d --name chalo-db -e POSTGRES_PASSWORD=luffy \
#   -e POSTGRES_DB=chalo -p 5432:5432 postgis/postgis:15-3.3)

# 2. Install dependencies
cd chalo-backend
npm install

# 3. Apply database migrations (already done — skip if DB exists)
npx prisma migrate deploy

# 4. Start development server
npm run dev
# → Server running at http://localhost:3001/api/v1
# → Health check: http://localhost:3001/health
```

### Daily Dev Workflow

```bash
docker start chalo-db   # Start DB if not running
cd chalo-backend
npm run dev             # Start API (hot reload)
```

### Environment Variables (`.env`)

```env
DATABASE_URL="postgresql://postgres:luffy@localhost:5432/chalo?schema=public"
PORT=3001
NODE_ENV=development

# Firebase (service account JSON path)
FIREBASE_SERVICE_ACCOUNT_PATH=./firebase-service-account.json
FIREBASE_DATABASE_URL=https://your-project.firebaseio.com

# Razorpay (test keys — skip for Cash-only dev)
RAZORPAY_KEY_ID=rzp_test_xxxxxxxxxxxx
RAZORPAY_KEY_SECRET=your_test_secret

# Google Maps (use Haversine fallback in dev — key optional)
GOOGLE_MAPS_API_KEY=your_google_maps_api_key
```

### Useful Commands

```bash
npm run dev            # Start dev server (hot reload)
npm run build          # Compile TypeScript → dist/
npm run lint           # ESLint
npm test               # Run all tests (unit + integration)
npm run db:studio      # Open Prisma Studio GUI (localhost:5555)
npm run db:seed        # Seed/reseed platform config
npx prisma migrate deploy   # Apply pending migrations
docker-compose up      # Full stack (API + PostGIS + Redis via Docker)
```

---

## API Endpoints — 41 Total

### Auth (7 endpoints)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/auth/otp/send` | Public | Send OTP to phone |
| POST | `/api/v1/auth/otp/verify` | Public | Verify OTP + get Firebase token |
| GET | `/api/v1/auth/profile` | Required | Get current user profile |
| PUT | `/api/v1/auth/profile` | Required | Complete / update profile |
| PUT | `/api/v1/auth/emergency-contact` | Required | Update emergency contact |
| PUT | `/api/v1/auth/saved-location` | Required | Save home / work location |
| PUT | `/api/v1/auth/device-token` | Required | Register FCM device token |

### Rides — Customer (11 endpoints)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/rides/fare-estimate` | Required | Get fare + ETA before booking |
| POST | `/api/v1/rides` | CUSTOMER | Create on-demand ride |
| POST | `/api/v1/rides/schedule` | CUSTOMER | Schedule a future ride |
| GET | `/api/v1/rides/history` | CUSTOMER | Paginated ride history |
| GET | `/api/v1/rides/scheduled` | CUSTOMER | Upcoming scheduled rides |
| GET | `/api/v1/rides/:rideId` | Required | Ride details |
| GET | `/api/v1/rides/:rideId/location` | Required | Live driver location |
| POST | `/api/v1/rides/:rideId/cancel` | CUSTOMER | Cancel a ride |
| POST | `/api/v1/rides/:rideId/rate` | CUSTOMER | Rate a completed ride |
| POST | `/api/v1/rides/:rideId/sos` | Required | Trigger SOS alert |
| POST | `/api/v1/rides/sos/:sosAlertId/resolve` | Required | Resolve SOS alert |

### Payments (3 endpoints)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/api/v1/payments/order` | Required | Create Razorpay order |
| POST | `/api/v1/payments/verify` | Required | Verify UPI payment |
| POST | `/api/v1/payments/webhook` | Signature | Razorpay webhook handler |

### Notifications (4 endpoints)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/v1/notifications` | Required | Paginated notifications list |
| GET | `/api/v1/notifications/unread-count` | Required | Unread notification count |
| PATCH | `/api/v1/notifications/:notificationId/read` | Required | Mark one as read |
| PATCH | `/api/v1/notifications/read-all` | Required | Mark all as read |

### Driver (16 endpoints)

All driver endpoints require Firebase auth + `DRIVER` role.

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/driver/go-online` | Set online + starting GPS location |
| POST | `/api/v1/driver/go-offline` | Set offline (blocked if active ride) |
| GET | `/api/v1/driver/status` | Current online state + active ride |
| POST | `/api/v1/driver/location` | GPS location update (Redis→Postgres→RTDB) |
| GET | `/api/v1/driver/rides/incoming` | Poll for pending ride offer |
| POST | `/api/v1/driver/rides/:rideId/accept` | Accept ride (atomic compare-and-swap) |
| POST | `/api/v1/driver/rides/:rideId/decline` | Decline ride + retrigger search |
| POST | `/api/v1/driver/rides/:rideId/arrived` | Mark arrived at pickup |
| POST | `/api/v1/driver/rides/:rideId/start` | Start ride (customer on board) |
| POST | `/api/v1/driver/rides/:rideId/complete` | Complete ride + create earnings record |
| POST | `/api/v1/driver/rides/:rideId/cancel` | Cancel ride (before start only) |
| GET | `/api/v1/driver/trips` | Paginated trip history |
| GET | `/api/v1/driver/earnings` | Earnings summary + breakdown by period |
| GET | `/api/v1/driver/earnings/settlement` | Pending / processing / settled amounts |
| POST | `/api/v1/driver/withdrawals` | Request payout (bank transfer or UPI) |
| GET | `/api/v1/driver/withdrawals/:withdrawalId` | Withdrawal status |

### Admin (8 endpoints)

All admin endpoints require Firebase auth + `ADMIN` role.

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/admin/drivers/pending` | List drivers pending verification (FIFO) |
| GET | `/api/v1/admin/drivers/:driverId` | Full driver profile + documents |
| POST | `/api/v1/admin/drivers/:driverId/approve` | Approve driver (optional note) |
| POST | `/api/v1/admin/drivers/:driverId/reject` | Reject driver (required reason) |
| POST | `/api/v1/admin/drivers/:driverId/auto-verify` | Run KYC API (Surepass / manual fallback) |
| GET | `/api/v1/admin/rides/live` | Live rides currently in progress |
| GET | `/api/v1/admin/config` | Get all platform config values |
| PUT | `/api/v1/admin/config/:key` | Update a platform config value |

---

## Design Files

All design assets are in [`docs/design/`](docs/design/):

| File | Contents |
|---|---|
| `chalo-prototype.html` | Interactive prototype |
| `chalo-customer-screens.html` | All 14 customer screen mockups |
| `chalo-driver-screens.html` | All 14 driver screen mockups |
| `chalo-wireframes.html` | Structural wireframes |
| `chalo-user-flows.html` | Complete user flow diagrams |
| `chalo-design-system.html` | Colors, typography, spacing rules |
| `chalo-component-library.html` | Reusable UI components |
| `chalo-spec-sheet.html` | Developer handoff specs |
| `chalo-design-tokens.json` | Design tokens (for Android theming) |

---

## Security & Reliability

All 25 security/performance/reliability findings have been resolved. Key protections:

| Feature | Implementation |
|---|---|
| OTP security | `crypto.randomInt()` + SHA-256 hashed storage |
| Webhook integrity | Raw body HMAC verification + timing-safe comparison |
| Payment validation | Ride ownership + order association + duplicate checks |
| Secret management | `requireEnv()` in production + startup guards |
| Circuit breaker | `opossum` on Razorpay API calls |
| Race conditions | `prisma.$transaction()` for ride creation + OTP verification |
| Rate limiting | Redis-backed, per-endpoint, trust proxy enabled |
| Idempotency | User-scoped cache keys (Redis, 24h TTL) |
| Metrics | Prometheus via `/metrics` (API key protected in production) |
| Observability | Request ID tracing, structured JSON logging (Winston) |

See [SECURITY_PERFORMANCE_REVIEW.md](docs/reviews/SECURITY_PERFORMANCE_REVIEW.md) for full details on all 25 findings (all fixed), and [CODE_REVIEW.md](docs/reviews/CODE_REVIEW.md) for the complete Round 3 + Round 4 reviews.

---

## Code Review Score

| Phase | Score | What Changed |
|---|---|---|
| Initial Review | 6.63/10 | Baseline |
| After Security Fixes (P0+P1) | 6.87/10 | Critical gaps identified and fixed |
| After P2+P3 Implementation | **7.42/10** | Redis singleton, RTDB sync, auth cache, indexes, k6 tests, coverage, notification validation |
| After Driver API + Docker + CI | **~8.0/10** | 16 driver endpoints, PostGIS indexes, Dockerized, CI pipeline |
| After Local DB Setup | **~8.0/10** | Docker PostGIS running locally, migrations applied, server live on port 3001 |

---

## Current Runtime Status

| Service | Status | Details |
|---|---|---|
| PostgreSQL + PostGIS | ✅ Running | Docker container `chalo-db`, port 5432 |
| Redis | ✅ Running | localhost:6379 |
| Firebase Admin | ✅ Connected | Service account loaded |
| API Server | ✅ Running | http://localhost:3001/api/v1 |
| BullMQ job queue | ✅ Running | OTP cleanup queue active |
| All 11 DB tables | ✅ Migrated | `prisma migrate deploy` applied |
| PostGIS GIST index | ✅ Applied | Driver proximity search optimised |

## What's Next

See [NEXT_STEPS.md](docs/development/NEXT_STEPS.md) for the detailed, step-by-step development roadmap.
