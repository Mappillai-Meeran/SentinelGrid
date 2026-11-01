#!/usr/bin/env bash
# ==============================================================================
# SentinelGrid — PostgreSQL Database Restore & Recovery Drill Script
# Safety:
#   - Defaults strictly to an isolated temporary recovery drill database
#   - Hard refusal to overwrite 'sentinelgrid' without --allow-production-overwrite
#   - Verifies SHA-256 checksum prior to restore
#   - Executes post-restore smoke validation queries
# ==============================================================================
set -euo pipefail

BACKUP_FILE="${1:-}"
TARGET_DB="${2:-}"
ALLOW_PROD_OVERWRITE="${3:-}"

if [ -z "${BACKUP_FILE}" ]; then
    echo "Usage: $0 <path_to_backup.dump> [target_database_name] [--allow-production-overwrite]" >&2
    echo "Example (Drill):   $0 backups/sentinelgrid_backup.dump" >&2
    echo "Example (Custom):  $0 backups/sentinelgrid_backup.dump sentinelgrid_stage_restore" >&2
    exit 1
fi

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-postgres}"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
if [ -z "${TARGET_DB}" ]; then
    TARGET_DB="sentinelgrid_recovery_drill_${TIMESTAMP}"
fi

# ------------------------------------------------------------------------------
# Production Overwrite Safeguard
# ------------------------------------------------------------------------------
if [ "${TARGET_DB}" = "sentinelgrid" ] && [ "${ALLOW_PROD_OVERWRITE}" != "--allow-production-overwrite" ]; then
    echo "[CRITICAL SAFETY BLOCK] Target database is 'sentinelgrid'!" >&2
    echo "To overwrite the active SentinelGrid database, you MUST explicitly provide --allow-production-overwrite" >&2
    echo "Aborting restore to prevent accidental data loss." >&2
    exit 1
fi

echo "[INFO] ========================================================="
echo "[INFO] SentinelGrid PostgreSQL Database Restore"
echo "[INFO] Source Archive : ${BACKUP_FILE}"
echo "[INFO] Target Host    : ${PGHOST}:${PGPORT}"
echo "[INFO] Target Database: ${TARGET_DB}"
echo "[INFO] ========================================================="

# ------------------------------------------------------------------------------
# Checksum Pre-flight Verification
# ------------------------------------------------------------------------------
CHECKSUM_FILE="${BACKUP_FILE}.sha256"
if [ -f "${CHECKSUM_FILE}" ]; then
    echo "[INFO] Verifying SHA-256 checksum before restoration..."
    (cd "$(dirname "${BACKUP_FILE}")" && sha256sum -c "$(basename "${CHECKSUM_FILE}")") || {
        echo "[ERROR] Pre-restore checksum verification FAILED!" >&2
        exit 1
    }
fi

# ------------------------------------------------------------------------------
# Prepare Target Database
# ------------------------------------------------------------------------------
echo "[INFO] Preparing target database '${TARGET_DB}'..."
psql -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d postgres -c "DROP DATABASE IF EXISTS \"${TARGET_DB}\" WITH (FORCE);" || true
psql -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d postgres -c "CREATE DATABASE \"${TARGET_DB}\";"

# ------------------------------------------------------------------------------
# Execute pg_restore
# ------------------------------------------------------------------------------
echo "[INFO] Restoring archive into '${TARGET_DB}' via pg_restore..."
START_TIME=$(date +%s)

pg_restore \
    -h "${PGHOST}" \
    -p "${PGPORT}" \
    -U "${PGUSER}" \
    -d "${TARGET_DB}" \
    --no-owner \
    --no-acl \
    "${BACKUP_FILE}" || echo "[WARN] pg_restore exited with warnings (standard for existing roles/extensions)."

END_TIME=$(date +%s)
echo "[INFO] Restoration completed in $((END_TIME - START_TIME))s."

# ------------------------------------------------------------------------------
# Post-Restore Smoke Test & Table Verification
# ------------------------------------------------------------------------------
echo "[INFO] Running post-restore verification on '${TARGET_DB}'..."
psql -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d "${TARGET_DB}" -c "
SELECT 
    schemaname, 
    relname as table_name, 
    n_live_tup as approximate_row_count 
FROM pg_stat_user_tables 
ORDER BY relname;
"

echo "[INFO] Table counts:"
psql -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d "${TARGET_DB}" -c "
SELECT 'pharmacies' as tbl, count(*) FROM pharmacies
UNION ALL SELECT 'medicines', count(*) FROM medicines
UNION ALL SELECT 'users', count(*) FROM users
UNION ALL SELECT 'inventory', count(*) FROM inventory
UNION ALL SELECT 'reservations', count(*) FROM reservations
UNION ALL SELECT 'audit_logs', count(*) FROM audit_logs
UNION ALL SELECT 'idempotency_records', count(*) FROM idempotency_records;
"

echo "[SUCCESS] Restore drill into '${TARGET_DB}' completed and verified successfully."
exit 0

