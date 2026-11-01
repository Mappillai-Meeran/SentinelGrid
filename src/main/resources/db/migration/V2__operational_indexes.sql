-- ==============================================================================
-- SentinelGrid Flyway Migration: V2__operational_indexes.sql
-- Milestone: Release Readiness & Production Hardening (Step 5D.6)
-- Target: High-throughput query performance and sort elimination
-- ==============================================================================

-- 1. Patient reservations query index: filters by patient_id + status, ordered by created_at DESC
CREATE INDEX IF NOT EXISTS idx_reservations_patient_status_created 
    ON reservations (patient_id, status, created_at DESC);

-- 2. Pharmacy dispensing queue index: filters by pharmacy_id + status, ordered by created_at DESC
CREATE INDEX IF NOT EXISTS idx_reservations_pharmacy_status_created 
    ON reservations (pharmacy_id, status, created_at DESC);

-- 3. Audit trail entity lookup index: filters by entity_name + entity_id
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity 
    ON audit_logs (entity_name, entity_id);

-- 4. Recent audit logs stream index: ordered by timestamp DESC
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp 
    ON audit_logs ("timestamp" DESC);

-- 5. Inventory fast lookup: filters by pharmacy_id and stock level
CREATE INDEX IF NOT EXISTS idx_inventory_pharmacy_qty 
    ON inventory (pharmacy_id, quantity);

