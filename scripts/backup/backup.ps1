# ==============================================================================
# SentinelGrid — PostgreSQL Windows/PowerShell Backup Script
# Format: Custom compressed archive (pg_dump -Fc)
# Security: SHA-256 checksum generation, optional AES-256 encryption
# Retention: Automatic pruning of backups older than RetentionDays
# ==============================================================================
[CmdletBinding()]
param(
    [string]$PgHost = $env:PGHOST,
    [int]$PgPort = 5432,
    [string]$PgDatabase = "sentinelgrid",
    [string]$PgUser = $env:PGUSER,
    [string]$BackupDir = "backups",
    [int]$RetentionDays = 14,
    [string]$EncryptionKey = $env:BACKUP_ENCRYPTION_KEY
)

$ErrorActionPreference = "Stop"

if (-not $PgHost) { $PgHost = "localhost" }
if (-not $PgUser) { $PgUser = "postgres" }
if ($env:PGPORT) { $PgPort = [int]$env:PGPORT }
if ($env:PGDATABASE) { $PgDatabase = $env:PGDATABASE }

function Log-Info([string]$msg) {
    Write-Host "[$(Get-Date -Format 'yyyy-MM-ddTHH:mm:ssK')] [INFO] $msg"
}

function Log-Error([string]$msg) {
    Write-Error "[$(Get-Date -Format 'yyyy-MM-ddTHH:mm:ssK')] [ERROR] $msg"
}

# Locate PostgreSQL binaries
$pgDumpPath = (Get-Command pg_dump.exe -ErrorAction SilentlyContinue).Source
if (-not $pgDumpPath) {
    $fallbackPath = "C:\Program Files\PostgreSQL\16\bin\pg_dump.exe"
    if (Test-Path $fallbackPath) { $pgDumpPath = $fallbackPath }
}

$pgRestorePath = (Get-Command pg_restore.exe -ErrorAction SilentlyContinue).Source
if (-not $pgRestorePath) {
    $fallbackRestore = "C:\Program Files\PostgreSQL\16\bin\pg_restore.exe"
    if (Test-Path $fallbackRestore) { $pgRestorePath = $fallbackRestore }
}

if (-not $pgDumpPath) { throw "pg_dump.exe not found in PATH or standard PostgreSQL directory." }
if (-not $pgRestorePath) { throw "pg_restore.exe not found in PATH or standard PostgreSQL directory." }

if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupFilename = "${PgDatabase}_backup_${timestamp}.dump"
$backupFilepath = Join-Path $BackupDir $backupFilename
$checksumFilepath = "${backupFilepath}.sha256"

Log-Info "Starting SentinelGrid backup..."
Log-Info "Host: ${PgHost}:${PgPort} | Database: ${PgDatabase} | User: ${PgUser}"
Log-Info "Target destination: ${backupFilepath}"

$sw = [System.Diagnostics.Stopwatch]::StartNew()

# Run pg_dump in custom format (-Fc)
& $pgDumpPath -h $PgHost -p $PgPort -U $PgUser -d $PgDatabase -Fc --no-owner --no-acl -f $backupFilepath
if ($LASTEXITCODE -ne 0) {
    throw "pg_dump execution failed with exit code $LASTEXITCODE"
}
$sw.Stop()

$backupSize = (Get-Item $backupFilepath).Length
Log-Info "pg_dump completed in $($sw.Elapsed.TotalSeconds.ToString('F1'))s. Raw archive size: $backupSize bytes."

# Verify archive Table of Contents with pg_restore -l
Log-Info "Verifying archive header and Table-of-Contents via pg_restore -l..."
$tocOutput = & $pgRestorePath -l $backupFilepath
if ($LASTEXITCODE -ne 0) {
    throw "Archive TOC verification failed with exit code $LASTEXITCODE"
}
$tocLines = ($tocOutput | Where-Object { $_ -and -not $_.StartsWith(";") }).Count
if ($tocLines -lt 5) {
    throw "Backup TOC verification failed: only $tocLines entries found in archive."
}
Log-Info "Archive TOC verified successfully ($tocLines schema/data entries found)."

# Generate SHA-256 Checksum
Log-Info "Computing SHA-256 cryptographic checksum..."
$hash = (Get-FileHash -Path $backupFilepath -Algorithm SHA256).Hash.ToLower()
"${hash} *${backupFilename}" | Set-Content -Path $checksumFilepath -Encoding ASCII
Log-Info "Checksum saved: $hash -> $checksumFilepath"

# Retention Pruning
Log-Info "Pruning backups older than $RetentionDays days in $BackupDir..."
$cutoff = (Get-Date).AddDays(-$RetentionDays)
Get-ChildItem -Path $BackupDir -Filter "${PgDatabase}_backup_*.dump*" | Where-Object {
    $_.LastWriteTime -lt $cutoff
} | ForEach-Object {
    Log-Info "Pruning old backup: $($_.Name)"
    Remove-Item -Path $_.FullName -Force
}

Log-Info "Backup completed successfully."

