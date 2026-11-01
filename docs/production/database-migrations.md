# SentinelGrid — Production Database Migration Guide

This document describes the schema migration architecture, operational procedures, and safety guidelines for PostgreSQL databases across development, staging, and production environments in SentinelGrid.

---

## 1. Architecture Overview

SentinelGrid uses **Flyway** (`org.flywaydb:flyway-core`) integrated natively with Spring Boot 3.2.3 and PostgreSQL 16.

```
                      +-----------------------------+
                      | Application Startup Phase   |
                      +-----------------------------+
                                     |
                                     v
                      +-----------------------------+
                      |  Profile Safety Validation  |
                      |  (ProfileSafetyValidator)   |
                      +-----------------------------+
                                     |
                                     v
                      +-----------------------------+
                      |      Flyway Migration       |
                      | (flywayInitializer bean)    |
                      +-----------------------------+
                        /                         \
                       /                           \
         [Fresh Database]                  [Existing Database]
                |                                   |
         Applies V1 DDL                   Applies Baseline V1
         from classpath                   without altering data
                \                                   /
                 \                                 /
                  +---------------+---------------+
                                  |
                                  v
                      +-----------------------------+
                      | Hibernate Schema Validation |
                      |    (ddl-auto: validate)     |
                      +-----------------------------+
                                  |
                                  v
                      +-----------------------------+
                      | Application Ready / Serving |
                      +-----------------------------+
```

### Key Architectural Invariants

1. **Deterministic Execution Order:**
   - Spring Boot auto-configuration automatically establishes a `depends-on` relationship where `entityManagerFactory` depends on `flywayInitializer`.
   - Flyway migrations execute strictly **before** Hibernate performs schema validation (`ddl-auto: validate`).
   - `spring.jpa.defer-datasource-initialization` is set to `false` to avoid circular dependency deadlocks.

2. **Decoupled Data Seeding:**
   - Versioned migration scripts under `src/main/resources/db/migration/` contain **strictly DDL** (table definitions, constraints, indexes, sequences).
   - Demo and test seed data are isolated in `src/main/resources/data.sql` and `DemoDataInitializer`, which only execute when `spring.sql.init.mode: always` and `@Profile("local")` are active. Production (`prod`) runs with `spring.sql.init.mode: never`.

3. **Zero Data Loss Invariant:**
   - Pre-existing databases with live production or staging records are never dropped, truncated, or recreated.
   - Flyway's `baseline-on-migrate: true` and `baseline-version: 1` allow existing schemas to be adopted safely into Flyway management.

---

## 2. Managed Schema Baseline (V1)

The baseline schema migration `V1__initial_schema.sql` manages 10 core tables:

| Table | Description | Key Constraints & Indexes |
|---|---|---|
| `pharmacies` | Pharmacy network locations, coordinates, operating hours | PK `id`, `opening_time`, `closing_time`, `is_24_seven` |
| `medicines` | Medicine catalog and classification | PK `id`, `requires_prescription`, `category` |
| `roles` | System roles | PK `id`, UK `uk_roles_name`, check `name IN ('PATIENT', 'PHARMACIST', 'ADMIN')` |
| `users` | System accounts and role assignments | PK `id`, UK `email`, UK `username`, FK `pharmacy_id`, check `users_role_check` (4 roles) |
| `inventory` | Stock levels and reservations per pharmacy/medicine | PK `id`, UK `(pharmacy_id, medicine_id)`, FKs, check `quantity >= 0`, `reserved_quantity >= 0`, optimistic `version` |
| `reservations` | Medicine hold reservations and prescription verification | PK `id`, FKs, check `quantity >= 1`, status enum check, composite index `idx_reservations_status_expires_at (status, expires_at)` |
| `idempotency_records` | API request deduplication tokens | PK `id`, UK `uk_user_idempotency_key (user_id, idempotency_key)`, FKs |
| `audit_logs` | Immutable audit trail for security and regulatory compliance | PK `id`, `entity_name`, `entity_id`, `action`, `performed_by`, `timestamp` |
| `manager_settings` | Per-pharmacy emergency hold and threshold configuration | PK `id`, assigned pharmacy configuration |
| `manager_staff` | Pharmacy branch staff and roster assignments | PK `id`, UK `username`, FK `pharmacy_id` |

---

## 3. Deployment Paths

### Path A: Fresh Database Provisioning

When deploying to a brand-new, empty PostgreSQL database (e.g., initial production setup, staging provisioning, or automated CI pipelines):

1. **Database Creation:**
   ```sql
   CREATE DATABASE sentinelgrid WITH OWNER postgres ENCODING 'UTF8';
   ```

2. **Application Startup:**
   ```bash
   SPRING_PROFILES_ACTIVE=prod \
   SPRING_DATASOURCE_URL=jdbc:postgresql://postgres.internal:5432/sentinelgrid \
   SPRING_DATASOURCE_USERNAME=sentinel_prod \
   SPRING_DATASOURCE_PASSWORD=*** \
   java -jar sentinelgrid.jar
   ```

3. **Behavior:**
   - Flyway detects an empty database.
   - Flyway creates the `flyway_schema_history` metadata table.
   - Flyway executes `V1__initial_schema.sql` in order.
   - Hibernate executes `ddl-auto: validate` against the freshly migrated tables and succeeds.

---

### Path B: Existing Database Baseline

When upgrading an existing database that already contains tables and data:

1. **Configuration:**
   Ensure `spring.flyway.baseline-on-migrate: true` and `spring.flyway.baseline-version: 1` are set (default in SentinelGrid profile configurations).

2. **Application Startup:**
   - Flyway connects to the existing database and detects existing tables without a schema history table.
   - Flyway automatically creates `flyway_schema_history` and writes a `BASELINE` record for version `1` (`Initial SentinelGrid Schema`).
   - `V1__initial_schema.sql` is **not** re-executed, ensuring zero risk of duplicate table errors, truncate, or data modification.
   - Future migrations (`V2__...`, `V3__...`) will apply sequentially on top of the baselined schema.

---

## 4. Migration Authoring Guidelines

All future schema changes must strictly follow these rules:

### File Naming Convention
- Versioned migrations: `src/main/resources/db/migration/V<Version>__<Description>.sql`
  - Examples: `V2__add_pharmacy_license_number.sql`, `V3__create_notification_events.sql`
  - Use double underscores (`__`) between version and description.
  - Descriptions should use snake_case or words separated by single underscores.
- Repeatable migrations: `R__<Description>.sql` (for idempotent views, triggers, functions).

### Best Practices for Production Safety
1. **Never Modify Past Migrations:** Once a migration has been applied in any shared environment or committed to main, its checksum is locked. Modifying an existing migration file will cause Flyway checksum validation to fail on startup.
2. **Backward Compatibility:** Schema changes must be backward-compatible with running application instances (e.g., expand-and-contract pattern for column renames).
3. **Non-Blocking DDL:**
   - Use `CREATE INDEX CONCURRENTLY` where appropriate on large tables.
   - Avoid long table locks during `ALTER TABLE` statements.
   - Always supply explicit `DEFAULT` values for non-nullable new columns or use a two-step backfill migration.
4. **Idempotency Safeguards:** Where supported, write DDL statements with safety clauses:
   - `CREATE TABLE IF NOT EXISTS ...`
   - `CREATE INDEX IF NOT EXISTS ...`
   - `ALTER TABLE ... ADD COLUMN IF NOT EXISTS ...`

---

## 5. Troubleshooting & Recovery Procedures

### 1. Migration Checksum Mismatch (`FlywayValidateException`)
- **Cause:** A previously applied migration script was modified locally or on disk.
- **Resolution:**
  - In development: Revert the file back to its exact applied state or run `mvn flyway:repair` if changes are intentional in local environment.
  - In production: Never modify applied migration files. Apply a forward migration (`V<Next>__...`).

### 2. Failed Migration Recovery
If a migration fails mid-execution (e.g., syntax error, lock timeout):
1. Identify the root cause from application startup logs.
2. Fix the SQL script.
3. If Flyway marked the migration as failed in `flyway_schema_history` (`success = false`):
   ```bash
   mvn flyway:repair
   ```
   Or via `psql`:
   ```sql
   DELETE FROM flyway_schema_history WHERE success = false;
   ```
4. Re-run migration.

---

## 6. Automated Verification

SentinelGrid includes automated integration tests verifying both migration paths:

- Test suite: `com.sentragrid.migration.PostgresFlywayMigrationIntegrationTest`
  - `testFreshDatabaseMigration`: Applies V1 schema to a clean database and verifies all 10 tables, constraints, and indexes.
  - `testHibernateSchemaValidationOnFreshMigratedDatabase`: Verifies Hibernate `ddl-auto: validate` against the fresh schema.
  - `testExistingDatabaseBaselinePreservesData`: Verifies baseline migration preserves existing records with zero data loss.
  - `testMigrationIdempotencyAndRepeatedRuns`: Verifies multiple startup cycles without errors.

To run the migration test suite:
```bash
mvn test -Dtest=PostgresFlywayMigrationIntegrationTest
```

