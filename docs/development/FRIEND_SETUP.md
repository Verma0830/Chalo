# Chalo — Setup Guide for a New Developer

> Follow this guide top-to-bottom on a fresh machine. This gets you from zero to a running backend that looks identical to what your teammate is seeing.

---

## What You Will Have at the End

- PostgreSQL + PostGIS database running in Docker
- Redis running in Docker
- The Chalo API running at `http://localhost:3001/api/v1`
- All 249 tests passing
- Postman ready to test every endpoint

Estimated time: **20–30 minutes** (mostly waiting for downloads).

---

## Step 1 — Install Prerequisites

Install these in order. Each has a link to the official download page.

### 1a. Node.js (version 20 or newer)

- Download from: https://nodejs.org — click the **LTS** button
- Run the installer, click Next through everything
- After install, open a new terminal and run:
  ```
  node --version
  ```
  You should see something like `v20.x.x`. If you see v18 or older, download again.

### 1b. Docker Desktop

This is what runs PostgreSQL and Redis — you do not need to install them separately.

- Download from: https://www.docker.com/products/docker-desktop/
- Install and restart your computer when asked
- After restart, open Docker Desktop from the Start menu
- Wait until you see the **whale icon** in your system tray (bottom-right taskbar area) and it says "Engine running"
- **Every time you use this project, Docker Desktop must be open first**

### 1c. Git

- Check if you already have it: open a terminal and run `git --version`
- If not installed, download from: https://git-scm.com/download/win
- Install with all defaults

### 1d. VS Code (recommended editor)

- Download from: https://code.visualstudio.com
- Install the **TypeScript**, **ESLint**, and **Prisma** extensions (search in the Extensions panel)

---

## Step 2 — Get the Code

Open a terminal (Command Prompt or PowerShell or Windows Terminal) and run:

```bash
git clone <repository-url>
cd Chalo
```

Replace `<repository-url>` with the GitHub URL your teammate shares with you.

---

## Step 3 — Get the Firebase Service Account File

The file `firebase-service-account.json` is **not in the git repo** (it contains private keys). Your teammate must send this file to you directly (WhatsApp, email, USB drive — whatever is convenient).

Once you have it, place it here:

```
Chalo/
└── chalo-backend/
    └── firebase-service-account.json   ← put it here
```

Do not rename it. Do not commit it to git (it is already in `.gitignore`).

---

## Step 4 — Create the Database and Redis Containers

Open Docker Desktop first. Wait for the whale icon to appear in the taskbar.

Then open a terminal and run these two commands (once each, never again):

```bash
# PostgreSQL database with PostGIS support
docker run -d --name chalo-db \
  -e POSTGRES_PASSWORD=luffy \
  -e POSTGRES_DB=chalo \
  -p 5432:5432 \
  postgis/postgis:15-3.3

# Redis cache
docker run -d --name chalo-redis \
  -p 6379:6379 \
  redis:7-alpine
```

To check they are running:
```bash
docker ps
```
You should see both `chalo-db` and `chalo-redis` in the list with status `Up`.

---

## Step 5 — Install Node Dependencies

```bash
cd chalo-backend
npm install
```

This downloads all the packages. Takes 1–2 minutes on first run.

---

## Step 6 — Create the `.env` File

Inside `chalo-backend/`, create a file called `.env` (no extension, just `.env`).

Copy and paste this exactly:

```env
DATABASE_URL="postgresql://postgres:luffy@localhost:5432/chalo?schema=public"
REDIS_URL="redis://localhost:6379"
PORT=3001
NODE_ENV=development

FIREBASE_SERVICE_ACCOUNT_PATH=./firebase-service-account.json
FIREBASE_DATABASE_URL=https://YOUR-PROJECT-DEFAULT-RTDB.firebaseio.com

RAZORPAY_KEY_ID=rzp_test_placeholder
RAZORPAY_KEY_SECRET=placeholder_secret

GOOGLE_MAPS_API_KEY=
SUREPASS_API_KEY=
MSG91_AUTH_KEY=
MSG91_SENDER_ID=CHALO
```

**You must fill in `FIREBASE_DATABASE_URL`.**

How to find it:
1. Go to https://console.firebase.google.com
2. Open the Chalo project (your teammate must add you as a member)
3. Click **Build** → **Realtime Database** in the left sidebar
4. Copy the URL that looks like `https://chalo-xxxxx-default-rtdb.firebaseio.com`
5. Paste it as the value for `FIREBASE_DATABASE_URL` in your `.env`

Everything else can stay as-is for local development.

---

## Step 7 — Set Up the Database

Run these two commands from inside `chalo-backend/`:

```bash
# Apply all database migrations (creates all tables)
npx prisma migrate deploy

# Seed the platform config (fares, commission rates, etc.)
npm run db:seed
```

If `migrate deploy` fails with an error about "extension postgis", run this first:

```bash
# Connect to the DB and enable PostGIS (one-time fix)
docker exec -it chalo-db psql -U postgres -d chalo -c "CREATE EXTENSION IF NOT EXISTS postgis;"
# Then re-run:
npx prisma migrate deploy
```

---

## Step 8 — Start the Server

```bash
npm run dev
```

You should see output like:
```
[info] Server running on port 3001
[info] Database connected
[info] Redis connected
[info] Firebase Admin initialized
```

Test it is working — open your browser and go to:

```
http://localhost:3001/health
```

You should see:
```json
{"status":"ok","timestamp":"..."}
```

If you see that, the backend is running correctly.

---

## Step 9 — Run the Tests

In a new terminal (leave `npm run dev` running in the other one):

```bash
cd chalo-backend
npm test
```

All 249 tests should pass. If any fail, see the troubleshooting section below.

---

## Step 10 — Set Up Postman for API Testing

Follow the guide at [docs/api/POSTMAN_GUIDE.md](../api/POSTMAN_GUIDE.md) — it explains every endpoint with step-by-step Postman instructions. All flows have been verified as of March 2026 — all 41 endpoints confirmed working correctly.

The quick summary:
1. Download Postman from https://www.postman.com/downloads/
2. Base URL is `http://localhost:3001/api/v1`
3. All protected endpoints need `Authorization: Bearer <token>` — you get the token from `/auth/otp/verify`

---

## Daily Workflow (After Setup)

Every day when you start working:

```bash
# 1. Open Docker Desktop — wait for the whale icon
# 2. Start the containers (they stop when you restart your PC)
docker start chalo-db
docker start chalo-redis
# 3. Start the API
cd chalo-backend
npm run dev
```

That's it. The server reloads automatically when you save files.

---

## Troubleshooting

### "Cannot connect to the Docker daemon"
Docker Desktop is not running. Open Docker Desktop from the Start menu and wait for the whale icon.

### "Error: connect ECONNREFUSED 127.0.0.1:5432"
The PostgreSQL container is not running.
```bash
docker start chalo-db
```

### "Error: connect ECONNREFUSED 127.0.0.1:6379"
The Redis container is not running.
```bash
docker start chalo-redis
```

### "P1001: Can't reach database server"
Same as above — DB not running or wrong DATABASE_URL. Check your `.env` file.

### "FirebaseAppError: Failed to parse service account"
The `firebase-service-account.json` is missing, in the wrong folder, or corrupted. Make sure it's in `chalo-backend/firebase-service-account.json`.

### "prisma migrate dev failed / shadow DB error"
Always use `prisma migrate deploy`, never `prisma migrate dev`. The `dev` command fails with PostGIS on Windows.

### "Module not found" or TypeScript errors after migration
After adding new fields to `schema.prisma`, regenerate the Prisma client:
```bash
npx prisma generate
```

### Tests failing with "surge multiplier" or fare errors
The config keys in test mocks must be **lowercase** (e.g., `'base_fare_per_km'`), not uppercase. Check the test file if you recently edited mocks.

### Port 3001 already in use
Another process is using the port. Find and kill it:
```bash
# Windows
netstat -ano | findstr :3001
taskkill /PID <pid-number> /F
```

### Container name already exists ("The container name 'chalo-db' is already in use")
The container exists but is stopped. Just start it instead of creating a new one:
```bash
docker start chalo-db
docker start chalo-redis
```

---

## Key URLs (Once Running)

| URL | What it is |
|---|---|
| `http://localhost:3001/health` | Health check — should return `{"status":"ok"}` |
| `http://localhost:3001/api/v1` | API base path |
| `http://localhost:5555` | Prisma Studio — visual database browser (run `npm run db:studio`) |

---

## Important Rules

1. **Never run `prisma migrate dev`** — always use `prisma migrate deploy`.
2. **Never commit `firebase-service-account.json`** or `.env` — they are in `.gitignore` for a reason.
3. **Always use `npm install`** after pulling new changes (dependencies may have changed).
4. **After pulling schema changes**, run `npx prisma migrate deploy` and `npx prisma generate`.
5. The app runs on **port 3001**, not 3000 or 5000 (Docker Desktop uses 5000 internally).
