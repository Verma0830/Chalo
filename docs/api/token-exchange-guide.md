# Firebase Token Exchange Guide

Last updated: 2026-03-21

## Why this step exists

For protected backend routes, you must send a Firebase ID token in Authorization header.

If your auth flow returns a custom token, exchange it with Firebase Identity Toolkit first.

## Exchange flow

1. Complete OTP endpoint flow.
2. Copy custom token from response payload.
3. Call Identity Toolkit:

POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=<WEB_API_KEY>

Body:
{
  "token": "<CUSTOM_TOKEN>",
  "returnSecureToken": true
}

4. Read idToken from response.
5. Store idToken in Postman environment variable:
   - customer_token or driver_token or admin_token

## Use token in requests

Authorization: Bearer <idToken>

## Expiry handling

ID tokens expire. If protected APIs return 401:

1. refresh or re-authenticate in client flow
2. update token in Postman
3. retry request

## Important

- Do not send raw custom token directly to protected backend routes.
- Do not commit any API keys or tokens to repository files.