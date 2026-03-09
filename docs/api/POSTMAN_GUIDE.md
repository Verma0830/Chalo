# Chalo Backend — Postman Testing Guide
### Complete beginner guide — start here if you've never used Postman

**Status: Updated March 2026. 58 endpoints. Trip sharing and driver cancellation tracking verified.**

---

## Step 1 — Download and Install Postman

1. Open your browser and go to: **https://www.postman.com/downloads/**
2. Click **Download the App** (Windows 64-bit)
3. Run the installer — it installs automatically
4. Open Postman — you will see a login screen
5. Click **Skip and go to the app** (bottom of the screen — you don't need an account)

---

## Step 2 — What is the Base URL?

The base URL is the address of our backend server running on your laptop.

When you run `npm run dev` inside `chalo-backend`, the server starts on port `3001`.

**Base URL = `http://localhost:3001/api/v1`**

- `localhost` = your own laptop
- `3001` = the port our server runs on
- `/api/v1` = all our API routes start with this

> If your friend is testing on his laptop, the base URL is the same — `http://localhost:3001/api/v1` — because the server is running locally on his machine too.

---

## Step 3 — Start the Backend Server First

Before doing anything in Postman, the server must be running.

Open a terminal in VS Code (`Ctrl + ~`) and run:

```
docker start chalo-db
cd chalo-backend
npm run dev
```

You should see this in the terminal:
```
INFO: Server running on port 3001
INFO: PostgreSQL connected
INFO: Redis connected
```

**Leave this terminal open.** The server must keep running while you test.

> The terminal is where you will also see OTP codes and log messages — keep an eye on it.

---

## Step 4 — Set Up the Postman Environment

An "Environment" in Postman is like a set of saved variables (like `token`, `rideId`) that you can reuse across requests instead of typing them every time.

### 4a. Create the Environment

1. In Postman, look at the **top-right corner** — you'll see a dropdown that says **"No Environment"**
2. Click the **eye icon** next to that dropdown
3. Click **"Add"** or **"Create Environment"**
4. Name it: `Chalo Dev`
5. Add the following rows (click "Add a new variable" for each):

| Variable Name    | Initial Value                          | Current Value                          |
|-----------------|----------------------------------------|----------------------------------------|
| `base_url`      | `http://localhost:3001/api/v1`         | `http://localhost:3001/api/v1`         |
| `customer_token`| *(leave empty)*                        | *(leave empty)*                        |
| `driver_token`  | *(leave empty)*                        | *(leave empty)*                        |
| `admin_token`   | *(leave empty)*                        | *(leave empty)*                        |
| `ride_id`       | *(leave empty)*                        | *(leave empty)*                        |
| `share_url`     | *(leave empty)*                        | *(leave empty)*                        |

6. Click **Save**
7. In the top-right dropdown, select **"Chalo Dev"** to activate it

> **What does `{{base_url}}` mean?** — Whenever you type `{{base_url}}` in Postman, it automatically replaces it with `http://localhost:3001/api/v1`. Same for `{{customer_token}}` etc.

---

## Step 5 — How to Make a Request in Postman

Every request has 4 parts. Here's how to fill them in:

### 5a. Method + URL

At the top of Postman you'll see a bar with `GET` and a text field.

- Click `GET` to change the method (GET, POST, PUT, DELETE)
- In the text field, type the full URL, for example:
  ```
  {{base_url}}/auth/otp/send
  ```

### 5b. Headers (for authenticated requests)

1. Click the **Headers** tab (below the URL bar)
2. Add a new row:
   - Key: `Authorization`
   - Value: `Bearer {{customer_token}}`
3. Add another row:
   - Key: `Content-Type`
   - Value: `application/json`

> You only need `Authorization` on routes that require login. The first 2 requests (send OTP, verify OTP) do NOT need it.

### 5c. Body (for POST requests)

1. Click the **Body** tab
2. Select **raw**
3. In the dropdown on the right, change `Text` to **JSON**
4. Type your JSON in the box, for example:
   ```json
   {
     "phone": "+919876543210"
   }
   ```

### 5d. Send and Read the Response

- Click the blue **Send** button
- The response appears in the bottom half of the screen
- Look at:
  - **Status code** (top right of response): `200 OK` = success, `400` = bad request, `401` = not logged in
  - **Body** tab in the response: shows the actual JSON data returned

---

## Step 6 — First Time Setup (Empty Database)

The database starts completely empty — no users, no config, no data. Before running any flow, do these 3 things once.

### 6a. Seed the platform config

The platform config (fares, commission %) must be seeded or every fare calculation will fail.

Open a **second terminal** (keep `npm run dev` running in the first one):

```bash
cd chalo-backend
npm run db:seed
```

You should see:
```
Seeding platform config...
✓ commission_percentage = 15
✓ subscription_fee_weekly = 199
✓ min_fare = 30
✓ base_fare_per_km = 12
✓ base_fare_per_min = 2
✓ settlement_days = 2
Seed complete.
```

You only need to run this **once**. After that the data stays in the DB.

### 6b. Apply the latest migration

```bash
cd chalo-backend
npx prisma migrate deploy
```

You should see:
```
3 migrations found in prisma/migrations
All migrations have been successfully applied.
```

If it says `Can't reach database server` — run `docker start chalo-db` first and try again.

### 6c. What order to do everything in

Because the DB is empty, follow this exact order:

```
1. Seed + migrate (done above — once only)
2. Register a customer  →  Flow 1
3. Register a driver    →  Flow 4
4. Create an admin      →  Flow 6 (do this before approving drivers)
5. Approve the driver   →  Flow 6c (or direct DB shortcut)
6. Run a full ride      →  Flow 5
```

---

## Step 8 — Verify the Server is Working

Before testing anything else, confirm the server is running:

**Request:**
```
Method: GET
URL: http://localhost:3001/health
```
*(No headers, no body)*

**Expected Response:**
```json
{
  "status": "ok",
  "uptime": 12.3,
  "database": "connected",
  "redis": "connected"
}
```

If you see `Could not get any response` — the server is not running. Go back to Step 3.

---

## Flow 1 — Customer Registration (Do this first)

### Request 1 of 3 — Send OTP

```
Method: POST
URL: {{base_url}}/auth/otp/send
Headers: Content-Type: application/json
Body (raw JSON):
{
  "phone": "+919876543210"
}
```

> Phone number rules: must start with `+91` followed by a number starting with 6, 7, 8, or 9, then 9 more digits. Example: `+919876543210`

Click **Send**.

**Expected Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "expiresAt": "2026-03-01T10:05:00.000Z"
  }
}
```

**Now look at the terminal** where `npm run dev` is running. You will see a line like:
```
INFO: OTP sent to +91987****3210 (expires: ...)
```

The actual OTP is printed in the terminal (in dev mode). It's a 4-digit number. **Write it down.**

---

### Request 2 of 3 — Verify OTP

```
Method: POST
URL: {{base_url}}/auth/otp/verify
Headers: Content-Type: application/json
Body (raw JSON):
{
  "phone": "+919876543210",
  "otp": "1234"
}
```

Replace `1234` with the actual OTP you saw in the terminal.

Click **Send**.

**Expected Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
    "isNewUser": true,
    "user": {
      "id": "clxxx...",
      "phone": "+919876543210",
      "role": "CUSTOMER"
    }
  }
}
```

**Save the token:**
1. Copy the entire value of `"token"` (the long string starting with `eyJ`)
2. In Postman, click the **eye icon** (top right, Environment)
3. Click **Edit** next to `Chalo Dev`
4. Find the `customer_token` row
5. Paste the token into the **Current Value** column
6. Click **Save**

Now `{{customer_token}}` will automatically send this token in all future requests.

---

### Request 3 of 3 — Complete Profile

This is only required for new users (`isNewUser: true` in the previous response).

```
Method: POST
URL: {{base_url}}/auth/profile
Headers:
  Content-Type: application/json
  Authorization: Bearer {{customer_token}}
Body (raw JSON):
{
  "name": "Rahul Kumar",
  "email": "rahul@example.com"
}
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "id": "clxxx...",
    "name": "Rahul Kumar",
    "phone": "+919876543210"
  }
}
```

Customer registration is done.

---

## Flow 2 — Fare Estimate

```
Method: POST
URL: {{base_url}}/rides/fare-estimate
Headers:
  Content-Type: application/json
  Authorization: Bearer {{customer_token}}
Body (raw JSON):
{
  "pickup": {
    "lat": 28.4089,
    "lng": 77.3178,
    "address": "Sector 15, Faridabad"
  },
  "drop": {
    "lat": 28.4595,
    "lng": 77.3023,
    "address": "NIT Faridabad"
  }
}
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "estimatedFare": 85,
    "distanceKm": 6.2,
    "estimatedMins": 18,
    "breakdown": {
      "distanceFare": 74,
      "timeFare": 36,
      "bookingFee": 5,
      "surgeMultiplier": 1
    }
  }
}
```

> If Google Maps API key is not set, the server uses Haversine distance (straight-line approximation). The fare will still be calculated — just slightly less accurate. This is fine for testing.

---

## Flow 3 — Book a Ride

### Request 1 of 2 — Create Ride

```
Method: POST
URL: {{base_url}}/rides
Headers:
  Content-Type: application/json
  Authorization: Bearer {{customer_token}}
Body (raw JSON):
{
  "pickup": {
    "lat": 28.4089,
    "lng": 77.3178,
    "address": "Sector 15, Faridabad"
  },
  "drop": {
    "lat": 28.4595,
    "lng": 77.3023,
    "address": "NIT Faridabad"
  },
  "paymentMethod": "CASH"
}
```

**Expected Response (201 Created):**
```json
{
  "success": true,
  "data": {
    "rideId": "clyyy...",
    "status": "REQUESTED",
    "estimatedFare": 85
  }
}
```

**Save the ride ID:**
1. Copy the value of `"rideId"`
2. Open Environment → Edit → find `ride_id` row
3. Paste into **Current Value** → Save

### Request 2 of 2 — Get Ride Details

```
Method: GET
URL: {{base_url}}/rides/{{ride_id}}
Headers:
  Authorization: Bearer {{customer_token}}
```

**Expected Response:** Full ride object with `status: "REQUESTED"` (if no driver found yet) or `status: "DRIVER_ASSIGNED"` (if a driver accepted).

---

## Flow 4 — Driver Registration

Use a **different phone number** than the customer. Open a new tab in Postman.

### Step 1 — Send OTP (Driver)

```
Method: POST
URL: {{base_url}}/auth/otp/send
Headers: Content-Type: application/json
Body:
{
  "phone": "+919999988888"
}
```

Check terminal for the OTP.

### Step 2 — Verify OTP (Driver)

```
Method: POST
URL: {{base_url}}/auth/otp/verify
Headers: Content-Type: application/json
Body:
{
  "phone": "+919999988888",
  "otp": "XXXX"
}
```

Copy the token → save it in the `driver_token` environment variable.

### Step 3 — Complete Profile (Driver)

```
Method: POST
URL: {{base_url}}/auth/profile
Headers:
  Content-Type: application/json
  Authorization: Bearer {{driver_token}}
Body:
{
  "name": "Suresh Singh"
}
```

### Step 4 — Submit Documents

```
Method: POST
URL: {{base_url}}/driver/documents
Headers:
  Content-Type: application/json
  Authorization: Bearer {{driver_token}}
Body:
{
  "licenseNumber": "HR1120230012345",
  "licenseUrl": "https://example.com/license.jpg",
  "rcNumber": "HR11AB1234",
  "rcUrl": "https://example.com/rc.jpg",
  "aadharNumber": "123412341234",
  "aadharUrl": "https://example.com/aadhar.jpg",
  "vehicleNumber": "HR 11 AB 1234",
  "vehicleModel": "Honda Activa 6G",
  "bikePhotoUrl": "https://example.com/bike.jpg"
}
```

> For testing you can use fake URLs — the server saves the URL string, it doesn't download the image.

### Step 5 — Approve the Driver (via Admin or direct DB)

The driver cannot go online until their status is `VERIFIED`.

**Option A — via Admin (proper way, see Flow 6c below)**

**Option B — directly in DB (quick way for testing):**

Open a terminal and run:
```bash
docker exec -it chalo-db psql -U postgres -d chalo
```

Then run this SQL (replace `<driver-user-id>` with the `id` you got from the verify OTP response):
```sql
UPDATE driver_profiles
SET "verificationStatus" = 'VERIFIED', "verifiedAt" = NOW()
WHERE "userId" = '<driver-user-id>';

-- Press Enter, then type:
\q
```

---

## Flow 5 — Complete Ride (End to End)

You need both the customer and driver registered and the driver approved. Keep two Postman tabs open.

### Step 1 — Driver Goes Online

```
Method: POST
URL: {{base_url}}/driver/online
Headers:
  Content-Type: application/json
  Authorization: Bearer {{driver_token}}
Body:
{
  "lat": 28.4089,
  "lng": 77.3178
}
```

The driver must be near the pickup location (within 5km). Use the same coordinates as the customer's pickup.

### Step 2 — Customer Creates a Ride

See Flow 3, Request 1. Save the `rideId`.

Look at the terminal — you'll see:
```
INFO: Broadcast driver search initiated { rideId: "...", totalCandidates: 1, firstBatchSize: 1 }
INFO: Batch dispatched { rideId: "...", driverCount: 1 }
```

### Step 3 — Driver Checks for Incoming Ride

```
Method: GET
URL: {{base_url}}/driver/rides/incoming
Headers:
  Authorization: Bearer {{driver_token}}
```

**Expected Response (200 OK):**
```json
{
  "data": {
    "rideId": "clyyy...",
    "pickup": { "address": "Sector 15, Faridabad", "lat": 28.4089, "lng": 77.3178 },
    "drop": { "address": "NIT Faridabad" },
    "fare": 85,
    "distanceKm": 6.2
  }
}
```

If it returns `null` — the ride offer has expired (65 second window). Create a new ride and check faster.

### Step 4 — Driver Accepts the Ride

```
Method: POST
URL: {{base_url}}/driver/rides/{{ride_id}}/accept
Headers:
  Authorization: Bearer {{driver_token}}
```

**Expected Response (200 OK):** status becomes `DRIVER_ASSIGNED`

### Step 5 — Driver Arrives at Pickup

```
Method: POST
URL: {{base_url}}/driver/rides/{{ride_id}}/arrived
Headers:
  Content-Type: application/json
  Authorization: Bearer {{driver_token}}
Body:
{
  "lat": 28.4089,
  "lng": 77.3178
}
```

The driver must be within 200 meters of the pickup. Use the exact same coordinates.

### Step 6 — Driver Starts the Ride

```
Method: POST
URL: {{base_url}}/driver/rides/{{ride_id}}/start
Headers:
  Authorization: Bearer {{driver_token}}
```

### Step 7 — Driver Completes the Ride

```
Method: POST
URL: {{base_url}}/driver/rides/{{ride_id}}/complete
Headers:
  Content-Type: application/json
  Authorization: Bearer {{driver_token}}
Body:
{
  "note": "Smooth ride, no issues"
}
```

**Expected Response (200 OK):** status becomes `COMPLETED`

### Step 8 — Customer Rates the Ride

```
Method: POST
URL: {{base_url}}/rides/{{ride_id}}/rate
Headers:
  Content-Type: application/json
  Authorization: Bearer {{customer_token}}
Body:
{
  "rating": 5,
  "comment": "Very smooth ride!"
}
```

---

## Flow 6 — Admin Panel

### How to Create an Admin User (Step by Step)

The database starts with no users at all. You need to create one via Postman, then promote it to ADMIN via the database, then log in again to get an admin token.

---

#### Step 1 — Register a new user (your admin account)

Use a phone number you control. This will become your admin account.

```
Method: POST
URL: {{base_url}}/auth/otp/send
Headers: Content-Type: application/json
Body:
{
  "phone": "+918800000001"
}
```

Check the terminal for the OTP code. It will print something like:
```
INFO: OTP sent to +91880****0001 (expires: ...)
```

---

#### Step 2 — Verify the OTP

```
Method: POST
URL: {{base_url}}/auth/otp/verify
Headers: Content-Type: application/json
Body:
{
  "phone": "+918800000001",
  "otp": "XXXX"
}
```

Replace `XXXX` with the 4-digit code from the terminal.

**Expected Response:**
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGci...",
    "isNewUser": true,
    "user": {
      "id": "clabcdef1234567890",
      "phone": "+918800000001",
      "role": "CUSTOMER"
    }
  }
}
```

**Write down the phone number you used.** You will need it in Step 4.

---

#### Step 3 — Complete the profile

```
Method: POST
URL: {{base_url}}/auth/profile
Headers:
  Content-Type: application/json
  Authorization: Bearer (paste the token from Step 2 directly here)
Body:
{
  "name": "Admin User"
}
```

---

#### Step 4 — Promote this user to ADMIN via API

No SQL or database access needed. Call the bootstrap endpoint directly in Postman:

```
Method: POST
URL: {{base_url}}/admin/promote
Headers:
  Content-Type: application/json
  x-internal-api-key: chalo-internal-dev-key-change-in-prod
Body:
{
  "phone": "+918800000001"
}
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "message": "User promoted to ADMIN",
  "data": {
    "id": "clabcdef1234567890",
    "phone": "+918800000001",
    "role": "ADMIN"
  }
}
```

> **Why can't I just use the token I already have?**
> The token is created at login time and the role is baked into it. Since you were `CUSTOMER` when you logged in, that token has `CUSTOMER` role. After promoting to ADMIN, you must log in again to get a new token that has `ADMIN` role.

---

#### Step 5 — Log in again to get your ADMIN token

Send OTP again with the same phone:

```
Method: POST
URL: {{base_url}}/auth/otp/send
Headers: Content-Type: application/json
Body:
{
  "phone": "+918800000001"
}
```

Check terminal for the new OTP code.

Then verify it:

```
Method: POST
URL: {{base_url}}/auth/otp/verify
Headers: Content-Type: application/json
Body:
{
  "phone": "+918800000001",
  "otp": "XXXX"
}
```

**Expected Response:**
```json
{
  "data": {
    "token": "eyJhbGci...",
    "user": {
      "role": "ADMIN"
    }
  }
}
```

Check that `role` says `"ADMIN"` this time. If it still says `CUSTOMER` — the SQL update didn't work, go back to Step 4.

**Save the token:**
1. Copy the `token` value
2. Open Environment (eye icon top right) → Edit → find `admin_token`
3. Paste into **Current Value** → Save

You now have a working admin token. Use `{{admin_token}}` in all admin requests below.

---

### 6a. List Pending Drivers

```
Method: GET
URL: {{base_url}}/admin/drivers/pending
Headers:
  Authorization: Bearer {{admin_token}}
```

Query params (optional — click **Params** tab in Postman):

| Key   | Value |
|-------|-------|
| page  | 1     |
| limit | 20    |

### 6b. Get Full Driver Detail

First you need the driver's user ID. Get it from the list above (it's the `userId` field in each result).

```
Method: GET
URL: {{base_url}}/admin/drivers/PASTE_DRIVER_USER_ID_HERE
Headers:
  Authorization: Bearer {{admin_token}}
```

### 6c. Approve a Driver

```
Method: POST
URL: {{base_url}}/admin/drivers/PASTE_DRIVER_USER_ID_HERE/approve
Headers:
  Content-Type: application/json
  Authorization: Bearer {{admin_token}}
Body:
{
  "note": "All documents verified manually"
}
```

### 6d. Reject a Driver

```
Method: POST
URL: {{base_url}}/admin/drivers/PASTE_DRIVER_USER_ID_HERE/reject
Headers:
  Content-Type: application/json
  Authorization: Bearer {{admin_token}}
Body:
{
  "reason": "License image is blurry. Please resubmit a clearer photo."
}
```

### 6e. View Live Rides

```
Method: GET
URL: {{base_url}}/admin/rides/live
Headers:
  Authorization: Bearer {{admin_token}}
```

### 6f. View Platform Config

```
Method: GET
URL: {{base_url}}/admin/config
Headers:
  Authorization: Bearer {{admin_token}}
```

Expected: list of all config keys like `commission_percentage`, `min_fare`, etc.

### 6g. Update Platform Config

```
Method: PUT
URL: {{base_url}}/admin/config/commission_percentage
Headers:
  Content-Type: application/json
  Authorization: Bearer {{admin_token}}
Body:
{
  "value": "18"
}
```

Valid keys you can update:

| Key                       | What it controls                          | Default |
|---------------------------|-------------------------------------------|---------|
| `commission_percentage`   | Platform cut per ride (%)                 | 15      |
| `subscription_fee_weekly` | Driver weekly plan (₹)                    | 199     |
| `min_fare`                | Minimum ride fare (₹)                     | 30      |
| `base_fare_per_km`        | Rate per kilometer (₹)                    | 12      |
| `base_fare_per_min`       | Rate per minute (₹)                       | 2       |
| `surge_enabled`           | Turn surge pricing on/off                 | false   |
| `surge_multiplier`        | Surge multiplier (1.0–2.0)                | 1.0     |
| `settlement_days`         | Payout delay (T+N days)                   | 2       |
| `free_cancel_window_secs` | Seconds before cancellation fee kicks in  | 120     |
| `cancel_fee_amount`       | Cancellation fee in ₹ (after window)      | 20      |

---

---

## Flow 7 — Driver Registration (New — Replaces Manual SQL)

Drivers now register via a single API call that verifies OTP and creates their account atomically.

### Step 1 — Send OTP (same as customer)

```
Method: POST
URL: {{base_url}}/auth/otp/send
Headers: Content-Type: application/json
Body:
{
  "phone": "+918800000002"
}
```

Check terminal for the OTP code.

### Step 2 — Register as Driver

```
Method: POST
URL: {{base_url}}/auth/register-driver
Headers: Content-Type: application/json
Body:
{
  "phone": "+918800000002",
  "otp": "XXXX",
  "name": "Ravi Kumar"
}
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "message": "Driver registered successfully",
  "data": {
    "isNewUser": true,
    "token": "<firebase-custom-token>",
    "user": {
      "id": "...",
      "phone": "+918800000002",
      "name": "Ravi Kumar",
      "role": "DRIVER"
    }
  }
}
```

Copy the `token` value from the response, exchange it for a Firebase ID token using the steps in [docs/api/token-exchange-guide.md](docs/api/token-exchange-guide.md), then save that ID token as `driver_token` in your Postman environment. The user is created with `DRIVER` role and a `DriverProfile` is automatically created — no manual SQL needed.

---

## Flow 8 — OTP Ride Start

The ride start flow now requires a 4-digit OTP that the customer receives when a driver is assigned.

### What happens automatically

1. Driver calls `POST /driver/rides/:rideId/accept` → customer receives FCM notification with an OTP like `"Ride OTP: 4821"`
2. Customer shows OTP to driver
3. Driver enters OTP when starting the ride

### Start Ride with OTP

```
Method: POST
URL: {{base_url}}/driver/rides/{{ride_id}}/start
Headers:
  Content-Type: application/json
  Authorization: Bearer {{driver_token}}
Body:
{
  "otp": "4821"
}
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "message": "Ride started",
  "data": {
    "rideId": "...",
    "status": "IN_PROGRESS",
    "startedAt": "2026-03-08T10:30:00.000Z"
  }
}
```

If the OTP is wrong:
```json
{ "success": false, "message": "Invalid OTP. Ask the customer to share their ride OTP.", "code": "INVALID_OTP" }
```

---

## Flow 9 — Driver Rates Customer

After ride completion, the driver can rate the customer (separate from customer rating the driver).

```
Method: POST
URL: {{base_url}}/driver/rides/{{ride_id}}/rate-customer
Headers:
  Content-Type: application/json
  Authorization: Bearer {{driver_token}}
Body:
{
  "rating": 5,
  "comment": "Very polite, on time at pickup"
}
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "message": "Customer rated successfully",
  "data": {
    "rideId": "...",
    "rating": 5,
    "comment": "Very polite, on time at pickup"
  }
}
```

- `rating`: 1–5 (required)
- `comment`: optional, max 300 characters
- Can only be called once per ride (returns 409 if already rated)

---

## Flow 10 — Ride Receipt

Get the full fare breakdown for a completed ride.

```
Method: GET
URL: {{base_url}}/rides/{{ride_id}}/receipt
Headers:
  Authorization: Bearer {{customer_token}}
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "rideId": "...",
    "route": {
      "pickup": "Sector 16A, Faridabad",
      "drop": "BPTP Park Centra, Faridabad",
      "distanceKm": 4.2,
      "durationMins": 18
    },
    "fare": {
      "baseFare": 55.40,
      "surgeMultiplier": 1.0,
      "surgeCharge": 0,
      "totalFare": 55.40
    },
    "payment": {
      "method": "CASH",
      "status": "PENDING",
      "amountPaid": 55.40
    },
    "driver": {
      "name": "Ravi Kumar",
      "vehicleNumber": "HR 51 AB 1234",
      "vehicleModel": "Honda Activa 6G",
      "rating": 4.7
    },
    "customerRating": 5
  }
}
```

Only works for `COMPLETED` rides owned by the authenticated customer.

---

## Flow 11 — Driver Earnings Summary

Get a lightweight summary for the driver dashboard card.

```
Method: GET
URL: {{base_url}}/driver/earnings/summary?period=week
Headers:
  Authorization: Bearer {{driver_token}}
```

Query params:
- `period`: `week` (last 7 days) or `month` (last 30 days)

**Expected Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "period": "week",
    "rides": 23,
    "grossEarnings": 3450.00,
    "commission": 517.50,
    "netEarnings": 2932.50,
    "avgPerRide": 127.50,
    "allTimeRides": 142,
    "allTimeEarnings": 18600.00,
    "ratingAvg": 4.6
  }
}
```

---

## Flow 12 — Cancellation Fee

If a customer cancels after a driver is assigned and the 2-minute free window has passed, a cancellation fee is returned.

### Cancel a ride with a driver assigned (after 2 minutes)

```
Method: POST
URL: {{base_url}}/rides/{{ride_id}}/cancel
Headers:
  Content-Type: application/json
  Authorization: Bearer {{customer_token}}
Body:
{
  "reason": "Changed plans"
}
```

**Response within 2-minute window (no fee):**
```json
{
  "data": { "rideId": "...", "status": "CANCELLED", "cancellationFee": 0 }
}
```

**Response after 2-minute window (fee applies):**
```json
{
  "data": { "rideId": "...", "status": "CANCELLED", "cancellationFee": 20 }
}
```

The fee amount is configurable via `PUT /admin/config/cancel_fee_amount`. The window is configurable via `PUT /admin/config/free_cancel_window_secs`.

---

## Flow 13 — Trip Share / Public Tracking Link

Create a shareable link for an active ride, then open the public tracking endpoint without any token.

### Request 1 of 2 — Create the tracking link

```
Method: POST
URL: {{base_url}}/rides/{{ride_id}}/share
Headers:
  Authorization: Bearer {{customer_token}}
```

**Expected Response (201 Created):**
```json
{
  "success": true,
  "message": "Tracking link created",
  "data": {
    "rideId": "...",
    "shareUrl": "http://localhost:3001/api/v1/track/abcDEF123...",
    "expiresAt": "2026-03-10T17:35:00.000Z"
  }
}
```

Copy the full `shareUrl` into the Postman environment as `share_url`.

### Request 2 of 2 — Open the public tracking link

```
Method: GET
URL: {{share_url}}
Headers: none
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "data": {
    "rideId": "...",
    "status": "IN_PROGRESS",
    "isTrackingActive": true,
    "pickup": {
      "lat": 28.4089,
      "lng": 77.3178,
      "address": "Sector 15, Faridabad"
    },
    "drop": {
      "lat": 28.4595,
      "lng": 77.3023,
      "address": "NIT Faridabad"
    },
    "driver": {
      "name": "Ravi Kumar",
      "vehicleNumber": "HR26AB1234",
      "vehicleModel": "Honda Activa"
    },
    "location": {
      "lat": 28.421,
      "lng": 77.331,
      "updatedAt": "2026-03-09T17:36:00.000Z"
    }
  }
}
```

Notes:
- This endpoint is public by design. Do **not** send `Authorization`.
- The link only works while it is unexpired and not revoked.
- Once the ride is completed or cancelled, the same link still returns final status, but `location` becomes `null`.

---

## Flow 14 — Driver Cancellation Tracking Threshold

Verify that driver-side cancellation returns tracking stats and triggers the threshold flag when the driver cancels too many rides in a day.

```
Method: POST
URL: {{base_url}}/driver/rides/{{ride_id}}/cancel
Headers:
  Content-Type: application/json
  Authorization: Bearer {{driver_token}}
Body:
{
  "reason": "Bike issue"
}
```

**Expected Response (200 OK):**
```json
{
  "success": true,
  "message": "Ride cancelled",
  "data": {
    "rideId": "...",
    "status": "CANCELLED",
    "cancellationStats": {
      "total": 3,
      "today": 3,
      "alertThreshold": 3,
      "alertTriggered": true
    }
  }
}
```

What to verify:
- `today` increments on every driver-side cancel in the same IST day.
- `alertTriggered` flips to `true` when `today === 3`.
- Admin users get an in-app SYSTEM notification when the threshold is hit.

---

## Common Errors and What They Mean

| Status Code | Error Code            | What went wrong | How to fix |
|------------|----------------------|-----------------|------------|
| `400`      | `VALIDATION_ERROR`   | Wrong field name, wrong type, missing required field | Check the Body JSON — look at the `data` array in the response for which field failed |
| `401`      | `UNAUTHORIZED`       | Token is missing or expired | Add `Authorization: Bearer {{customer_token}}` header, or re-login and get a fresh token |
| `403`      | `FORBIDDEN`          | You are logged in but don't have permission | Customer trying a driver endpoint, or driver trying admin endpoint |
| `404`      | `RIDE_NOT_FOUND`     | Wrong `rideId` | Check the `ride_id` environment variable has been set |
| `409`      | `RIDE_ALREADY_ACTIVE`| Customer already has an ongoing ride | Cancel the existing ride first |
| `429`      | `TOO_MANY_REQUESTS`  | OTP rate limit — too many OTP requests | Wait 5 minutes |

---

## Checklist — Ready for Customer App?

Go through these in order. Put a tick next to each when it passes.

**One-time setup:**
- [ ] `docker start chalo-db` runs without error
- [ ] `npx prisma migrate deploy` → "All migrations successfully applied"
- [ ] `npm run db:seed` → shows all config keys seeded
- [ ] `GET http://localhost:3001/health` → returns `status: ok, database: connected`

**Create test data:**
- [ ] Flow 1 passed — customer OTP → token → profile complete
- [ ] Flow 6 (Admin creation) — promoted a user to ADMIN, got admin token with `role: ADMIN`
- [ ] Flow 4 passed — driver registered and documents submitted
- [ ] Flow 6c passed — admin approved the driver via API

**Verify ride lifecycle:**
- [ ] Flow 2 passed — fare estimate returns a number
- [ ] Flow 3 passed — ride created with `status: REQUESTED`
- [ ] Flow 5 passed — full ride completed end-to-end (online → accept → arrive → start with OTP → complete → rate)
- [ ] Flow 9 passed — driver rated customer after completion
- [ ] Flow 10 passed — ride receipt returns fare breakdown
- [ ] Flow 13 passed — trip share link opens public tracking data
- [ ] Flow 14 passed — driver cancellation stats increment and threshold triggers

**Verify new features:**
- [ ] Flow 7 passed — driver registered via API (no SQL needed)
- [ ] Flow 8 passed — ride start requires and validates OTP
- [ ] Flow 11 passed — driver earnings summary returns totals
- [ ] Flow 12 tested — cancellation fee returned correctly

**All 17 checked = backend is ready. Start the customer app.**
