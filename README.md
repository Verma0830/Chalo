# Chalo 🛵

> Punjab-focused bike ride-hailing platform — production-ready backend, a fully-tested Android customer app, and a driver app in active development.

**Chalo** ("Let's go" in Punjabi/Hindi) is a complete ride-hailing system built for two-wheeler taxis in Punjab. It covers the full lifecycle: OTP-based phone auth, real-time driver dispatch via FCM, live GPS tracking through Firebase RTDB, OTP-verified ride starts, Razorpay payment processing, SOS alerts, KYC document workflows, and an admin control plane.

The codebase is a monorepo with three primary modules: a Node.js/TypeScript REST API (`chalo-backend`), an Android customer app (`chalo-customer-app`), and an Android driver app (`chalo-driver-app`).

---

## Table of Contents

- [Current Status](#current-status)
- [Repository Layout](#repository-layout)
- [Architecture Overview](#architecture-overview)
- [Ride Lifecycle](#ride-lifecycle)
- [Backend Stack](#backend-stack)
- [Android Customer App Stack](#android-customer-app-stack)
- [Quick Start — Backend](#quick-start--backend)
- [Quick Start — Android Customer App](#quick-start--android-customer-app)
- [Environment Variables](#environment-variables)
- [Platform Config Keys](#platform-config-keys)
- [Feature Flags](#feature-flags)
- [Running Tests](#running-tests)
- [CI Pipeline](#ci-pipeline)
- [Known Gaps](#known-gaps)
- [Documentation Index](#documentation-index)
- [Contributing](#contributing)

---

## Current Status

| Component | State |
| --- | --- |
| Backend API | **60 endpoints** across 7 route groups — 321/321 tests passing |
| Database | Prisma + PostgreSQL 16 + PostGIS 3.4, 10 migrations applied |
| Android customer app | Full feature implementation — Compose screens, DI, Room, Firebase RTDB + FCM. 53 unit tests |
| Android driver app | In active development (`chalo-driver-app/`) |
| CI | GitHub Actions — backend (lint + test + build) + Android (lint + unit tests + APK) on every push to `main` |

Last updated: March 2026.

---

## Repository Layout

```
Chalo/
├── chalo-backend/              Node.js + TypeScript REST API
│   ├── prisma/                 DB schema, 10 migrations, seed script
│   └── src/
│       ├── config/             env, database, redis, firebase, logger
│       ├── middleware/         auth, validate, idempotency, rateLimiter, sanitize, errorHandler
│       ├── routes/             auth, ride, driver, payment, notification, admin, track
│       ├── controllers/        Thin HTTP layer — delegates to services
│       ├── services/           Business logic (auth, ride, driver, fare, payment, sos, kyc, admin)
│       ├── jobs/               BullMQ queue + worker definitions
│       ├── validators/         Zod schemas for every route
│       └── utils/              constants, apiError, helpers
│
├── chalo-customer-app/         Android customer app (Kotlin + Jetpack Compose)
│   └── app/src/main/java/com/chalo/customer/
│       ├── di/                 NetworkModule, DatabaseModule, RepositoryModule (Hilt)
│       ├── data/               Remote (Retrofit APIs, DTOs, AuthInterceptor)
│       │                       Local (Room DB, DAOs, DataStore preferences)
│       │                       Repositories (impl)
│       ├── domain/             Pure Kotlin models + repository interfaces
│       └── presentation/
│           └── screens/        auth, home, activeride, postride, history,
│                               profile, notifications, scheduled (+ ViewModels)
│
├── chalo-driver-app/           Android driver app (in development)
│
├── docs/
│   ├── CODEBASE.md             Full architecture reference — all 60 endpoints, screens, data flows
│   ├── api/                    POSTMAN_GUIDE.md, CUSTOMER_APP_API_GUIDE.md, token-exchange-guide.md
│   ├── development/            NEXT_STEPS.md, EMULATOR_SETUP.md, FRIEND_SETUP.md,
│   │                           IMPROVEMENTS.md, BACKEND_FLOWS.md, BACKUP_DISASTER_RECOVERY.md,
│   │                           INCIDENT_RUNBOOK.md
│   ├── reviews/                CODE_REVIEW.md, SECURITY_PERFORMANCE_REVIEW.md
│   ├── design/                 UI/UX HTML mockups
│   └── product/                chalo-product-documentation.md
│
├── CHANGELOG.md
├── package.json                Root workspace scripts
└── tsconfig.json
```

---

## Architecture Overview

```
┌──────────────────┐     OTP + Firebase Auth      ┌─────────────────────┐
│  Customer App    │ ◄──────────────────────────► │                     │
│  (Android)       │                              │   chalo-backend     │
└──────────────────┘     REST /api/v1             │   Node.js/TS        │
                                                  │   Port 3001         │
┌──────────────────┐     REST /api/v1             │                     │
│  Driver App      │ ◄──────────────────────────► │   PostgreSQL 16     │
│  (Android)       │                              │   + PostGIS 3.4     │
└──────────────────┘                              │                     │
                                                  │   Redis 7 + BullMQ  │
┌──────────────────┐     Admin REST /api/v1       │                     │
│  Admin Panel     │ ◄──────────────────────────► │   Firebase Admin    │
│  (HTTP client)   │                              │   + RTDB + FCM      │
└──────────────────┘                              └─────────────────────┘

Real-time driver location: Firebase RTDB (read by customer app during active ride)
Push notifications:        Firebase Cloud Messaging (FCM) — ride offers, status updates
Payments:                  Razorpay (UPI + webhook verification)
SMS OTP:                   MSG91 (production) / console log (development)
KYC:                       Pluggable — ManualKYCProvider (default) or SurepassKYCProvider
Observability:             OpenTelemetry + Prometheus client + Winston
```

---

## Ride Lifecycle

```
REQUESTED ──► DRIVER_ASSIGNED ──► DRIVER_ARRIVED ──► IN_PROGRESS ──► COMPLETED
                                                                  └──► CANCELLED

REQUESTED ──► NO_DRIVER   (broadcast exhausted, no driver accepted)
SCHEDULED               (pending dispatch — transitions to REQUESTED at scheduled time)
```

**Driver dispatch flow:** When a ride is created, the top-5 nearest online + KYC-approved drivers within the initial search radius receive a simultaneous FCM push. Each batch has a 30-second acceptance window. If no one accepts, a BullMQ delayed job fires and dispatches the next batch at an expanded radius (default 12 km). Redis coordinates offer state and prevents double-assignment.

**Cancellation fee:** Applied only when status is `DRIVER_ARRIVED` or later (default ₹40). Driver-fault reason codes (`DRIVER_ASKED_TO_CANCEL`, `DRIVER_NOT_MOVING`, `DRIVER_WRONG_VEHICLE`, `DRIVER_BEHAVIOUR`) waive the fee regardless of status.

---

## Backend Stack

| Layer | Technology |
| --- | --- |
| Runtime | Node.js 20 |
| Framework | Express 4 |
| Language | TypeScript (strict mode) |
| ORM | Prisma 5 |
| Database | PostgreSQL 16 + PostGIS 3.4 |
| Cache / Queues | Redis 7 + BullMQ |
| Auth / Realtime / Push | Firebase Admin SDK + RTDB + FCM |
| Payments | Razorpay |
| Validation | Zod |
| Logging | Winston |
| Metrics | Prometheus client |
| Tracing | OpenTelemetry |
| SMS | MSG91 |
| KYC | Surepass (optional) / Manual |
| Tests | Jest + ts-jest — 321 passing |

---

## Android Customer App Stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository (Clean Architecture) |
| DI | Hilt + KSP |
| Networking | Retrofit 2 + OkHttp 4 + Gson |
| Local DB | Room 2 |
| Preferences | DataStore |
| Auth | Firebase Auth (custom token flow) |
| Real-time | Firebase RTDB (driver location + ride status) |
| Push | Firebase Cloud Messaging |
| Maps | Google Maps Compose |
| Location | Play Services Location (FusedLocationProviderClient) |
| Tests | JUnit 4 + MockK + Turbine + kotlinx-coroutines-test — 53 passing |
| Min SDK | API 28 (Android 9 Pie) |
| Target/Compile SDK | API 34 |

---

## Quick Start — Backend

**Prerequisites:** Docker Desktop running (starts PostgreSQL + Redis containers automatically).

```bash
cd chalo-backend

# First-time setup
npx prisma migrate deploy
npm run db:seed

# Daily development (hot reload via ts-node-dev)
npm run dev
```

Health check: `http://localhost:3001/health`

> **Important:** Always use `prisma migrate deploy`, not `prisma migrate dev`. Shadow DB creation fails with PostGIS on Windows.

Database URL (local): `postgresql://postgres:luffy@localhost:5433/chalo_db?schema=public`

For required `.env` values, see [docs/development/FRIEND_SETUP.md](docs/development/FRIEND_SETUP.md).

### Local infrastructure

| Container | External port | Purpose |
| --- | --- | --- |
| `chalo-postgres` | 5433 | PostgreSQL + PostGIS |
| `chalo-redis` | 6379 | Redis |
| `chalo-api` | 5000 | Dockerised API (not used in dev) |

Use port **5433** from the host machine. Port **5432** is for container-to-container communication inside Docker.

---

## Quick Start — Android Customer App

**Prerequisites:** Android Studio, Java 17+, a Google Maps API key.

1. Configure `chalo-customer-app/local.properties`:

```properties
sdk.dir=/path/to/Android/sdk
DEV_BASE_URL=http://10.0.2.2:3001/api/v1
MAPS_API_KEY=your_google_maps_api_key
```

2. Open `chalo-customer-app/` in Android Studio.
3. Sync Gradle.
4. Run on an emulator — use a **Google APIs** image, not plain AOSP (Maps + Firebase require Google Play Services).

Full emulator setup: [docs/development/EMULATOR_SETUP.md](docs/development/EMULATOR_SETUP.md)

> **Physical device testing:** Add your host machine's actual IP (e.g. `192.168.1.105`) to `network_security_config.xml` — the placeholder network addresses (`192.168.0.0`, `192.168.1.0`) do not work.

---

## Environment Variables

Create `chalo-backend/.env` with the following keys. See [docs/development/FRIEND_SETUP.md](docs/development/FRIEND_SETUP.md) for full instructions.

| Variable | Required | Description |
| --- | --- | --- |
| `DATABASE_URL` | ✅ | PostgreSQL connection string |
| `REDIS_URL` | ✅ | Redis connection string |
| `FIREBASE_PROJECT_ID` | ✅ | Firebase project ID |
| `FIREBASE_CLIENT_EMAIL` | ✅ | Firebase service account email |
| `FIREBASE_PRIVATE_KEY` | ✅ | Firebase service account private key |
| `FIREBASE_DATABASE_URL` | ✅ | Firebase RTDB URL |
| `RAZORPAY_KEY_ID` | ✅ | Razorpay API key |
| `RAZORPAY_KEY_SECRET` | ✅ | Razorpay API secret |
| `RAZORPAY_WEBHOOK_SECRET` | ✅ | Razorpay webhook HMAC secret |
| `MSG91_AUTH_KEY` | ✅ (prod) | MSG91 SMS API key |
| `MSG91_TEMPLATE_ID` | ✅ (prod) | MSG91 OTP template ID |
| `INTERNAL_API_KEY` | ✅ | Key for `x-internal-api-key` admin promote endpoint |
| `SUREPASS_API_KEY` | ❌ | Enables Surepass KYC provider (ManualKYCProvider used if absent) |
| `NODE_ENV` | ✅ | `development` or `production` |
| `PORT` | ❌ | API port (default: 3001) |

---

## Platform Config Keys

Stored in the database and editable via `PUT /admin/config/:key`. Seeded by `npm run db:seed`.

| Key | Default | Description |
| --- | --- | --- |
| `base_fare` | — | Minimum base charge (₹) |
| `per_km_rate` | — | Per-kilometre charge (₹) |
| `booking_fee` | — | Flat booking fee (₹) |
| `minimum_fare` | — | Floor fare (₹) |
| `surge_multiplier` | `1.0` | Peak pricing multiplier |
| `gst_percentage` | `5` | GST applied on top of fare (%) |
| `cancel_fee_arrived_amount` | `40` | Cancellation fee after driver arrived (₹) |
| `driver_search_radius_km` | — | Initial driver broadcast radius (km) |
| `driver_search_radius_km_expanded` | `12` | Expanded broadcast radius after first pass fails (km) |
| `ride_offer_batch_ttl_secs` | — | Acceptance window per broadcast batch (seconds) |
| `serial_cancel_threshold` | — | Consecutive cancellations before customer block |
| `max_schedule_days` | `7` | Maximum days ahead a ride can be scheduled |
| `phantom_driver_ttl_mins` | — | Stale online-driver cleanup threshold (minutes) |

---

## Feature Flags

Feature flags are stored as platform config keys and can be toggled via the admin API.

| Flag key | Default | Description |
| --- | --- | --- |
| `enable_dynamic_surge` | `false` | Enables dynamic surge pricing logic |
| `enable_wallet` | `false` | Enables in-app wallet balance for customers |
| `enable_places_autocomplete` | `false` | Enables Google Places autocomplete in the apps |

---

## Running Tests

```bash
# Backend — 321 tests
cd chalo-backend
npm test

# Android unit tests — 53 tests
gradle -p chalo-customer-app test

# Backend type check only
cd chalo-backend
npx tsc --noEmit
```

### What still needs tests

- **Android instrumentation tests** (`src/androidTest/`) — no Espresso/Compose UI tests exist yet. OTP flow, book ride flow, active ride status updates, and post-ride screens are the priority targets.
- **Backend contract tests** — DTOs in the Android app are manually maintained and not generated from backend Zod schemas. A contract enforcement layer would catch silent nulls on field renames.

---

## CI Pipeline

Every push to `main` triggers:

| Step | What it does |
| --- | --- |
| **Lint** | TypeScript type check + ESLint + `npm audit` |
| **Test** | Jest with live PostgreSQL/PostGIS + Redis (Docker services), `prisma migrate deploy`, coverage upload |
| **Build** | TypeScript compile, verifies `dist/server.js` exists |
| **Android** | Android lint + JVM unit tests + `assembleDebug` APK, artifact uploaded |

> **Note:** `gradlew` is not committed in `chalo-customer-app`. The workflow uses a configured Gradle version. Keep Gradle and AGP versions aligned when upgrading Android build tooling.

---

## Known Gaps

| Gap | Severity | Notes |
| --- | --- | --- |
| No Android instrumentation tests | High | UI/navigation regressions can slip through without emulator-level automation |
| DTO contract fragility | Medium | Android DTOs are not generated from backend Zod schemas — field renames produce silent nulls |
| `network_security_config.xml` IP entries | Low | Lists network addresses, not host IPs — each dev must add their own machine IP manually |
| Documentation lag risk | Low | Docs are manually maintained; route or schema changes require manual updates to stay in sync |

See [docs/development/NEXT_STEPS.md](docs/development/NEXT_STEPS.md) for the full prioritised backlog, and [docs/development/IMPROVEMENTS.md](docs/development/IMPROVEMENTS.md) for the feature improvement queue.

---

## Documentation Index

| Document | What it covers |
| --- | --- |
| [docs/CODEBASE.md](docs/CODEBASE.md) | Authoritative architecture reference — all 60 endpoints, Android screens, data flows, migrations |
| [docs/api/POSTMAN_GUIDE.md](docs/api/POSTMAN_GUIDE.md) | How to test every backend endpoint from scratch — 10 complete journeys |
| [docs/api/CUSTOMER_APP_API_GUIDE.md](docs/api/CUSTOMER_APP_API_GUIDE.md) | Screen-by-screen API calls the Android customer app makes |
| [docs/api/token-exchange-guide.md](docs/api/token-exchange-guide.md) | How to exchange a Firebase custom token for an ID token in Postman |
| [docs/development/EMULATOR_SETUP.md](docs/development/EMULATOR_SETUP.md) | Android emulator setup, `local.properties`, common problems |
| [docs/development/FRIEND_SETUP.md](docs/development/FRIEND_SETUP.md) | New developer onboarding guide |
| [docs/development/BACKEND_FLOWS.md](docs/development/BACKEND_FLOWS.md) | Backend lifecycle flows — auth, ride, driver broadcast |
| [docs/development/NEXT_STEPS.md](docs/development/NEXT_STEPS.md) | Remaining work with priority and impact |
| [docs/development/IMPROVEMENTS.md](docs/development/IMPROVEMENTS.md) | Prioritised feature and quality improvement backlog |
| [docs/development/BACKUP_DISASTER_RECOVERY.md](docs/development/BACKUP_DISASTER_RECOVERY.md) | Backup cadence, restore drill, RPO/RTO targets |
| [docs/development/INCIDENT_RUNBOOK.md](docs/development/INCIDENT_RUNBOOK.md) | Step-by-step incident response playbook |
| [docs/reviews/CODE_REVIEW.md](docs/reviews/CODE_REVIEW.md) | Full code quality review — 15 backend + 15 Android findings |
| [docs/reviews/SECURITY_PERFORMANCE_REVIEW.md](docs/reviews/SECURITY_PERFORMANCE_REVIEW.md) | OWASP security + performance findings with remediation notes |
| [docs/product/chalo-product-documentation.md](docs/product/chalo-product-documentation.md) | Product scope, delivery state, user journeys |
| [CHANGELOG.md](CHANGELOG.md) | Chronological release and change notes |

---

## Contributing

1. Clone the repo and follow [docs/development/FRIEND_SETUP.md](docs/development/FRIEND_SETUP.md) to get your local environment running.
2. Run `npm test` in `chalo-backend` and `gradle -p chalo-customer-app test` before opening a PR — the CI will enforce this anyway.
3. For backend changes, update `docs/CODEBASE.md` if you add, remove, or rename endpoints or schema fields.
4. For Android changes, update `docs/api/CUSTOMER_APP_API_GUIDE.md` if screen-to-API mappings change.
5. Use `prisma migrate deploy` (never `migrate dev`) when applying migrations locally.

---

*Chalo — built for Punjab, designed to scale.*
