#!/usr/bin/env bash
# ==============================================================================
# SentinelGrid — Backup Verification Tool (Read-Only)
# Inspects: SHA-256 checksum match and pg_restore -l TOC integrity
# Safety: Purely read-only; connects to no database and executes no DDL.
# ==============================================================================
set -euo pipefail

BACKUP_FILE="${1:-}"

if [ -z "${BACKUP_FILE}" ]; then
    echo "Usage: $0 <path_to_backup.dump>" >&2
    exit 1
fi

if [ ! -f "${BACKUP_FILE}" ]; then
    echo "[ERROR] Backup file not found: ${BACKUP_FILE}" >&2
    exit 1
fi

echo "[INFO] Verifying SentinelGrid backup archive: ${BACKUP_FILE}"

# 1. Verify Checksum if present
CHECKSUM_FILE="${BACKUP_FILE}.sha256"
if [ -f "${CHECKSUM_FILE}" ]; then
    echo "[INFO] Verifying SHA-256 cryptographic checksum against ${CHECKSUM_FILE}..."
    (cd "$(dirname "${BACKUP_FILE}")" && sha256sum -c "$(basename "${CHECKSUM_FILE}")") || {
        echo "[ERROR] Checksum verification FAILED! Backup file may be corrupted or tampered." >&2
        exit 1
    }
    echo "[INFO] Checksum verified: OK"
else
    echo "[WARN] No companion .sha256 file found. Computing standalone SHA-256 hash..."
    sha256sum "${BACKUP_FILE}"
fi

# 2. Verify Table of Contents via pg_restore -l
echo "[INFO] Inspecting Table of Contents via pg_restore -l..."
TOC_OUTPUT=$(pg_restore -l "${BACKUP_FILE}")
TOC_ENTRIES=$(echo "${TOC_OUTPUT}" | grep -v '^;' | wc -l || true)

echo "[INFO] Archive metadata:"
echo "${TOC_OUTPUT}" | head -n 12

echo "[INFO] Archive valid. Total schema and data entries: ${TOC_ENTRIES}"
if [ "${TOC_ENTRIES}" -lt 5 ]; then
    echo "[ERROR] Suspiciously small archive: less than 5 TOC entries found." >&2
    exit 1
fi

echo "[SUCCESS] Backup verification PASSED."
exit 0

