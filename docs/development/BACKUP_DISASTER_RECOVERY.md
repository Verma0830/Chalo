# Backup and Disaster Recovery

Last updated: 2026-03-22

## Objectives

- RPO (Recovery Point Objective): 24 hours
- RTO (Recovery Time Objective): 2 hours

## Daily backup

1. Ensure `pg_dump` is installed on the host/runner.
2. Set `DATABASE_URL` for the production or staging DB.
3. Run one of the scripts:

```bash
cd chalo-backend
DATABASE_URL="postgresql://..." ./scripts/backup-db.sh
```

```powershell
cd chalo-backend
$env:DATABASE_URL = "postgresql://..."
./scripts/backup-db.ps1
```

4. Upload backup artifacts (`backups/chalo_*.sql.gz`) to durable object storage (S3, GCS, or equivalent).

## Restore procedure (monthly drill)

1. Create an empty restore database.
2. Decompress the selected backup file.
3. Restore:

```bash
gunzip -c backups/chalo_YYYYMMDD_HHMMSS.sql.gz | psql "postgresql://..."
```

4. Run migrations in deploy mode:

```bash
cd chalo-backend
DATABASE_URL="postgresql://..." npx prisma migrate deploy
```

5. Run smoke checks:
- `GET /health`
- OTP send/verify flow
- Ride creation and cancellation flow

## Retention

- Keep daily backups for 14 days.
- Keep weekly backups for 8 weeks.
- Keep monthly backups for 12 months.

## Notes

- The scripts only generate local backup files and cleanup old local files.
- Upload/replication is environment-specific and should be handled by CI/CD or infra automation.
