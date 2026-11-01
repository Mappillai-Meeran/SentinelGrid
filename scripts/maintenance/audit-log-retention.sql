-- ==============================================================================
-- SentinelGrid Production Maintenance: Audit Log Retention & Pruning Script
-- ==============================================================================
-- Purpose:
--   Safely prune historical audit records older than the designated retention
--   threshold (default: 90 days) without causing long table locks, connection
--   timeouts, or excessive WAL generation.
--
-- Execution Strategy:
--   - Iterates in bounded batches (default: 5,000 records per transaction).
--   - Leverages `idx_audit_logs_timestamp` to scan expired records quickly.
--   - Releases locks between batches.
--
-- Partitioning Architecture Guidance (Scale > 10M records):
--   For large-scale production deployments with high audit frequency, partition
--   audit_logs by RANGE ("timestamp") into monthly tables. Dropping an expired
--   month (e.g., DROP TABLE audit_logs_y2025m01) executes in O(1) time without
--   DML locking or WAL overhead.
-- ==============================================================================

DO $$
DECLARE
    retention_days CONSTANT INT := 90;
    batch_size CONSTANT INT := 5000;
    cutoff_timestamp TIMESTAMP WITH TIME ZONE;
    deleted_in_batch INT := 0;
    total_deleted INT := 0;
BEGIN
    cutoff_timestamp := CURRENT_TIMESTAMP - (retention_days || ' days')::INTERVAL;
    
    RAISE NOTICE 'Starting audit log retention purge for records older than % (retention: % days)',
        cutoff_timestamp, retention_days;

    LOOP
        WITH doomed AS (
            SELECT id
            FROM audit_logs
            WHERE "timestamp" < cutoff_timestamp
            LIMIT batch_size
            FOR UPDATE SKIP LOCKED
        )
        DELETE FROM audit_logs
        WHERE id IN (SELECT id FROM doomed);

        GET DIAGNOSTICS deleted_in_batch = ROW_COUNT;
        total_deleted := total_deleted + deleted_in_batch;

        EXIT WHEN deleted_in_batch = 0;

        RAISE NOTICE 'Deleted batch of % records (cumulative deleted: %)', deleted_in_batch, total_deleted;
    END LOOP;

    RAISE NOTICE 'Audit log pruning complete. Total records purged: %', total_deleted;
END $$;

