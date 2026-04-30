param(
  [string]$BackupDir = "./backups",
  [int]$RetentionDays = 14
)

if (-not $env:DATABASE_URL) {
  Write-Error "DATABASE_URL is required"
  exit 1
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null
$backupFile = Join-Path $BackupDir "chalo_$timestamp.sql.gz"

$dumpBytes = & pg_dump --dbname="$env:DATABASE_URL" --format=plain --no-owner --no-privileges 2>$null
if ($LASTEXITCODE -ne 0) {
  Write-Error "pg_dump failed"
  exit 1
}

$tmpSql = Join-Path $BackupDir "chalo_$timestamp.sql"
$dumpBytes | Out-File -Encoding utf8 $tmpSql

gzip -f $tmpSql
Move-Item "$tmpSql.gz" $backupFile -Force
Write-Host "Backup created: $backupFile"

$cutoff = (Get-Date).AddDays(-$RetentionDays)
Get-ChildItem -Path $BackupDir -Filter "chalo_*.sql.gz" | Where-Object { $_.LastWriteTime -lt $cutoff } | Remove-Item -Force
Write-Host "Old backups older than $RetentionDays days removed"
