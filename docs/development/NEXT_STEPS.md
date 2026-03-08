# Chalo — Development Roadmap & Next Steps

> Last updated: March 2026
> Backend: ✅ Complete — server running, DB live, all migrations applied
> Review score: **~8.0/10** (up from 6.63/10)
> Database: ✅ Running (Docker PostGIS, local)
> Customer App: ⬜ Not started
> Driver App: ⬜ Not started

---

## Current Status Summary

| Component | Status | Notes |
|---|---|---|
| Backend API (Node.js + Express) | ✅ Done | 41 endpoints across 5 route groups |
| Database schema (Prisma + PostgreSQL) | ✅ Done | 11 tables, 11 enums, PostGIS + spatial indexes |
| PostgreSQL + PostGIS (local Docker) | ✅ Running | `docker start chalo-db` · port 5432 |
| Redis | ✅ Running | localhost:6379 |
| Firebase Admin SDK | ✅ Connected | Service account loaded from JSON |
| API Server | ✅ Running | http://localhost:3001/api/v1 |
| Auth service (Firebase OTP) | ✅ Done | Hashed OTP storage, transactional verification, Redis cache |
| Fare / ride services | ✅ Done | L1+L2+L3 config cache, transactional ride creation, RTDB sync |
| Payment service (Razorpay) | ✅ Done | Raw body webhooks, circuit breaker, ride-order checks |
| Driver API (all endpoints) | ✅ Done | 16 endpoints: online/offline, location, ride lifecycle, earnings, withdrawals |
| PostGIS spatial indexes | ✅ Done | GIST index + partial B-tree indexes applied to local DB |
| Notifications (FCM) | ✅ Done | `messaging.send()` wired + stale-token cleanup |
| SOS service | ✅ Done | MSG91 SMS wired + participant verification |
| k6 load test | ✅ Done | `k6/smoke.js` with thresholds |
| Security review | ✅ Done | 25/25 findings fixed |
| TypeScript strict mode | ✅ Done | 0 errors, dual tsconfig (IDE + build) |
| Docker / docker-compose | ✅ Done | Multi-stage Dockerfile + docker-compose with PostGIS + Redis |
| GitHub Actions CI | ✅ Done | `.github/workflows/ci.yml` — type-check, lint, test, build on every PR |
| BullMQ job queue | ✅ Done | OTP cleanup queue + graceful close on SIGTERM/SIGINT |
| Admin API | ✅ Done | 8 endpoints: driver approval, KYC auto-verify, live rides, config |
| Customer Android app | ⬜ Pending | Step 3 below — **start here** |
| Driver Android app | ⬜ Pending | Step 4 below |
| Deployment / CI-CD | ⬜ Pending | Step 5 below |

---

## Step 0 — Docker + CI ✅ Done

Docker, docker-compose, and GitHub Actions CI are all implemented. The backend runs fully containerized with PostGIS + Redis services. CI runs on every push: type-check → lint → test → build.

---

## Step 0b — Remaining Backend Integrations ✅ Done

All four integrations are implemented and verified (249 tests, 0 TS errors, 0 lint errors).

### FCM Push Send (3 hrs)

`notificationService.sendPushNotification()` already stores notifications in the DB. It needs `messaging.send()` wired in. See `IMPLEMENTATION_ROADMAP.md` Task 1.3 for the exact code.

### Google Maps Directions API (2 hrs)

`fareService.getRouteDetails()` falls back to Haversine. Replace with a real `fetch` call to the Directions API. See `IMPLEMENTATION_ROADMAP.md` Task 1.4.

### SOS SMS via MSG91 (2 hrs)

`sosService.triggerSOS()` creates DB records but doesn't send SMS. Wire in `sendSOSSMS()` to call MSG91. See `IMPLEMENTATION_ROADMAP.md` Task 1.5.

### BullMQ Job Queue (1 day)

Replace `setInterval` OTP cleanup with a BullMQ worker. Add scheduled-ride dispatch job. See `IMPLEMENTATION_ROADMAP.md` Task 2.2.

---

## Step 0c — Originally Step 0 Docker instructions (for reference)

### Add Dockerfile

Create `chalo-backend/Dockerfile`:
```dockerfile
FROM node:20-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package.json .
COPY --from=builder /app/prisma ./prisma
EXPOSE 3000
CMD ["sh", "-c", "npx prisma migrate deploy && node dist/server.js"]
```

### Add docker-compose.yml

Create `docker-compose.yml` at the project root:
```yaml
version: '3.9'
services:
  api:
    build: ./chalo-backend
    ports: ["3000:3000"]
    environment:
      NODE_ENV: development
      DATABASE_URL: postgresql://chalo:chalo@postgres:5432/chalo_dev
      REDIS_URL: redis://redis:6379
    depends_on:
      postgres: { condition: service_healthy }
      redis: { condition: service_healthy }
  postgres:
    image: postgis/postgis:16-3.4-alpine
    environment:
      POSTGRES_DB: chalo_dev
      POSTGRES_USER: chalo
      POSTGRES_PASSWORD: chalo
    ports: ["5432:5432"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U chalo"]
      interval: 5s
      retries: 5
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
```

### Add GitHub Actions CI

Create `.github/workflows/ci.yml`:
```yaml
name: CI
on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: { node-version: '20', cache: 'npm', cache-dependency-path: chalo-backend/package-lock.json }
      - run: cd chalo-backend && npm ci
      - run: cd chalo-backend && npx tsc --noEmit
      - run: cd chalo-backend && npm run lint
      - run: cd chalo-backend && npm test
      - run: cd chalo-backend && npm run build
```

---

## Step 1 — Database Setup ✅ DONE

**Completed March 2026.** Local Docker container running with PostGIS.

### What was done
- Docker container `chalo-db` running `postgis/postgis:15-3.3` on port 5432
- PostGIS extension pre-enabled on the `chalo` database
- Both migrations applied via `prisma migrate deploy`:
  - `20260301011006_init` — all 11 tables + enums + indexes
  - `20260301011007_add_postgis_indexes` — 5 spatial/performance indexes
- Native Windows PostgreSQL stopped (was conflicting on port 5432)
- `.env` updated: `DATABASE_URL=postgresql://postgres:luffy@localhost:5432/chalo?schema=public`

### To restart after reboot
```bash
docker start chalo-db   # start the DB container
cd chalo-backend
npm run dev             # start the API server
```

### Notes for production
- Switch `DATABASE_URL` to Neon / Supabase / Railway Postgres
- Run `npx prisma migrate deploy` on the production DB
- The `CREATE EXTENSION postgis` must be supported by the host (Neon/Supabase both support it)

---

### What's in the database

| Table | Purpose |
|---|---|
| `users` | Shared user record (customers + drivers) |
| `customer_profiles` | Customer-specific data (saved locations) |
| `driver_profiles` | Driver docs, vehicle, plan type, location |
| `rides` | Ride requests and lifecycle |
| `ride_events` | Event log per ride (status changes) |
| `earnings` | Driver earnings per completed ride |
| `withdrawals` | Driver withdrawal requests |
| `sos_alerts` | SOS triggers from active rides |
| `otp_verifications` | Phone OTP records with expiry |
| `notifications` | In-app notification storage |
| `platform_config` | Runtime-configurable business values |

---

## Step 2 — Backend Environment Variables

Most are already configured. Remaining items:

### Firebase ✅ Already connected
Firebase Admin is initialised from `firebase-service-account.json`. The following are enabled:
- **Phone Authentication** — for OTP login
- **Cloud Messaging (FCM)** — for push notifications
- **Realtime Database** — for live driver location sync
- **Storage** — for driver document uploads

Firebase is **free** for development (Spark plan):
- Phone auth: unlimited verifications
- FCM: always free
- Realtime DB: 1 GB / 10 GB bandwidth free
- Storage: 5 GB free

### Razorpay (for UPI payments) — skip for now
Skip until ready for real payments. All Cash rides work without it.
When needed:
1. Sign up at https://dashboard.razorpay.com
2. Settings → API Keys → Generate Test Key
3. For webhook URL use `http://your-server/api/v1/payments/webhook`
4. App URL for test mode: use `http://localhost:3001`
```env
RAZORPAY_KEY_ID=rzp_test_xxxxxxxxxxxx
RAZORPAY_KEY_SECRET=xxxxxxxxxxxxxxxxxxxx
RAZORPAY_WEBHOOK_SECRET=your_webhook_secret
```

### Google Maps (for directions + fare distance)
The app falls back to Haversine when no key is set — fine for dev.
When real fare estimates are needed:
1. Go to https://console.cloud.google.com → enable Directions API
2. Google gives **$200/month free credit** — covers thousands of daily requests
3. Add key to `.env`:
```env
GOOGLE_MAPS_API_KEY=AIzaxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

---

## Step 2b — Admin Panel API ✅ DONE (March 2026)

All 8 admin endpoints are live at `/api/v1/admin/*`. All require `ADMIN` role.

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/admin/drivers/pending` | Drivers with PENDING or UNDER_REVIEW status (FIFO) |
| GET | `/api/v1/admin/drivers/:driverId` | Full driver profile + documents |
| POST | `/api/v1/admin/drivers/:driverId/approve` | Approve driver (optional note) |
| POST | `/api/v1/admin/drivers/:driverId/reject` | Reject driver (required reason) |
| POST | `/api/v1/admin/drivers/:driverId/auto-verify` | Run KYC API (auto-approves if confidence ≥ 0.85) |
| GET | `/api/v1/admin/rides/live` | Live rides: DRIVER_ASSIGNED + DRIVER_ARRIVED + IN_PROGRESS |
| GET | `/api/v1/admin/config` | All platform config values |
| PUT | `/api/v1/admin/config/:key` | Update a single config value |

**To get an ADMIN token:** register normally → promote via SQL → re-login:
```sql
UPDATE users SET role = 'ADMIN' WHERE phone = '+91XXXXXXXXXX';
```

---

## Step 3 — Customer Android App

After the database is running, this is the main build work.

### Tech stack
- **Language**: Kotlin
- **UI**: Jetpack Compose
- **Architecture**: MVVM + Repository pattern
- **Navigation**: Jetpack Navigation Component
- **DI**: Hilt
- **Network**: Retrofit + OkHttp
- **Maps**: Google Maps SDK for Android
- **Auth**: Firebase Auth SDK (phone OTP)
- **Realtime**: Firebase Realtime Database SDK
- **Push**: FCM SDK

### Create the Android project
1. Open Android Studio
2. New Project → Empty Activity (Jetpack Compose)
3. Package name: `com.chalo.customer`
4. Min SDK: API 28 (Android 9.0)
5. Language: Kotlin

### Folder structure to follow
```
app/src/main/
├── java/com/chalo/customer/
│   ├── data/
│   │   ├── api/            # Retrofit API interfaces
│   │   ├── models/         # Data classes (request/response)
│   │   └── repository/     # Data layer (calls API + Firebase)
│   ├── di/                 # Hilt modules
│   ├── ui/
│   │   ├── theme/          # colors.kt, typography.kt, spacing.kt
│   │   ├── auth/           # OTP screens
│   │   ├── home/           # Map + booking screen
│   │   ├── ride/           # Active ride screen
│   │   ├── history/        # Past rides
│   │   └── profile/        # Profile + emergency contact
│   └── utils/              # Extensions, formatters, validators
└── res/
    ├── values/
    │   ├── strings.xml     # English strings
    │   └── strings_pa.xml  # Punjabi strings
    └── ...
```

### Build order (sprints)

**Sprint 1 — Auth flow (1 week)**
1. Splash screen → check if token exists → route to home or login
2. Phone number entry screen (+91 format validation)
3. OTP verification screen (4-digit, auto-read via SMS Retriever)
4. Complete profile screen (name + language preference)
5. Connect to `POST /api/v1/auth/send-otp` and `POST /api/v1/auth/verify-otp`

**Sprint 2 — Home + booking (1–2 weeks)**
1. Google Maps screen with current location
2. Pickup location auto-filled from GPS
3. Destination search using Places Autocomplete
4. Route preview (Directions API)
5. Fare estimate panel → connect to `POST /api/v1/rides/fare-estimate`
6. "Book Ride" button → connect to `POST /api/v1/rides`
7. Payment method selector (UPI / Cash)

**Sprint 3 — Active ride screen (1 week)**
1. Live driver location on map (Firebase RTDB listener)
2. Ride status updates (Looking for driver → Driver assigned → En route → Arrived → Ongoing → Completed)
3. Driver info card (name, photo, rating, vehicle)
4. Cancel button (with reason screen)
5. SOS button (press-and-hold 2 seconds)
6. Call driver button

**Sprint 4 — Post-ride flow (3–4 days)**
1. Payment screen (UPI via Razorpay SDK or cash confirmation)
2. Rating screen (1–5 stars + optional comment)
3. Ride summary card

**Sprint 5 — History + profile (3–4 days)**
1. Past rides list → connect to `GET /api/v1/rides`
2. Ride detail screen
3. Profile screen (name, phone, email, language)
4. Emergency contact screen
5. Saved locations management

**Sprint 6 — Scheduled rides (2–3 days)**
1. Schedule ride screen (date/time picker)
2. Scheduled rides list
3. Cancellation of scheduled rides

**Sprint 7 — Notifications (2 days)**
1. FCM notification handling (background + foreground)
2. In-app notifications list → connect to `GET /api/v1/notifications`
3. Mark as read

**Sprint 8 — Polish + Punjabi (1 week)**
1. Add all Punjabi strings (`strings_pa.xml`)
2. Language toggle in profile
3. Offline state handling (no internet banner)
4. Loading states on every screen
5. Error states + retry flows

---

## Step 4 — Driver Android App

Start this after the customer app Sprint 2 is working (so driver matching has riders to pick up).

### Create the project
Same process as customer app but:
- Package name: `com.chalo.driver`
- Min SDK: API 28

### Build order (sprints)

**Sprint 1 — Auth + document upload (1 week)**
1. Phone OTP login (same as customer)
2. Driver registration form (name, vehicle number, vehicle model)
3. Document upload screen (DL, RC, Aadhaar via Firebase Storage)
4. Verification pending screen (admin approval needed)

**Sprint 2 — Driver home + online toggle (1 week)**
1. Driver home screen with earnings summary
2. Go Online / Go Offline toggle (updates RTDB location)
3. Background location updates while online (Foreground Service)

**Sprint 3 — Ride request flow (1 week)**
1. Incoming ride request card (60-second countdown to accept/decline)
2. Accepted ride → navigation to pickup
3. "I have arrived" button (activates within 200m of pickup)
4. Start ride button (requires OTP from customer)
5. End ride button

**Sprint 4 — Earnings + plan (3–4 days)**
1. Earnings dashboard (today, week, month)
2. Plan type display (commission vs subscription)
3. Subscription renewal screen
4. Withdrawal request screen

**Sprint 5 — History + SOS (2–3 days)**
1. Trip history
2. SOS trigger (same as customer)
3. Profile + document management

---

## Step 5 — Deployment

### Backend deployment options

**Recommended for V1: Railway or Render (simple, affordable)**

**Railway:**
1. Go to https://railway.app
2. New Project → Deploy from GitHub repo
3. Add a PostgreSQL plugin inside the project (auto-provides DATABASE_URL)
4. Set all environment variables in the Railway dashboard
5. Railway auto-detects Node.js and runs `npm start`
6. Custom domain available for free

**Render:**
1. Go to https://render.com
2. New → Web Service → connect your GitHub repo
3. Set root directory to `chalo-backend`
4. Build command: `npm install && npm run build && npx prisma migrate deploy`
5. Start command: `npm start`
6. Add a Render PostgreSQL database (free tier or paid)

**Environment checklist before going live:**
- [ ] `NODE_ENV=production` in all production env vars
- [ ] `DATABASE_URL` points to production DB
- [ ] All Firebase keys from the production Firebase project
- [ ] Razorpay keys switched from `rzp_test_` to `rzp_live_`
- [ ] `RAZORPAY_WEBHOOK_SECRET` configured in Razorpay dashboard with your live API URL
- [ ] Google Maps API key has IP/app restrictions set
- [ ] Run `npx prisma migrate deploy` (not dev) on production
- [ ] Run `npx prisma db:seed` once on production

### CI/CD (GitHub Actions)

Create `.github/workflows/test.yml` to run on every push:
```yaml
name: Test
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
      - run: cd chalo-backend && npm ci
      - run: cd chalo-backend && npm run lint
      - run: cd chalo-backend && npm test
      - run: cd chalo-backend && npm run build
```

---

## Step 6 — Admin Panel (after V1 launch)

A simple web dashboard to manage the platform without touching the DB directly.

**Scope:**
- Approve / reject driver applications (view uploaded documents)
- View all active rides in real time
- Change platform config (commission %, surge, fares)
- View earnings and settlements
- Resolve SOS alerts
- Send push notifications to all users

**Tech stack recommendation:**
- Next.js 14 (App Router)
- Tailwind CSS
- Recharts (dashboards)
- Firebase Auth (admin login)
- Connects to the same backend API

---

## Step 7 — Post-Launch Improvements

Once V1 is live and getting rides, prioritise these based on user feedback:

| Feature | Priority | Why |
|---|---|---|
| Auto-accept rides for drivers | High | Reduces friction |
| Ride sharing (pooling) | Medium | Revenue efficiency |
| Delivery mode (V2) | Medium | Market expansion |
| iOS app | Low | Low iPhone penetration in Faridabad |
| Web booking | Low | Most users are on Android |
| Wallet / Chalo credits | High | Reduces payment friction |
| Referral programme | High | Low-cost user acquisition |
| Driver rating breakdown | Medium | Trust signal |
| Live chat support | Medium | Reduce CS load |
| Multi-city expansion | Low | After Faridabad is profitable |

---

## Quick Reference — Who Builds What

| Task | Responsible |
|---|---|
| Backend API | Done ✅ |
| PostgreSQL setup | Developer (Step 1) |
| Firebase project setup | Developer (Step 2) |
| Razorpay account | Business (Step 2) |
| Google Maps API keys | Developer (Step 2) |
| Customer Android app | Android Developer (Step 3) |
| Driver Android app | Android Developer (Step 4) |
| Backend deployment | DevOps / Developer (Step 5) |
| Admin panel | Full-stack Developer (Step 6) |
| Google Play Store listing | Business + Developer |
| Driver onboarding flow | Business (offline) |

---

## Immediate Next Action

> **Backend is 100% complete (March 2026):**
> - ✅ All 41 endpoints live and tested
> - ✅ 249/249 tests passing, 0 TypeScript errors, 0 lint errors
> - ✅ Admin API: 8 endpoints, KYC pluggable, DRIVER_ARRIVED in live rides
> - ✅ Broadcast driver search: top-5 batch FCM + BullMQ timeout
> - ✅ DB: Docker PostGIS + Redis running
>
> **Pending one-time setup (do these once):**
> 1. `npx prisma migrate deploy` — apply the verificationMetadata migration
> 2. `npm run db:seed` — seed platform config (fares, commission %)
>
> **Next steps in priority order:**
> 1. **Test the API in Postman** — see `docs/api/POSTMAN_GUIDE.md`
> 2. **Start Customer Android app** (Step 3) — all 41 endpoints are live and stable
> 3. **Get Google Maps key** when you want accurate fare estimates (Haversine fallback works for now)
> 4. **Get Razorpay test keys** when ready to test UPI payments (Cash rides work without it)
>
> Both Android teams can build in parallel:
> - **Customer team:** `/api/v1/auth/*`, `/api/v1/rides/*`, `/api/v1/payments/*`, `/api/v1/notifications/*`
> - **Driver team:** `/api/v1/driver/*` (all 16 endpoints)
