# ==============================================================================
# SentinelGrid — Provision Least-Privilege Database Roles (PowerShell)
# Configures:
#   - sentinel_migrator (DDL)
#   - sentinel_app (DML)
#   - sentinel_backup (Read-only)
# ==============================================================================
[CmdletBinding()]
param(
    [string]$PgHost = $env:PGHOST,
    [int]$PgPort = 5432,
    [string]$PgDatabase = "sentinelgrid",
    [string]$AdminUser = "postgres",
    [string]$AppPassword = $env:SENTINEL_APP_PASSWORD,
    [string]$MigratorPassword = $env:SENTINEL_MIGRATOR_PASSWORD,
    [string]$BackupPassword = $env:SENTINEL_BACKUP_PASSWORD
)

$ErrorActionPreference = "Stop"

if (-not $PgHost) { $PgHost = "localhost" }
if ($env:PGPORT) { $PgPort = [int]$env:PGPORT }

foreach ($secret in @(
    @{ Name = "SENTINEL_APP_PASSWORD"; Value = $AppPassword },
    @{ Name = "SENTINEL_MIGRATOR_PASSWORD"; Value = $MigratorPassword },
    @{ Name = "SENTINEL_BACKUP_PASSWORD"; Value = $BackupPassword }
)) {
    if ([string]::IsNullOrWhiteSpace($secret.Value) -or $secret.Value.Length -lt 24) {
        throw "$($secret.Name) must be supplied securely and contain at least 24 characters. No default password is provided."
    }
}

$psqlPath = (Get-Command psql.exe -ErrorAction SilentlyContinue).Source
if (-not $psqlPath) {
    $fallbackPsql = "C:\Program Files\PostgreSQL\16\bin\psql.exe"
    if (Test-Path $fallbackPsql) { $psqlPath = $fallbackPsql }
}
if (-not $psqlPath) { throw "psql.exe not found." }

Write-Host "[INFO] Configuring least-privilege roles on database '$PgDatabase' ($PgHost:$PgPort)..."

$sql = @"
DO `$do`$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'sentinel_migrator') THEN
        CREATE ROLE sentinel_migrator WITH LOGIN PASSWORD '$MigratorPassword';
    ELSE
        ALTER ROLE sentinel_migrator WITH PASSWORD '$MigratorPassword';
    END IF;

    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'sentinel_app') THEN
        CREATE ROLE sentinel_app WITH LOGIN PASSWORD '$AppPassword';
    ELSE
        ALTER ROLE sentinel_app WITH PASSWORD '$AppPassword';
    END IF;

    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'sentinel_backup') THEN
        CREATE ROLE sentinel_backup WITH LOGIN PASSWORD '$BackupPassword';
    ELSE
        ALTER ROLE sentinel_backup WITH PASSWORD '$BackupPassword';
    END IF;
END `$do`$;

GRANT CONNECT ON DATABASE $PgDatabase TO sentinel_migrator, sentinel_app, sentinel_backup;
GRANT ALL ON SCHEMA public TO sentinel_migrator;

GRANT USAGE ON SCHEMA public TO sentinel_app, sentinel_backup;
REVOKE CREATE ON SCHEMA public FROM sentinel_app, sentinel_backup;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO sentinel_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO sentinel_app;
REVOKE TRUNCATE ON ALL TABLES IN SCHEMA public FROM sentinel_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sentinel_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO sentinel_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE TRUNCATE ON TABLES FROM sentinel_app;

GRANT SELECT ON ALL TABLES IN SCHEMA public TO sentinel_backup;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO sentinel_backup;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON ALL TABLES IN SCHEMA public FROM sentinel_backup;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO sentinel_backup;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO sentinel_backup;
"@

& $psqlPath -h $PgHost -p $PgPort -U $AdminUser -d $PgDatabase -c "$sql"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to configure least-privilege roles. Exit code: $LASTEXITCODE"
}

Write-Host "[SUCCESS] Least-privilege roles (sentinel_migrator, sentinel_app, sentinel_backup) successfully provisioned." -ForegroundColor Green

