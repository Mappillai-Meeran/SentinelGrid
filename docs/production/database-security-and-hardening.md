# SentinelGrid — Production Database Security & Network Hardening Guide

This document defines the database security architecture, least-privilege role matrix, network isolation, and TLS encryption standards for PostgreSQL across SentinelGrid environments.

---

## 1. Least-Privilege Role Separation Architecture

SentinelGrid separates database operations into distinct, non-overlapping roles to minimize the blast radius of credential compromise or application vulnerabilities:

```
+-----------------------------------------------------------------------------------------+
|                                 PostgreSQL Database Cluster                             |
+-----------------------------------------------------------------------------------------+
       |                                      |                                  |
       v                                      v                                  v
+-----------------------+          +-----------------------+          +-----------------------+
|   sentinel_migrator   |          |     sentinel_app      |          |    sentinel_backup    |
+-----------------------+          +-----------------------+          +-----------------------+
| - Full DDL Authority  |          | - DML Only:           |          | - Read-Only:          |
| - Flyway Migrations   |          |   SELECT, INSERT,     |          |   SELECT on all tables|
| - CREATE/ALTER/DROP   |          |   UPDATE, DELETE      |          | - USAGE on sequences  |
| - Table/Index Owner   |          | - Sequence USAGE      |          | - pg_dump archives    |
|                       |          | - CANNOT DROP/TRUNCATE|          | - CANNOT INSERT/UPDATE|
|                       |          | - CANNOT ALTER SCHEMA |          | - CANNOT ALTER/DROP   |
+-----------------------+          +-----------------------+          +-----------------------+
```

### Role Privilege Matrix

| Operation | `sentinel_migrator` | `sentinel_app` (Runtime) | `sentinel_backup` | Description |
|---|:---:|:---:|:---:|---|
| **CONNECT** | YES | YES | YES | Connect to database `sentinelgrid` |
| **SELECT** | YES | YES | YES | Read query execution |
| **INSERT / UPDATE / DELETE** | YES | YES | **NO** | Core application business transactions |
| **SEQUENCE (nextval / currval)** | YES | YES | Read-Only | Auto-increment identifier generation |
| **TRUNCATE** | YES | **NO (DENIED)** | **NO (DENIED)** | Destructive mass data purging |
| **DROP TABLE / VIEW** | YES | **NO (DENIED)** | **NO (DENIED)** | Object destruction |
| **ALTER TABLE / DDL** | YES | **NO (DENIED)** | **NO (DENIED)** | Schema modifications |
| **CREATE TABLE / TYPE** | YES | **NO (DENIED)** | **NO (DENIED)** | Unmanaged schema pollution |

---

## 2. Role Provisioning & SQL Scripts

The least-privilege roles are managed through automated scripts:

- **SQL Definition:** [`scripts/security/init-least-privilege-roles.sql`](file:///E:/Sentinel/scripts/security/init-least-privilege-roles.sql)
- **PowerShell Admin Tool:** [`scripts/security/configure-roles.ps1`](file:///E:/Sentinel/scripts/security/configure-roles.ps1)
- **Docker Container Initializer:** [`scripts/security/init-roles.sh`](file:///E:/Sentinel/scripts/security/init-roles.sh)

### SQL Grants Enforced
```sql
-- Application Role: Grants DML and sequence usage only
GRANT CONNECT ON DATABASE sentinelgrid TO sentinel_app;
GRANT USAGE ON SCHEMA public TO sentinel_app;
REVOKE CREATE ON SCHEMA public FROM sentinel_app;

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO sentinel_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO sentinel_app;
REVOKE TRUNCATE ON ALL TABLES IN SCHEMA public FROM sentinel_app;

-- Default privileges for tables created during future Flyway migrations
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sentinel_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO sentinel_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE TRUNCATE ON TABLES FROM sentinel_app;

-- Backup Role: Grants read-only access for pg_dump
GRANT SELECT ON ALL TABLES IN SCHEMA public TO sentinel_backup;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO sentinel_backup;
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON ALL TABLES IN SCHEMA public FROM sentinel_backup;
```

---

## 3. Network Hardening & Port Isolation

### A. Zero Host Port Exposure in Production
In production container environments ([`docker-compose.yml`](file:///E:/Sentinel/docker-compose.yml)):
- PostgreSQL port `5432` is **never exposed** to the host interface (`0.0.0.0:5432`).
- Communication between the application (`sentinelgrid-app`) and database (`sentinelgrid-postgres`) occurs exclusively across the internal Docker bridge network (`postgres:5432`).
- Direct external TCP attacks against database ports are structurally blocked at the network topology layer.

### B. Local Development Override
For local developer debugging where external database GUI tools (e.g. DBeaver, pgAdmin) connect to the container:
- Use [`docker-compose.local.yml`](file:///E:/Sentinel/docker-compose.local.yml):
  ```yaml
  services:
    postgres:
      ports:
        - "127.0.0.1:5432:5432" # Bound strictly to loopback interface
  ```
- Command:
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.local.yml up
  ```

---

## 4. Encryption in Transit (TLS / SSL)

All remote PostgreSQL network traffic must be encrypted to protect PHI (Protected Health Information) and credentials in transit.

- **Configuration:** [`application-prod.yml`](file:///E:/Sentinel/src/main/resources/application-prod.yml)
  ```yaml
  spring:
    datasource:
      url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://postgres:5432/sentinelgrid?sslmode=require}
      username: ${SPRING_DATASOURCE_USERNAME:sentinel_app}
      password: ${SPRING_DATASOURCE_PASSWORD}
  ```
- `sslmode=require`: Prevents plaintext transmission and guards against downgrade attacks.
- High-security production environments should upgrade to `sslmode=verify-full` with trusted CA certificates (`sslrootcert`).

---

## 5. Secret Management & Environment Injection

SentinelGrid enforces fail-safe secret injection. Hardcoded database passwords and secrets have been removed:

1. **Production Compose Fail-Fast Safeguard:**
   ```yaml
   POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?CRITICAL: POSTGRES_PASSWORD must be explicitly set for Docker deployment.}
   SPRING_DATASOURCE_PASSWORD: ${SPRING_DATASOURCE_PASSWORD:?CRITICAL: SPRING_DATASOURCE_PASSWORD must be explicitly set for Docker deployment.}
   ```
2. **Template Reference:**
   [`E:\Sentinel\.env.example`](file:///E:/Sentinel/.env.example) provides an exhaustive configuration template for infrastructure teams.

---

## 6. Automated Verification

SentinelGrid includes an end-to-end integration test verifying database security and role boundaries:
[`com.sentragrid.security.DatabaseSecurityAndHardeningIntegrationTest`](file:///E:/Sentinel/src/test/java/com/sentragrid/security/DatabaseSecurityAndHardeningIntegrationTest.java)

- `testLeastPrivilegeAppRoleCanPerformDml`: Verifies `sentinel_app` can perform required SELECT, INSERT, UPDATE, DELETE, and sequence nextval operations.
- `testLeastPrivilegeAppRoleCannotPerformDestructiveDdl`: Verifies `sentinel_app` is blocked with `PSQLException` (permission denied / must be owner) on `DROP TABLE`, `TRUNCATE TABLE`, and `CREATE TABLE`.
- `testLeastPrivilegeBackupRoleIsReadOnly`: Verifies `sentinel_backup` can read tables for backup, but is denied on INSERT, UPDATE, DELETE, and DDL.
- `testConfigurationHardeningEnforcesTlsAndRequiredSecrets`: Asserts removal of public 5432 exposure, enforcement of required secret guards, and `sslmode=require` in production profiles.
- `testLocalProfileConnectivityPreserved`: Confirms development environments remain unblocked.

Run verification:
```bash
mvn test -Dtest=DatabaseSecurityAndHardeningIntegrationTest
```

