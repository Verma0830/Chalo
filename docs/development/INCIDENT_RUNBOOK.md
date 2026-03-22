# Incident Runbook

Last updated: 2026-03-22

## Severity levels

- SEV-1: customer safety or platform-wide outage
- SEV-2: major user-facing degradation
- SEV-3: limited feature degradation

## 1. Payment webhook failure

Symptoms:
- `paymentStatus` remains `PENDING` after successful Razorpay checkout

Checks:
- Inspect backend logs for `/payments/webhook` failures
- Verify `x-razorpay-signature` and webhook secret configuration

Mitigation:
1. Validate payment using Razorpay dashboard by `razorpay_payment_id`.
2. Call payment verification endpoint if safe to replay.
3. Update ride payment status manually only after payment source confirmation.

## 2. SOS not delivered

Symptoms:
- Customer triggers SOS but emergency contact did not receive alert

Checks:
- Inspect `sos_alerts` record and `alertSentTo` payload
- Check SMS provider logs (MSG91) and FCM logs

Mitigation:
1. Contact emergency number manually from support channel.
2. Mark alert as resolved only after human confirmation.
3. File post-incident ticket with delivery failure details.

## 3. Ride stuck in REQUESTED

Symptoms:
- Customer waits indefinitely, no assignment, no NO_DRIVER transition

Checks:
- Verify BullMQ workers are healthy
- Inspect Redis keys for `ride:candidates:*`, `ride:active_batch:*`
- Check `ride-offer-expired` job execution in logs

Mitigation:
1. Re-dispatch search if worker recovered and candidates remain.
2. If exhausted, move ride to `NO_DRIVER` and notify customer.
3. Capture queue metrics/logs for root-cause analysis.

## 4. Redis outage

Symptoms:
- Rate limiting inconsistent
- Queue and cache behavior degraded

Checks:
- `GET /health` redis status
- Redis process/service health and connectivity

Mitigation:
1. Restore Redis service first.
2. Confirm workers reconnect and pending jobs are draining.
3. Watch auth and ride endpoints for elevated latency/error rates.

## Communication template

- Start: "Incident detected, impact under investigation, next update in 15 minutes."
- Update: include affected flows, mitigation status, ETA.
- End: include root cause, fix summary, and preventive action owner.
