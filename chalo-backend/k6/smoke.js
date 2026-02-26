// ============================================================
// Chalo Backend — k6 Smoke / Load Test
// Run: k6 run k6/smoke.js
// Requires: k6 installed (https://k6.io/docs/getting-started/installation/)
//
// k6 runtime globals (__ENV, __VU, __ITER) are declared in
// .eslintrc.json under overrides → k6/**/*.js → globals.
// ============================================================

import http from 'k6/http';
import { check, sleep } from 'k6';

// -------------------------------------------------------
// Configuration — override via env: k6 run -e BASE_URL=...
// -------------------------------------------------------

const BASE_URL = __ENV.BASE_URL || 'http://localhost:5000';

export const options = {
  // Smoke test: 5 VUs for 30 seconds
  stages: [
    { duration: '10s', target: 5 },   // Ramp up
    { duration: '20s', target: 5 },   // Sustain
    { duration: '5s', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],  // 95% of requests under 500ms
    http_req_failed: ['rate<0.01'],    // Less than 1% request failures
  },
};

// -------------------------------------------------------
// Scenario: Health Check
// Validates the server is responsive under load
// -------------------------------------------------------

export default function () {
  // Health check
  const healthRes = http.get(`${BASE_URL}/health`);
  check(healthRes, {
    'health: status is 200 or 503': (r) => r.status === 200 || r.status === 503,
    'health: body has status field': (r) => JSON.parse(r.body).status !== undefined,
    'health: response < 200ms': (r) => r.timings.duration < 200,
  });

  // Unauthenticated endpoint — should return 401
  const ridesRes = http.get(`${BASE_URL}/api/v1/rides/history`);
  check(ridesRes, {
    'rides: returns 401 without auth': (r) => r.status === 401,
  });

  // OTP send with invalid phone — should return 400
  const otpRes = http.post(
    `${BASE_URL}/api/v1/auth/otp/send`,
    JSON.stringify({ phone: 'invalid' }),
    { headers: { 'Content-Type': 'application/json' } }
  );
  check(otpRes, {
    'otp: validation returns 400': (r) => r.status === 400,
  });

  // 404 handling
  const notFoundRes = http.get(`${BASE_URL}/api/v1/nonexistent`);
  check(notFoundRes, {
    '404: returns 404': (r) => r.status === 404,
  });

  sleep(0.5);
}
