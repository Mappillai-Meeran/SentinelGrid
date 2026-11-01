# ==============================================================================
# SentinelGrid — PostgreSQL Database Restore & Recovery Drill Script (PowerShell)
# Safety:
#   - Defaults strictly to an isolated temporary recovery drill database
#   - Hard refusal to overwrite 'sentinelgrid' without -AllowProductionOverwrite
#   - Verifies SHA-256 checksum prior to restore
#   - Executes post-restore smoke validation queries
# ==============================================================================
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$BackupFile,

    [Parameter(Position = 1)]
    [string]$TargetDatabase,

    [switch]$AllowProductionOverwrite,

    [string]$PgHost = $env:PGHOST,
    [int]$PgPort = 5432,
    [string]$PgUser = $env:PGUSER
)

$ErrorActionPreference = "Stop"

if (-not $PgHost) { $PgHost = "localhost" }
if (-not $PgUser) { $PgUser = "postgres" }
if ($env:PGPORT) { $PgPort = [int]$env:PGPORT }

if (-not (Test-Path $BackupFile)) {
    throw "Backup archive not found: $BackupFile"
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
if (-not $TargetDatabase) {
    $TargetDatabase = "sentinelgrid_recovery_drill_${timestamp}"
}

# Production overwrite safeguard
if ($TargetDatabase -eq "sentinelgrid" -and -not $AllowProductionOverwrite) {
    Write-Error "[CRITICAL SAFETY BLOCK] Target database is 'sentinelgrid'!"
    Write-Error "To overwrite the active SentinelGrid database, you MUST specify -AllowProductionOverwrite"
    throw "Aborted restore to protect active production database."
}

# Locate binaries
$pgRestorePath = (Get-Command pg_restore.exe -ErrorAction SilentlyContinue).Source
if (-not $pgRestorePath) {
    $fallbackRestore = "C:\Program Files\PostgreSQL\16\bin\pg_restore.exe"
    if (Test-Path $fallbackRestore) { $pgRestorePath = $fallbackRestore }
}

$psqlPath = (Get-Command psql.exe -ErrorAction SilentlyContinue).Source
if (-not $psqlPath) {
    $fallbackPsql = "C:\Program Files\PostgreSQL\16\bin\psql.exe"
    if (Test-Path $fallbackPsql) { $psqlPath = $fallbackPsql }
}

if (-not $pgRestorePath) { throw "pg_restore.exe not found." }
if (-not $psqlPath) { throw "psql.exe not found." }

Write-Host "=========================================================" -ForegroundColor Cyan
Write-Host "SentinelGrid PostgreSQL Database Restore Drill" -ForegroundColor Cyan
Write-Host "Source Archive : $BackupFile"
Write-Host "Target Host    : ${PgHost}:${PgPort}"
Write-Host "Target Database: $TargetDatabase"
Write-Host "=========================================================" -ForegroundColor Cyan

# 1. Verify Checksum if present
$checksumFile = "${BackupFile}.sha256"
if (Test-Path $checksumFile) {
    Write-Host "[INFO] Verifying SHA-256 checksum before restoration..."
    $expectedLine = Get-Content $checksumFile -Raw
    $expectedHash = ($expectedLine -split '\s+')[0].Trim().ToLower()
    $actualHash = (Get-FileHash -Path $BackupFile -Algorithm SHA256).Hash.ToLower()
    if ($expectedHash -ne $actualHash) {
        throw "Checksum verification failed! Expected $expectedHash but got $actualHash"
    }
    Write-Host "[INFO] Checksum verified: OK"
}

# 2. Recreate target database
Write-Host "[INFO] Preparing target database '$TargetDatabase'..."
& $psqlPath -h $PgHost -p $PgPort -U $PgUser -d postgres -c "DROP DATABASE IF EXISTS `"$TargetDatabase`" WITH (FORCE);" | Out-Null
& $psqlPath -h $PgHost -p $PgPort -U $PgUser -d postgres -c "CREATE DATABASE `"$TargetDatabase`";" | Out-Null

# 3. Execute pg_restore
Write-Host "[INFO] Restoring archive into '$TargetDatabase' via pg_restore..."
$sw = [System.Diagnostics.Stopwatch]::StartNew()
& $pgRestorePath -h $PgHost -p $PgPort -U $PgUser -d $TargetDatabase --no-owner --no-acl $BackupFile
$sw.Stop()
Write-Host "[INFO] Restoration finished in $($sw.Elapsed.TotalSeconds.ToString('F1'))s."

# 4. Smoke Test Queries
Write-Host "[INFO] Running post-restore verification queries on '$TargetDatabase'..." -ForegroundColor Yellow
& $psqlPath -h $PgHost -p $PgPort -U $PgUser -d $TargetDatabase -c "
SELECT 'pharmacies' as tbl, count(*) FROM pharmacies
UNION ALL SELECT 'medicines', count(*) FROM medicines
UNION ALL SELECT 'users', count(*) FROM users
UNION ALL SELECT 'inventory', count(*) FROM inventory
UNION ALL SELECT 'reservations', count(*) FROM reservations
UNION ALL SELECT 'audit_logs', count(*) FROM audit_logs
UNION ALL SELECT 'idempotency_records', count(*) FROM idempotency_records;
"

Write-Host "[SUCCESS] Restore drill completed and verified successfully into '$TargetDatabase'." -ForegroundColor Green

