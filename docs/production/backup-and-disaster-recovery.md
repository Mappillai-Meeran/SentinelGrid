# SentinelGrid — Production Backup, PITR & Disaster Recovery Guide

This guide establishes the database backup architecture, disaster recovery drills, Point-In-Time Recovery (PITR) design, and operational runbooks for SentinelGrid on PostgreSQL 16.

---

## 1. Recovery Objectives (RPO & RTO)

| Metric | Target | Mechanism |
|---|---|---|
| **Recovery Point Objective (RPO)** | **< 15 minutes** (Continuous WAL Archiving)<br>**< 24 hours** (Daily Snapshot Dumps) | Continuous Write-Ahead Log (WAL) shipping to secure off-site object storage (S3 / GCS) combined with daily full compressed logical dumps (`pg_dump -Fc`). |
| **Recovery Time Objective (RTO)** | **< 30 minutes** (Snapshot Dump Restore)<br>**< 60 minutes** (Full PITR from Basebackup) | Automated parallel restore (`pg_restore --jobs`) into pre-provisioned or newly spawned PostgreSQL instances. |
| **Data Integrity Invariant** | **Zero Data Corruption / Cryptographically Verified** | Every backup is accompanied by an immutable SHA-256 checksum file and pre-restore Table-of-Contents (`pg_restore -l`) validation. |

---

## 2. Backup Architecture & Standards

### A. Snapshot Dump Format (`pg_dump -Fc`)
All full database backups use PostgreSQL's custom format (`-Fc`):
- **Built-in Compression:** Uses zlib compression natively, reducing storage footprint by ~70–80%.
- **Table of Contents (TOC):** Contains an internal schema and data directory, allowing selective table restoration and pre-restoration structure validation (`pg_restore -l`).
- **Parallel Restore Capability:** Supports multi-core restoration (`pg_restore -j <n>`), drastically minimizing recovery time.
- **Reordering Safety:** Automatically restores constraints and foreign keys after all table rows are populated, avoiding circular reference or constraint violation failures.

### B. Backup Script Tooling
SentinelGrid provides production-grade scripts with built-in checksumming, TOC validation, and retention management:

- **Linux / Containerized Environments:** [`scripts/backup/backup.sh`](file:///E:/Sentinel/scripts/backup/backup.sh)
- **Windows / PowerShell Environments:** [`scripts/backup/backup.ps1`](file:///E:/Sentinel/scripts/backup/backup.ps1)
- **Verification Tool:** [`scripts/backup/verify-backup.sh`](file:///E:/Sentinel/scripts/backup/verify-backup.sh) and [`scripts/backup/verify-backup.ps1`](file:///E:/Sentinel/scripts/backup/verify-backup.ps1)
- **Isolated Restore Tool:** [`scripts/backup/restore.sh`](file:///E:/Sentinel/scripts/backup/restore.sh) and [`scripts/backup/restore.ps1`](file:///E:/Sentinel/scripts/backup/restore.ps1)

### C. Backup Retention Schedule
To balance storage costs with regulatory and operational compliance:

| Backup Frequency | Retention Period | Pruning Policy |
|---|---|---|
| **Daily Snapshots** | 14 days | Automated daily pruning by `backup.sh` / `backup.ps1` (`RETENTION_DAYS=14`). |
| **Weekly Snapshots** | 8 weeks | Archived to secondary storage tier. |
| **Monthly Snapshots** | 12 months | Archived to immutable cold storage (e.g. AWS S3 Glacier / GCS Archive). |

---

## 3. Cryptographic Verification & Encryption at Rest

### A. SHA-256 Integrity Verification
Every backup generates a companion `.sha256` checksum file. Before any restore is attempted, the archive's SHA-256 digest is verified against the recorded hash:

```bash
# Verify checksum
sha256sum -c sentinelgrid_backup_20261031_102158.dump.sha256
```

### B. Optional Encryption at Rest
For off-site transmission to cloud object storage, backups can be encrypted using OpenSSL with AES-256-CBC and PBKDF2 key derivation:

```bash
# Encrypt
openssl enc -aes-256-cbc -pbkdf2 -salt \
    -in sentinelgrid_backup.dump \
    -out sentinelgrid_backup.dump.enc \
    -pass pass:"$BACKUP_ENCRYPTION_KEY"

# Decrypt
openssl enc -d -aes-256-cbc -pbkdf2 \
    -in sentinelgrid_backup.dump.enc \
    -out sentinelgrid_backup.dump \
    -pass pass:"$BACKUP_ENCRYPTION_KEY"
```

---

## 4. Continuous Archiving & Point-In-Time Recovery (PITR) Design

To achieve an RPO of < 15 minutes, PostgreSQL must be configured for continuous WAL archiving.

### A. PostgreSQL Server Configuration (`postgresql.conf`)
```ini
# ==============================================================================
# SentinelGrid PITR Archive Configuration
# ==============================================================================
wal_level = replica
archive_mode = on
archive_command = 'test ! -f /var/lib/postgresql/wal_archive/%f && cp %p /var/lib/postgresql/wal_archive/%f'
# For cloud deployments:
# archive_command = 'aws s3 cp %p s3://sentinelgrid-wal-archive-prod/%f'
archive_timeout = 900  # Force WAL switch every 15 minutes to guarantee RPO
```

### B. PITR Recovery Configuration
To restore the database to an exact point in time (e.g., right before an accidental operator error at `2026-10-31 09:15:00 UTC`):

1. **Restore Base Backup:** Restore the latest physical basebackup (or initialize target cluster).
2. **Configure Recovery Target:** Create a `postgresql.auto.conf` or signal file with:
   ```ini
   restore_command = 'cp /var/lib/postgresql/wal_archive/%f %p'
   # For cloud deployments:
   # restore_command = 'aws s3 cp s3://sentinelgrid-wal-archive-prod/%f %p'
   recovery_target_time = '2026-10-31 09:15:00 UTC'
   recovery_target_action = 'promote'
   ```
3. **Start PostgreSQL:** PostgreSQL plays WAL logs up to the exact timestamp and promotes itself to read-write serving.

---

## 5. Isolated Restore Drill Runbook

> [!CAUTION]
> **Data Loss Prevention Invariant:**
> Never test recovery drills by dropping or overwriting the active `sentinelgrid` database. Always restore into an isolated drill database (e.g., `sentinelgrid_recovery_drill_<timestamp>`).

### Step 1: Run On-Demand Backup
```bash
# Linux
./scripts/backup/backup.sh

# Windows / PowerShell
.\scripts\backup\backup.ps1
```

### Step 2: Verify Archive Table of Contents (Read-Only)
```bash
# Linux
./scripts/backup/verify-backup.sh /var/backups/sentinelgrid/sentinelgrid_backup_*.dump

# Windows / PowerShell
.\scripts\backup\verify-backup.ps1 .\backups\sentinelgrid_backup_*.dump
```

Expected Output:
```
[INFO] Archive TOC verified successfully (86 schema/data entries found).
[SUCCESS] Backup archive is intact, valid, and readable.
```

### Step 3: Execute Isolated Restore Drill
```bash
# Linux
./scripts/backup/restore.sh /var/backups/sentinelgrid/sentinelgrid_backup_*.dump sentinelgrid_recovery_drill_test

# Windows / PowerShell
.\scripts\backup\restore.ps1 -BackupFile .\backups\sentinelgrid_backup_*.dump -TargetDatabase sentinelgrid_recovery_drill_test
```

### Step 4: Smoke Test & Verify Data Fidelity
Query the drill database to verify that record counts and integrity match the source:
```sql
SELECT 'pharmacies' as tbl, count(*) FROM pharmacies
UNION ALL SELECT 'medicines', count(*) FROM medicines
UNION ALL SELECT 'users', count(*) FROM users
UNION ALL SELECT 'inventory', count(*) FROM inventory
UNION ALL SELECT 'reservations', count(*) FROM reservations
UNION ALL SELECT 'audit_logs', count(*) FROM audit_logs
UNION ALL SELECT 'idempotency_records', count(*) FROM idempotency_records;
```

### Step 5: Clean Up Drill Database
```sql
DROP DATABASE sentinelgrid_recovery_drill_test WITH (FORCE);
```

---

## 6. Automated Recovery Integration Test

SentinelGrid includes an end-to-end automated disaster recovery test suite:
[`com.sentragrid.recovery.PostgresBackupAndRecoveryIntegrationTest`](file:///E:/Sentinel/src/test/java/com/sentragrid/recovery/PostgresBackupAndRecoveryIntegrationTest.java)

The suite programmatically verifies:
1. `testAutomatedBackupGenerationAndChecksumIntegrity`: Generates a custom-format dump (`pg_dump -Fc`), asserts size, and computes SHA-256 hash.
2. `testBackupTableOfContentsVerification`: Parses TOC via `pg_restore -l` and asserts presence of all 10 application tables.
3. `testIsolatedRestoreDrillAndDataIntegrity`: Creates `sentinelgrid_recovery_drill_test`, restores archive, asserts 100% table row count match, asserts representative records (users, medicines, inventory), and checks constraints and indexes.
4. `testRestoreProductionOverwriteSafeguard`: Enforces safety blocks against accidental production database overwrites.

To execute the recovery integration test:
```bash
mvn test -Dtest=PostgresBackupAndRecoveryIntegrationTest
```

