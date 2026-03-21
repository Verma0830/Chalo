# Running Chalo on Android Emulator

Last updated: 2026-03-21

This guide walks through everything required to get the customer app running against the local backend on an Android emulator. Covers first-time setup and daily workflow.

---

## Prerequisites

| Tool | Required version | Notes |
|---|---|---|
| Android Studio | Hedgehog (2023.1.1) or newer | Includes emulator + SDK manager |
| JDK | 17 | Android Studio bundles one |
| Docker Desktop | Any recent | Must be running before starting backend |
| Node.js | 18+ | For the backend |
| Google Maps API Key | Any | Needed or map screen is blank |

---

## 1. Backend: start the local stack

The Android emulator hits your host machine via `10.0.2.2`. The backend must be running and reachable on port 3001.

**Step 1** — Open Docker Desktop and wait for it to be running. The `chalo-postgres` (port 5433) and `chalo-redis` (port 6379) containers start automatically.

**Step 2** — Start the API server:

```bash
cd chalo-backend
npm run dev
```

Wait for: `Server running on port 3001`

**Verify the backend is alive:**

```
GET http://localhost:3001/health
```

Expected: `{ "status": "ok" }`

> If the server crashes on startup, the most common cause is the database not being ready. Wait 5–10 seconds after Docker Desktop shows the containers as running, then retry.

---

## 2. Android: configure local.properties

Open `chalo-customer-app/local.properties`. It must contain:

```properties
sdk.dir=C\:\\Users\\yadav\\AppData\\Local\\Android\\Sdk
MAPS_API_KEY=<your_real_google_maps_api_key>
DEV_BASE_URL=http://10.0.2.2:3001/api/v1
```

- `DEV_BASE_URL` is already set correctly for emulator use. Do not change it.
- `MAPS_API_KEY` is currently a placeholder. Replace it or the map screen will be blank.

**Getting a Maps API Key:**

1. Go to Google Cloud Console → APIs & Services → Credentials
2. Create API Key → restrict to "Android apps" → add SHA-1 fingerprint of your debug keystore
3. Enable: **Maps SDK for Android** and **Directions API**
4. Paste the key into `local.properties`

---

## 3. Android: create the right emulator

The app requires an emulator with **Google Play Services** (Maps SDK and Firebase both need it).

In Android Studio:

1. Open **Device Manager** (right toolbar or View → Tool Windows → Device Manager)
2. Click **+** → **Create Virtual Device**
3. Pick any phone form factor (Pixel 6 recommended — mid-range baseline)
4. Select a system image:
   - API Level: **28 or higher** (minSdk is 28)
   - ABI: **x86_64**
   - **Important**: choose the image labelled **"Google APIs"** or **"Google Play"** — not plain AOSP
5. Finish and start the emulator

Verify Google Play Services are installed by opening the emulator's app drawer and checking for "Google Play Store" or "Google Play Services".

---

## 4. Build configuration (how it works)

The `build.gradle.kts` reads `local.properties` at build time:

```kotlin
buildConfigField("String", "BASE_URL",
    "\"${localProps["DEV_BASE_URL"] ?: "http://10.0.2.2:3001/api/v1"}\"")
manifestPlaceholders["MAPS_API_KEY"] = localProps["MAPS_API_KEY"] ?: "YOUR_MAPS_API_KEY"
```

- In **debug** builds: `BASE_URL` comes from `DEV_BASE_URL` in `local.properties`.
- In **release** builds: `BASE_URL` is hardcoded to `https://api.chalo.in/api/v1` — `local.properties` is irrelevant.

The `network_security_config.xml` explicitly allows cleartext HTTP to `10.0.2.2`:

```xml
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="false">10.0.2.2</domain>
</domain-config>
```

This is debug-overrides only — release builds disallow cleartext entirely.

---

## 5. Run the app

1. Select your emulator as the target device in Android Studio
2. Click **Run** (green play button) or press `Shift+F10`
3. Android Studio will sync Gradle, compile, and install the APK on the emulator
4. The app launches automatically

First Gradle sync takes several minutes on a fresh clone. Subsequent builds are incremental.

---

## 6. First-launch flow (verify everything is wired)

| Step | What happens | What to watch |
|---|---|---|
| App opens | Splash screen checks Firebase auth state | Should proceed quickly |
| No session | Navigates to Phone Input screen | `FirebaseAuth.currentUser == null` |
| Enter `+919876543210` → Send OTP | App calls `POST /auth/otp/send` | Backend console prints the 4-digit OTP |
| Enter OTP | App calls `POST /auth/otp/verify` → gets `customToken` | Then calls Firebase `signInWithCustomToken` |
| Firebase sign-in succeeds | App saves session in DataStore | Navigates to Complete Profile (new user) or Home (returning) |
| Complete Profile | App calls `PUT /auth/profile` | Navigates to Home screen |
| Home screen | Map loads, fare estimate available | Map tiles require a valid Maps API key |

**Watch the Logcat** in Android Studio (filter by `chalo` or `OkHttp`) to see all API requests and responses in real time.

---

## 7. How auth works in the app (vs Postman)

Postman requires a manual token exchange step. The app does it automatically:

```
OtpVerifyViewModel
  → authRepository.verifyOtp()       # POST /auth/otp/verify → customToken
  → FirebaseAuth.signInWithCustomToken(customToken)  # Firebase SDK
  → FirebaseAuth manages idToken internally

AuthInterceptor (OkHttp)
  → FirebaseAuth.currentUser.getIdToken()   # auto-refreshed by Firebase SDK
  → injects as "Authorization: Bearer <idToken>" on every API request
  → on 401: force-refreshes token once and retries
```

You never handle tokens manually in the app. If you get persistent 401 errors, the Firebase project in `google-services.json` does not match the one used by the backend's `firebase-service-account.json`.

---

## 8. Physical device testing

If you want to test on a real phone on the same WiFi network:

1. Find your machine's local IP: run `ipconfig` → look for IPv4 under your WiFi adapter (e.g. `192.168.1.105`)
2. Update `local.properties`:
   ```
   DEV_BASE_URL=http://192.168.1.105:3001/api/v1
   ```
3. Update `network_security_config.xml` to add your exact IP:
   ```xml
   <domain includeSubdomains="false">192.168.1.105</domain>
   ```
   Note: the existing entries `192.168.0.0` and `192.168.1.0` are network addresses, not host IPs — they do not work as-is.
4. Enable USB debugging on the phone or connect wirelessly via Android Studio's wireless debugging

---

## 9. Common problems

| Symptom | Likely cause | Fix |
|---|---|---|
| App cannot connect to backend | Backend not running or wrong port | Run `npm run dev`, check port 3001 |
| `ECONNREFUSED` in Logcat | Using `localhost` instead of `10.0.2.2` | Check `DEV_BASE_URL` in `local.properties` |
| Map screen blank or crashes | Invalid Maps API key | Add real key to `local.properties` |
| "Authentication failed" on OTP verify | Firebase project mismatch | `google-services.json` and `firebase-service-account.json` must be same project |
| `401` on all API calls | Firebase token not attached | Check `AuthInterceptor` — `FirebaseAuth.currentUser` must be non-null |
| Build fails: "google-services.json not found" | Missing file | File is at `app/google-services.json` — do not move it |
| Emulator has no Google Play Services | Wrong emulator image | Use "Google APIs" or "Google Play" system image |
| Gradle sync fails: "SDK not found" | Wrong `sdk.dir` in `local.properties` | Android Studio sets this automatically on first open |

---

## 10. Daily dev workflow

```
1. Open Docker Desktop  →  postgres + redis containers start automatically
2. cd chalo-backend && npm run dev
3. Open Android Studio, select emulator, hit Run
4. Use Logcat (filter: OkHttp) to watch API traffic
5. Use backend console to read OTPs during auth testing
```

Hot reload: the backend uses `ts-node-dev` with `--respawn` — save any `.ts` file and the server restarts automatically. The Android app requires a rebuild for code changes but Compose previews and Layout Inspector work without one.
