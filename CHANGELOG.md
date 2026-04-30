# Changelog

All notable project changes are documented here.

## 2026-03-22

### Backend
- Added OpenTelemetry bootstrap and queue trace-context propagation for BullMQ ride jobs.
- Added platform feature-flag keys (`enable_dynamic_surge`, `enable_wallet`, `enable_places_autocomplete`) and seeded defaults.
- Wired dynamic surge rollout to `enable_dynamic_surge` platform config.
- Added Hindi (`hi`) support in complete-profile language validation.

### Android
- Enabled Room schema export for migration tracking.
- Added Room migration registry (`DatabaseMigrations`) and removed destructive migration fallback from DB builder.
- Strengthened ProGuard keep rules for DTO class members.

### DevOps / Docs
- Added DB backup scripts (`scripts/backup-db.sh`, `scripts/backup-db.ps1`).
- Added backup/restore runbook and incident response runbook docs.
- Updated CI/docs references, API guide examples, and backlog status docs.
