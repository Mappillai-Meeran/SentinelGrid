#!/usr/bin/env bash
# ==============================================================================
# SentinelGrid — Docker Entrypoint Role Provisioner
# Mount to: /docker-entrypoint-initdb.d/01-init-roles.sh
# ==============================================================================
set -euo pipefail

echo "[INFO] Initializing SentinelGrid least-privilege roles..."

APP_USER="${SENTINEL_APP_USER:-sentinel_app}"
APP_PASSWORD="${SENTINEL_APP_PASSWORD:?SENTINEL_APP_PASSWORD must be supplied; no default is permitted}"

BACKUP_USER="${SENTINEL_BACKUP_USER:-sentinel_backup}"
BACKUP_PASSWORD="${SENTINEL_BACKUP_PASSWORD:?SENTINEL_BACKUP_PASSWORD must be supplied; no default is permitted}"

if [[ ${#APP_PASSWORD} -lt 24 || ${#BACKUP_PASSWORD} -lt 24 ]]; then
  echo "[ERROR] Database role passwords must each contain at least 24 characters." >&2
  exit 1
fi

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    DO \$\$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${APP_USER}') THEN
            CREATE ROLE ${APP_USER} WITH LOGIN PASSWORD '${APP_PASSWORD}';
        END IF;

        IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '${BACKUP_USER}') THEN
            CREATE ROLE ${BACKUP_USER} WITH LOGIN PASSWORD '${BACKUP_PASSWORD}';
        END IF;
    END \$\$;

    GRANT CONNECT ON DATABASE ${POSTGRES_DB} TO ${APP_USER}, ${BACKUP_USER};
    GRANT USAGE ON SCHEMA public TO ${APP_USER}, ${BACKUP_USER};
    REVOKE CREATE ON SCHEMA public FROM ${APP_USER}, ${BACKUP_USER};

    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ${APP_USER};
    GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO ${APP_USER};
    REVOKE TRUNCATE ON ALL TABLES IN SCHEMA public FROM ${APP_USER};

    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${APP_USER};
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO ${APP_USER};
    ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE TRUNCATE ON TABLES FROM ${APP_USER};

    GRANT SELECT ON ALL TABLES IN SCHEMA public TO ${BACKUP_USER};
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ${BACKUP_USER};
    REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON ALL TABLES IN SCHEMA public FROM ${BACKUP_USER};

    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO ${BACKUP_USER};
    ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO ${BACKUP_USER};
EOSQL

echo "[INFO] Least-privilege roles initialized successfully."

