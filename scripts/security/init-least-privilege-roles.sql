-- ==============================================================================
-- SentinelGrid — Least-Privilege Database Roles Setup
-- Roles:
--   1. sentinel_migrator : DDL & schema migrations (used by Flyway)
--   2. sentinel_app      : DML only (SELECT, INSERT, UPDATE, DELETE, SEQUENCE USAGE)
--   3. sentinel_backup   : Read-only (SELECT, SEQUENCE USAGE for pg_dump)
-- ==============================================================================

-- 1. Roles must be created with explicit secrets before this grants-only script runs.
-- Use configure-roles.ps1 or init-roles.sh. This file deliberately contains no
-- passwords, fallbacks, or shell-style placeholders because PostgreSQL does not
-- expand those placeholders safely on its own.

-- 2. Database Connection Grants
GRANT CONNECT ON DATABASE sentinelgrid TO sentinel_migrator;
GRANT CONNECT ON DATABASE sentinelgrid TO sentinel_app;
GRANT CONNECT ON DATABASE sentinelgrid TO sentinel_backup;

-- 3. Schema Grants
-- Migrator has full schema administration rights
GRANT ALL ON SCHEMA public TO sentinel_migrator;

-- Runtime app and backup roles have USAGE only (no schema-level CREATE rights)
GRANT USAGE ON SCHEMA public TO sentinel_app;
GRANT USAGE ON SCHEMA public TO sentinel_backup;
REVOKE CREATE ON SCHEMA public FROM sentinel_app;
REVOKE CREATE ON SCHEMA public FROM sentinel_backup;

-- 4. Application Role (sentinel_app): Least-Privilege DML Permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO sentinel_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO sentinel_app;

-- Prohibit destructive DDL actions for application role
REVOKE TRUNCATE ON ALL TABLES IN SCHEMA public FROM sentinel_app;

-- Ensure future tables/sequences created by migrator or postgres automatically inherit DML permissions
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO sentinel_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO sentinel_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE TRUNCATE ON TABLES FROM sentinel_app;

-- 5. Backup Role (sentinel_backup): Read-Only Permissions
GRANT SELECT ON ALL TABLES IN SCHEMA public TO sentinel_backup;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO sentinel_backup;

-- Prohibit all mutations for backup role
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON ALL TABLES IN SCHEMA public FROM sentinel_backup;

-- Ensure future tables/sequences created by migrator or postgres automatically inherit read-only permissions
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO sentinel_backup;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO sentinel_backup;

