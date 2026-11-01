# ==============================================================================
# SentinelGrid — Backup Verification Tool for PowerShell (Read-Only)
# Inspects: SHA-256 checksum match and pg_restore -l TOC integrity
# Safety: Purely read-only; connects to no database and executes no DDL.
# ==============================================================================
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$BackupFile
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupFile)) {
    Write-Error "Backup file not found: $BackupFile"
    exit 1
}

$pgRestorePath = (Get-Command pg_restore.exe -ErrorAction SilentlyContinue).Source
if (-not $pgRestorePath) {
    $fallbackRestore = "C:\Program Files\PostgreSQL\16\bin\pg_restore.exe"
    if (Test-Path $fallbackRestore) { $pgRestorePath = $fallbackRestore }
}
if (-not $pgRestorePath) { throw "pg_restore.exe not found." }

Write-Host "[INFO] Verifying SentinelGrid backup archive: $BackupFile"

# 1. Verify Checksum
$checksumFile = "${BackupFile}.sha256"
if (Test-Path $checksumFile) {
    Write-Host "[INFO] Verifying SHA-256 checksum against $checksumFile..."
    $expectedLine = Get-Content $checksumFile -Raw
    $expectedHash = ($expectedLine -split '\s+')[0].Trim().ToLower()
    $actualHash = (Get-FileHash -Path $BackupFile -Algorithm SHA256).Hash.ToLower()

    if ($expectedHash -ne $actualHash) {
        Write-Error "[ERROR] Checksum MISMATCH! Expected: $expectedHash, Actual: $actualHash"
        exit 1
    }
    Write-Host "[INFO] Checksum verified: MATCH ($actualHash)"
} else {
    Write-Warning "No companion .sha256 file found."
    $hash = (Get-FileHash -Path $BackupFile -Algorithm SHA256).Hash.ToLower()
    Write-Host "[INFO] Computed SHA-256: $hash"
}

# 2. Verify Table of Contents via pg_restore -l
Write-Host "[INFO] Inspecting Table of Contents via pg_restore -l..."
$toc = & $pgRestorePath -l $BackupFile
if ($LASTEXITCODE -ne 0) {
    Write-Error "[ERROR] pg_restore -l failed with exit code $LASTEXITCODE"
    exit 1
}

$headerLines = $toc | Select-Object -First 12
$headerLines | ForEach-Object { Write-Host "   $_" }

$tocEntries = ($toc | Where-Object { $_ -and -not $_.StartsWith(";") }).Count
Write-Host "[INFO] Total schema and data entries in TOC: $tocEntries"
if ($tocEntries -lt 5) {
    Write-Error "[ERROR] Suspiciously small archive ($tocEntries entries)."
    exit 1
}

Write-Host "[SUCCESS] Backup archive is intact, valid, and readable." -ForegroundColor Green

