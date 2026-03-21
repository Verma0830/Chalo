# Chalo Setup Guide (New Developer)

Last updated: 2026-03-21

## 1) Prerequisites

- Node.js 20+
- Docker Desktop
- Git
- Android Studio (for customer app)
- Firebase service account JSON for backend

## 2) Clone and install

1. Clone repository.
2. Backend install:
   - cd chalo-backend
   - npm install

## 3) Start local infra

Use existing container names from your team setup. If unsure, inspect docker ps -a and align with team naming conventions.

Expected services:

- PostgreSQL with PostGIS
- Redis

## 4) Backend environment

Create chalo-backend/.env with required values:

- DATABASE_URL
- REDIS_URL
- PORT (commonly 3001)
- FIREBASE_SERVICE_ACCOUNT_PATH
- FIREBASE_DATABASE_URL
- Razorpay keys (test keys for local)
- INTERNAL_API_KEY (for /admin/promote)

## 5) Database bootstrap

- npx prisma migrate deploy
- npm run db:seed

## 6) Run backend

- npm run dev

Check:

- http://localhost:3001/health

## 7) Android customer app setup

1. Open chalo-customer-app in Android Studio.
2. Ensure app/local configuration includes:
   - DEV_BASE_URL in local.properties (or use fallback)
   - MAPS_API_KEY in local.properties
3. Sync Gradle and run debug app.

Build facts from app module:

- minSdk 28
- compileSdk/targetSdk 34
- Java/Kotlin target 17
- Debug default backend: http://10.0.2.2:3001/api/v1

## 8) Quick sanity checks

Backend:

- health endpoint responds
- OTP send/verify routes work
- fare estimate route works

Android:

- splash to auth navigation works
- OTP flow reaches home
- fare estimate screen can call backend

## 9) Testing status

- Backend has Jest tests under chalo-backend/src/__tests__.
- Android app currently has no test files in src/test and src/androidTest.

## 10) Common issues

- Docker not running: backend cannot connect to db/redis.
- Invalid Firebase service account path: auth initialization fails.
- Wrong DEV_BASE_URL for emulator/device: API calls fail.
- Missing MAPS_API_KEY: map tiles/features degrade or fail.

## 11) Team guidance

When docs disagree with code, trust:

1. route files
2. schema.prisma
3. app module build + NavGraph + API service interfaces