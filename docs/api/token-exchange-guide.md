# Firebase Token Exchange Guide

## Why This Step Is Needed

Every time you complete OTP authentication, the backend returns a **custom token**. This happens for both `/auth/otp/verify` and `/auth/register-driver`. This custom token **cannot** be used directly in API requests.

You must exchange it for a **Firebase ID token** using the step below. The ID token is what you save in Postman as `customer_token` or `driver_token`.

> ⚠️ Do this every time you verify an OTP — for both customers and drivers.

---

## Steps to Exchange Token

### 1. Copy the custom token
After calling `/auth/otp/verify` or `/auth/register-driver`, copy the value of `"token"` from the response.

### 2. Create a new request in Postman

- **Method:** `POST`
- **URL:**
```
https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=XXXXX-Your_API_KEY
```
- **Body:** raw → JSON
```json
{
  "token": "paste your custom token here",
  "returnSecureToken": true
}
```

### 3. Send the request

You will get a `200 OK` response with an `idToken` field — this is your real Firebase ID token.

### 4. Save the ID token in Postman

- Click the **eye icon** (top right, next to environment dropdown)
- Click **Edit** next to `Chalo Dev`
- Paste the `idToken` value into:
  - `customer_token` — if this was a customer OTP
  - `driver_token` — if this was a driver OTP
- Click **Save**

---

## Summary Flow

```
Send OTP → Verify OTP or Register Driver → Get custom token
→ Exchange at identitytoolkit.googleapis.com
→ Get idToken
→ Save as customer_token or driver_token in Postman
```

> ⏱️ ID tokens expire after **1 hour**. If you get a `401 Unauthorized` error, repeat this flow to get a fresh token.
