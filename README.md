# Chalo

Punjab-focused bike ride-hailing platform. Production-ready backend with a fully-tested Android customer app.

## Current Status (March 2026)

- Backend API: **60 endpoints** across auth, rides, driver, payments, notifications, admin, and public tracking. 321/321 tests passing.
- Database: Prisma + PostgreSQL/PostGIS, 10 migrations applied.
- Android customer app: full feature implementation — Compose screens, navigation, DI, repositories, API clients, Room, Firebase RTDB + FCM integration. 53 unit tests across 6 test classes.
- CI: GitHub Actions pipeline — backend (lint + test + build) and Android (lint + unit tests + debug APK) run on every push to `main`.

## Repository Layout

```
chalo-backend/          Node.js + TypeScript backend
chalo-customer-app/     Android customer app (Kotlin + Jetpack Compose)
docs/                   All documentation
  api/                  POSTMAN_GUIDE.md, token-exchange-guide.md
  development/          NEXT_STEPS.md, EMULATOR_SETUP.md, BACKEND_FLOWS.md, FRIEND_SETUP.md
  reviews/              CODE_REVIEW.md, SECURITY_PERFORMANCE_REVIEW.md
  product/              chalo-product-documentation.md
  CODEBASE.md           Full architecture and endpoint reference
```

## Backend Stack

| Layer | Technology |
|---|---|
| Runtime | Node.js 20 |
| Framework | Express 4 |
| Language | TypeScript (strict mode) |
| ORM | Prisma 5 |
| Database | PostgreSQL 16 + PostGIS 3.4 |
| Cache / Queues | Redis 7 + BullMQ |
| Auth / Realtime / Push | Firebase Admin + RTDB + FCM |
| Payments | Razorpay |
| Validation | Zod |
| Logging | Winston |
| Metrics | Prometheus client |
| Tests | Jest + ts-jest (321 passing) |

## Android Customer App Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt + KSP |
| Networking | Retrofit 2 + OkHttp 4 + Gson |
| Local DB | Room 2 |
| Auth | Firebase Auth (custom token flow) |
| Real-time | Firebase RTDB (driver location + ride status) |
| Push | Firebase Cloud Messaging |
| Maps | Google Maps Compose |
| Location | Play Services Location (FusedLocationProviderClient) |
| Preferences | DataStore |
| Tests | JUnit 4 + MockK + Turbine + kotlinx-coroutines-test |

## Quick Start — Backend

**Prerequisites:** Docker Desktop running (starts postgres + redis containers automatically).

```bash
cd chalo-backend

# First time setup
npx prisma migrate deploy
npm run db:seed

# Daily dev
npm run dev
```

Health check: `http://localhost:3001/health`

Environment: `chalo-backend/.env` — see [docs/development/FRIEND_SETUP.md](docs/development/FRIEND_SETUP.md) for required values.

Database URL (local): `postgresql://postgres:luffy@localhost:5433/chalo_db?schema=public`

## Quick Start — Android App

**Prerequisites:** Android Studio, local.properties configured.

```
chalo-customer-app/local.properties:
  sdk.dir=/path/to/Android/sdk
  DEV_BASE_URL=http://10.0.2.2:3001/api/v1
  MAPS_API_KEY=your_google_maps_api_key
```

1. Open `chalo-customer-app/` in Android Studio.
2. Sync Gradle.
3. Run on emulator (use Google APIs image, not AOSP).

Full emulator setup: [docs/development/EMULATOR_SETUP.md](docs/development/EMULATOR_SETUP.md)

## Running Tests

```bash
# Backend (321 tests)
cd chalo-backend
npm test

# Android unit tests (53 tests)
gradle -p chalo-customer-app test
```

## CI Pipeline

Every push to `main` runs:

1. **Lint** — TypeScript type check + ESLint + npm audit
2. **Test** — Jest with live PostgreSQL/PostGIS + Redis (Docker services), prisma migrate deploy, coverage upload
3. **Build** — TypeScript compile, verify `dist/server.js` exists
4. **Android** — Android lint + JVM unit tests + assembleDebug APK, artifact upload

## Documentation Index

| Document | What it covers |
|---|---|
| [docs/CODEBASE.md](docs/CODEBASE.md) | Full architecture reference — all 60 endpoints, Android screens, data flow, migrations |
| [docs/api/POSTMAN_GUIDE.md](docs/api/POSTMAN_GUIDE.md) | How to test every backend endpoint from scratch, 10 journeys |
| [docs/api/CUSTOMER_APP_API_GUIDE.md](docs/api/CUSTOMER_APP_API_GUIDE.md) | Screen-by-screen API calls the Android customer app makes |
| [docs/api/token-exchange-guide.md](docs/api/token-exchange-guide.md) | Firebase token exchange for Postman testing |
| [docs/development/EMULATOR_SETUP.md](docs/development/EMULATOR_SETUP.md) | Android emulator setup, local.properties, common problems |
| [docs/development/NEXT_STEPS.md](docs/development/NEXT_STEPS.md) | Remaining work with priority and impact |
| [docs/development/FRIEND_SETUP.md](docs/development/FRIEND_SETUP.md) | New developer onboarding |
| [docs/development/BACKEND_FLOWS.md](docs/development/BACKEND_FLOWS.md) | Backend lifecycle flows (auth, ride, driver broadcast) |
| [docs/development/BACKUP_DISASTER_RECOVERY.md](docs/development/BACKUP_DISASTER_RECOVERY.md) | Backup cadence, restore drill, RPO/RTO targets |
| [docs/development/INCIDENT_RUNBOOK.md](docs/development/INCIDENT_RUNBOOK.md) | Step-by-step incident response playbook |
| [CHANGELOG.md](CHANGELOG.md) | Chronological release and change notes |
| [docs/reviews/CODE_REVIEW.md](docs/reviews/CODE_REVIEW.md) | Full code quality review — 15 backend + 15 Android findings |
| [docs/reviews/SECURITY_PERFORMANCE_REVIEW.md](docs/reviews/SECURITY_PERFORMANCE_REVIEW.md) | Security (OWASP) + performance findings with fixes |
| [docs/development/IMPROVEMENTS.md](docs/development/IMPROVEMENTS.md) | Prioritised feature and quality improvements backlog |
| [docs/product/chalo-product-documentation.md](docs/product/chalo-product-documentation.md) | Product scope, delivery state, user journeys |
