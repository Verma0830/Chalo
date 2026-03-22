#!/usr/bin/env bash
set -euo pipefail

# Usage:
#   DATABASE_URL=postgresql://... ./scripts/backup-db.sh
# Optional:
#   BACKUP_DIR=./backups
#   RETENTION_DAYS=14

BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"

if [[ -z "${DATABASE_URL:-}" ]]; then
  echo "DATABASE_URL is required"
  exit 1
fi

mkdir -p "${BACKUP_DIR}"
BACKUP_FILE="${BACKUP_DIR}/chalo_${TIMESTAMP}.sql.gz"

pg_dump --dbname="${DATABASE_URL}" --format=plain --no-owner --no-privileges | gzip > "${BACKUP_FILE}"

echo "Backup created: ${BACKUP_FILE}"

# Cleanup old backups
find "${BACKUP_DIR}" -type f -name "chalo_*.sql.gz" -mtime +"${RETENTION_DAYS}" -delete

echo "Old backups older than ${RETENTION_DAYS} days removed"
