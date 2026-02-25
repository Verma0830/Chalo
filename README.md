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
├── chalo-backend/          # Node.js + TypeScript + Express API
│   ├── prisma/             # Database schema (PostgreSQL) + seed
│   ├── src/
│   │   ├── config/         # App config, logger, Firebase, database
│   │   ├── controllers/    # HTTP request handlers
│   │   ├── middleware/     # Auth, error handler, rate limiter, validator
│   │   ├── routes/         # Express route definitions
│   │   ├── services/       # Business logic (ride, fare, payment, SOS…)
│   │   ├── types/          # TypeScript type definitions
│   │   ├── utils/          # Helpers, constants, ApiError, ApiResponse
│   │   ├── validators/     # Zod request validators
│   │   └── __tests__/      # Unit tests (Jest + ts-jest)
│   ├── .env.example        # Required environment variables template
│   ├── jest.config.ts
│   ├── jest.setup.ts         # Global test mocks (uuid, metrics, rate limiter)
│   ├── package.json
│   ├── tsconfig.json          # IDE config (VS Code, noEmit, includes test files)
│   └── tsconfig.build.json    # Build config (compilation, rootDir: ./src)
│
├── docs/
│   ├── design/             # All UI/UX design files
│   │   ├── chalo-component-library.html
│   │   ├── chalo-customer-screens.html
│   │   ├── chalo-design-system.html
│   │   ├── chalo-design-tokens.json
│   │   ├── chalo-driver-screens.html
│   │   ├── chalo-prototype.html
│   │   ├── chalo-spec-sheet.html
│   │   ├── chalo-user-flows.html
│   │   └── chalo-wireframes.html
│   └── product/
│       └── chalo-product-documentation.md
│
├── NEXT_STEPS.md           # Detailed development roadmap
├── README.md               # This file
└── tsconfig.json           # Root project references (for VS Code)
```

---

## Backend Stack

| Layer | Technology |
|---|---|
| Runtime | Node.js 20+ |
| Language | TypeScript 5.3 (strict) |
| Framework | Express 4 |
| ORM | Prisma 5 |
| Database | PostgreSQL (PostGIS) |
| Auth | Firebase Admin SDK |
| Push Notifications | FCM (Firebase Cloud Messaging) |
| Realtime Location | Firebase Realtime Database |
| File Storage | Firebase Storage |
| Payments | Razorpay (UPI + webhooks) |
| Maps | Google Maps Directions + Places APIs |
| Validation | Zod (every endpoint) |
| Logging | Winston (JSON prod / colorized dev) |
| Security | Helmet, CORS, HPP, per-endpoint rate limiting |
| Testing | Jest + ts-jest (154 tests, 8 suites, 100% passing) |
| Circuit Breaker | opossum (Razorpay API protection) |
| Metrics | prom-client (Prometheus, custom business metrics) |

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
- PostgreSQL ≥ 14
- A Firebase project (for auth + FCM)
- A Razorpay account (for payments)

### Setup

```bash
# 1. Install dependencies
cd chalo-backend
npm install

# 2. Copy and fill environment variables
cp .env.example .env
# Edit .env with your DB URL, Firebase creds, Razorpay keys, Google Maps key

# 3. Run database migrations
npx prisma migrate dev --name init

# 4. Seed platform config
npx prisma db:seed

# 5. Start development server
npm run dev
```

### Environment Variables (`.env`)

```env
DATABASE_URL="postgresql://USER:PASSWORD@localhost:5432/chalo_dev?schema=public"
PORT=3000
NODE_ENV=development

# Firebase
FIREBASE_PROJECT_ID=
FIREBASE_CLIENT_EMAIL=
FIREBASE_PRIVATE_KEY=
FIREBASE_DATABASE_URL=
FIREBASE_STORAGE_BUCKET=

# Razorpay
RAZORPAY_KEY_ID=
RAZORPAY_KEY_SECRET=
RAZORPAY_WEBHOOK_SECRET=

# Google Maps
GOOGLE_MAPS_API_KEY=
```

### Useful Commands

```bash
npm run dev            # Start dev server (hot reload)
npm run build          # Compile TypeScript → dist/
npm run lint           # ESLint
npm test               # Run all 154 tests (unit + integration)
npm run db:studio      # Open Prisma Studio (DB GUI)
npm run db:migrate     # Run new migrations
npm run db:seed        # Seed/reseed platform config
```

---

## API Endpoints (Customer)

| Method | Path | Description |
|---|---|---|
| POST | `/api/v1/auth/send-otp` | Send OTP to phone |
| POST | `/api/v1/auth/verify-otp` | Verify OTP + get token |
| POST | `/api/v1/auth/complete-profile` | Set name / language |
| GET | `/api/v1/auth/me` | Current user profile |
| POST | `/api/v1/auth/emergency-contact` | Update emergency contact |
| POST | `/api/v1/rides/fare-estimate` | Get fare before booking |
| POST | `/api/v1/rides` | Create on-demand ride |
| POST | `/api/v1/rides/schedule` | Schedule a future ride |
| GET | `/api/v1/rides/:id` | Ride details |
| GET | `/api/v1/rides` | Ride history |
| POST | `/api/v1/rides/:id/cancel` | Cancel a ride |
| POST | `/api/v1/rides/:id/rate` | Rate a completed ride |
| POST | `/api/v1/payments/order` | Create Razorpay order |
| POST | `/api/v1/payments/verify` | Verify UPI payment |
| POST | `/api/v1/notifications` | Get notifications |
| PATCH | `/api/v1/notifications/:id/read` | Mark notification read |

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

See [SECURITY_PERFORMANCE_REVIEW.md](SECURITY_PERFORMANCE_REVIEW.md) for full details and [CODE_REVIEW.md](CODE_REVIEW.md) for the complete review.

---

## What's Next

See [NEXT_STEPS.md](NEXT_STEPS.md) for the detailed, step-by-step development roadmap.
