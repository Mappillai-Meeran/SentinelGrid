-- ==============================================================================
-- SentinelGrid Production Maintenance: Autoincrement Sequence Synchronization
-- ==============================================================================
-- Purpose:
--   Detect and repair sequence drift across all serial/autoincrement primary key
--   sequences. Sequence drift can occur after manual data corrections, bulk data
--   loads, or partial disaster recovery restorations, leading to:
--   "duplicate key value violates unique constraint ..._pkey"
--
-- Target Entities (8 Tables):
--   1. audit_logs
--   2. idempotency_records
--   3. inventory
--   4. manager_staff
--   5. medicines
--   6. pharmacies
--   7. reservations
--   8. users
-- ==============================================================================

DO $$
DECLARE
    rec RECORD;
    v_max_id BIGINT;
    v_curr_val BIGINT;
BEGIN
    RAISE NOTICE 'Starting SentinelGrid sequence synchronization check...';

    FOR rec IN
        SELECT
            table_name,
            column_name,
            column_default,
            pg_get_serial_sequence(table_name, column_name) AS seq_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name IN (
            'audit_logs',
            'idempotency_records',
            'inventory',
            'manager_staff',
            'medicines',
            'pharmacies',
            'reservations',
            'users'
          )
          AND column_name = 'id'
          AND column_default LIKE 'nextval%'
    LOOP
        IF rec.seq_name IS NOT NULL THEN
            -- Calculate actual MAX(id) from the table
            EXECUTE format('SELECT COALESCE(MAX(%I), 0) FROM %I', rec.column_name, rec.table_name)
            INTO v_max_id;

            -- Retrieve current sequence value
            EXECUTE format('SELECT last_value FROM %I', rec.seq_name)
            INTO v_curr_val;

            IF v_curr_val <= v_max_id THEN
                -- Advance sequence to MAX(id) so next nextval() yields MAX(id) + 1
                EXECUTE format('SELECT setval(%L, %s, true)', rec.seq_name, GREATEST(v_max_id, 1));
                RAISE NOTICE 'SYNCHRONIZED sequence % for table %. Previous: %, Max ID: %, New Sequence: %',
                    rec.seq_name, rec.table_name, v_curr_val, v_max_id, GREATEST(v_max_id, 1);
            ELSE
                RAISE NOTICE 'OK: Sequence % for table % is ahead of data (Sequence: %, Max ID: %)',
                    rec.seq_name, rec.table_name, v_curr_val, v_max_id;
            END IF;
        ELSE
            RAISE WARNING 'No sequence found for table %.% with default %',
                rec.table_name, rec.column_name, rec.column_default;
        END IF;
    END LOOP;

    RAISE NOTICE 'SentinelGrid sequence synchronization complete.';
END $$;

