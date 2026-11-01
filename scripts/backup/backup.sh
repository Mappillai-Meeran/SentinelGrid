#!/usr/bin/env bash
# ==============================================================================
# SentinelGrid — PostgreSQL Production Backup Script
# Format: Custom compressed archive (pg_dump -Fc)
# Security: SHA-256 checksum generation, optional AES-256-CBC encryption
# Retention: Automatic pruning of backups older than RETENTION_DAYS
# ==============================================================================
set -euo pipefail

# ------------------------------------------------------------------------------
# Configuration (Overridable via environment variables)
# ------------------------------------------------------------------------------
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-sentinelgrid}"
PGUSER="${PGUSER:-postgres}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/sentinelgrid}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
BACKUP_ENCRYPTION_KEY="${BACKUP_ENCRYPTION_KEY:-}"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILENAME="${PGDATABASE}_backup_${TIMESTAMP}.dump"
BACKUP_FILEPATH="${BACKUP_DIR}/${BACKUP_FILENAME}"
CHECKSUM_FILEPATH="${BACKUP_FILEPATH}.sha256"

# ------------------------------------------------------------------------------
# Functions
# ------------------------------------------------------------------------------
log() {
    echo "[$(date +'%Y-%m-%dT%H:%M:%S%z')] [INFO] $*"
}

warn() {
    echo "[$(date +'%Y-%m-%dT%H:%M:%S%z')] [WARN] $*" >&2
}

error() {
    echo "[$(date +'%Y-%m-%dT%H:%M:%S%z')] [ERROR] $*" >&2
    exit 1
}

# ------------------------------------------------------------------------------
# Preflight Checks
# ------------------------------------------------------------------------------
command -v pg_dump >/dev/null 2>&1 || error "pg_dump binary not found in PATH"
command -v pg_restore >/dev/null 2>&1 || error "pg_restore binary not found in PATH"
command -v sha256sum >/dev/null 2>&1 || error "sha256sum binary not found in PATH"

mkdir -p "${BACKUP_DIR}" || error "Failed to create backup directory: ${BACKUP_DIR}"

log "Starting SentinelGrid backup..."
log "Host: ${PGHOST}:${PGPORT} | Database: ${PGDATABASE} | User: ${PGUSER}"
log "Target destination: ${BACKUP_FILEPATH}"

# ------------------------------------------------------------------------------
# Execution: pg_dump -Fc (Custom Format with compression)
# ------------------------------------------------------------------------------
START_TIME=$(date +%s)

pg_dump \
    -h "${PGHOST}" \
    -p "${PGPORT}" \
    -U "${PGUSER}" \
    -d "${PGDATABASE}" \
    -Fc \
    --no-owner \
    --no-acl \
    -f "${BACKUP_FILEPATH}" || error "pg_dump execution failed!"

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))
BACKUP_SIZE=$(wc -c < "${BACKUP_FILEPATH}")
log "pg_dump completed in ${DURATION}s. Raw archive size: ${BACKUP_SIZE} bytes."

# ------------------------------------------------------------------------------
# Table-of-Contents (TOC) Integrity Verification
# ------------------------------------------------------------------------------
log "Verifying archive header and Table-of-Contents via pg_restore -l..."
TOC_ENTRIES=$(pg_restore -l "${BACKUP_FILEPATH}" | grep -v '^;' | wc -l || true)
if [ "${TOC_ENTRIES}" -lt 5 ]; then
    error "Backup TOC verification failed: only ${TOC_ENTRIES} entries found in archive."
fi
log "Archive TOC verified successfully (${TOC_ENTRIES} entries found)."

# ------------------------------------------------------------------------------
# Checksum Generation (SHA-256)
# ------------------------------------------------------------------------------
log "Computing SHA-256 cryptographic checksum..."
(cd "${BACKUP_DIR}" && sha256sum "${BACKUP_FILENAME}" > "${CHECKSUM_FILEPATH}")
log "Checksum saved to: ${CHECKSUM_FILEPATH}"

# ------------------------------------------------------------------------------
# Optional Encryption at Rest (OpenSSL AES-256-CBC)
# ------------------------------------------------------------------------------
if [ -n "${BACKUP_ENCRYPTION_KEY}" ]; then
    log "Encrypting backup archive with OpenSSL AES-256-CBC..."
    ENCRYPTED_FILEPATH="${BACKUP_FILEPATH}.enc"
    openssl enc -aes-256-cbc -pbkdf2 -salt \
        -in "${BACKUP_FILEPATH}" \
        -out "${ENCRYPTED_FILEPATH}" \
        -pass "pass:${BACKUP_ENCRYPTION_KEY}" || error "Encryption failed!"
    
    # Compute checksum of encrypted file
    (cd "${BACKUP_DIR}" && sha256sum "$(basename "${ENCRYPTED_FILEPATH}")" > "${ENCRYPTED_FILEPATH}.sha256")
    
    # Securely remove plaintext dump
    rm -f "${BACKUP_FILEPATH}" "${CHECKSUM_FILEPATH}"
    log "Backup encrypted successfully: ${ENCRYPTED_FILEPATH}"
fi

# ------------------------------------------------------------------------------
# Retention Pruning
# ------------------------------------------------------------------------------
log "Pruning backups older than ${RETENTION_DAYS} days in ${BACKUP_DIR}..."
find "${BACKUP_DIR}" -name "${PGDATABASE}_backup_*.dump*" -type f -mtime +"${RETENTION_DAYS}" -exec rm -vf {} \; || warn "Retention pruning encountered non-fatal issues."

log "Backup completed successfully."
exit 0

