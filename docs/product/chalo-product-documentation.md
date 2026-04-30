# Chalo Product Documentation

Last updated: 2026-03-21

## 1) Product summary

Chalo is a Punjab-focused ride-hailing product with bike ride scope in V1.

Core outcomes:

- customer can book rides and track lifecycle
- driver can accept and complete rides
- admin can monitor and operate platform workflows

## 2) Current delivery snapshot

### Completed summary

- Backend lifecycle and platform APIs are broadly implemented.
- Customer Android app has major screen coverage and architecture in place.
- Core integrations are present in codebase (Firebase, maps, payment rails).

### In-progress summary

- Android customer app stabilization and end-to-end quality hardening.
- Android test coverage establishment.
- Documentation cleanup and launch-readiness process alignment.

## 3) V1 scope (practical)

- OTP auth and profile completion
- Fare estimate and ride booking
- Scheduled rides
- Active ride tracking
- Cancellation, rating, receipt
- Notification center
- Emergency/SOS trigger
- Driver ride operations and earnings endpoints
- Admin moderation and platform config

## 4) Out of scope for immediate launch

- iOS app
- advanced growth/loyalty systems
- deep demand forecasting and dynamic surge intelligence
- full automated ops tooling for every edge case

## 5) User personas (condensed)

- Customer commuter: wants quick booking, predictable fare, and safety.
- Driver partner: wants reliable demand and clear ride actions.
- Operator/admin: wants verification, live ops visibility, and configuration controls.

## 6) Product quality gaps to close

- Automated Android testing is the largest gap.
- Contract verification matrix between app and backend needs periodic refresh.
- Release checklist and incident runbook should be formalized before public rollout.

## 7) Next product steps

1. Stabilize customer app critical path end-to-end.
2. Add Android automated tests for high-risk journeys.
3. Complete pre-release operational runbook.
4. Freeze API contracts and update API examples.
5. Execute beta pilot in controlled geography.

## 8) Done vs pending policy in docs

This product doc now keeps a compact done summary and avoids repeating deep historical implementation logs. Detailed technical history belongs in code and review docs.