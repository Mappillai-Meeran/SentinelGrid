# SentinelGrid Production Guide: Operational Reliability, Indexing & High Availability

## 1. Overview & Purpose

This document provides technical reference and operational runbooks for SentinelGrid's database query performance, sequence synchronization, connection pooling, automated recovery, and container lifecycle management.

---

## 2. Operational Query Patterns & Indexing Strategy

### 2.1 Targeted Operational Indexes (Flyway `V2__operational_indexes.sql`)

SentinelGrid operates high-frequency transactional and auditing workflows across patients, pharmacists, and compliance officers. To prevent table scans and eliminate CPU-heavy sorting as data volumes scale, five targeted composite B-Tree indexes were created in Flyway migration `V2` and mapped in JPA entities:

| Index Name | Target Table | Columns & Sort Direction | Targeted Workflows & Queries |
| :--- | :--- | :--- | :--- |
| `idx_reservations_patient_status_created` | `reservations` | `(patient_id, status, created_at DESC)` | Patient portal: Active reservations history, filtering by status, ordered chronologically. |
| `idx_reservations_pharmacy_status_created` | `reservations` | `(pharmacy_id, status, created_at DESC)` | Pharmacist dispense queue: Filtering by branch pharmacy and status (`CONFIRMED`, `PENDING`). |
| `idx_audit_logs_entity` | `audit_logs` | `(entity_name, entity_id)` | Regulatory & dispute drilldown: Tracking full audit trail of a specific reservation or inventory item. |
| `idx_audit_logs_timestamp` | `audit_logs` | `("timestamp" DESC)` | Global audit stream: Live administrative observability feed, latest actions first. |
| `idx_inventory_pharmacy_qty` | `inventory` | `(pharmacy_id, quantity)` | Pharmacy inventory lookup: Real-time stock availability and reservation eligibility checks. |

### 2.2 Benchmark Validation: `EXPLAIN (ANALYZE, BUFFERS)`

Benchmarked against live PostgreSQL 16 database (`sentinelgrid`):

#### A. Audit Log Drilldown by Entity
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM audit_logs
WHERE entity_name = 'Reservation' AND entity_id = 1;
```
- **Unindexed:** `Seq Scan on audit_logs (cost=0.00..17.77 rows=1 width=139) (actual time=0.038..0.102 rows=1 loops=1) Buffers: shared hit=10`
- **Indexed (`idx_audit_logs_entity`):** `Index Scan using idx_audit_logs_entity on audit_logs (cost=0.27..8.29 rows=1 width=139) (actual time=0.015..0.016 rows=1 loops=1) Buffers: shared hit=3`
- **Result:** 70% shared buffer reduction, index lookup directly by entity identifier.

#### B. Audit Log Global Stream
```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM audit_logs
ORDER BY "timestamp" DESC
LIMIT 50;
```
- **Unindexed:** Required `Sort (cost=32.25..32.38 rows=50 width=139)` with `Sort Method: top-N heapsort` and full sequential table scan.
- **Indexed (`idx_audit_logs_timestamp`):** `Index Scan Backward using idx_audit_logs_timestamp on audit_logs (cost=0.27..4.17 rows=50 width=139)`
- **Result:** **Sort eliminated entirely**. Query cost reduced by **87%**, reads 50 rows in 0.021ms directly from index leaf pages.

#### C. Pharmacy & Patient Reservation Queues
- Composite indexes `(pharmacy_id, status, created_at DESC)` and `(patient_id, status, created_at DESC)` satisfy both the equality filter (`pharmacy_id = ? AND status = ?`) and the ordering requirement (`ORDER BY created_at DESC`) within a single index scan, completely removing in-memory sorting (`quicksort` or `external merge disk sort`).

---

## 3. Sequence Synchronization & Primary Key Integrity

### 3.1 The Risk of Sequence Drift
PostgreSQL `BIGSERIAL` / `GENERATED ALWAYS AS IDENTITY` primary keys rely on underlying sequence objects (`<table_name>_id_seq`). Sequence drift occurs when records are inserted with explicit IDs (e.g. during database seeding, historical CSV loads, or partial backup restores). If the sequence `last_value` lags behind `MAX(id)`, the next application insert produces a fatal constraint violation:
`ERROR: duplicate key value violates unique constraint "<table_name>_pkey"`

### 3.2 Automated Sequence Synchronization
SentinelGrid provides `scripts/maintenance/sync-sequences.sql` which dynamically scans all 8 sequence-backed application tables:
1. `audit_logs`
2. `idempotency_records`
3. `inventory`
4. `manager_staff`
5. `medicines`
6. `pharmacies`
7. `reservations`
8. `users`

For every table, it executes:
```sql
SELECT setval(pg_get_serial_sequence(table_name, 'id'), GREATEST(COALESCE(MAX(id), 0), 1), true);
```
This guarantees `last_value >= MAX(id)` and ensures subsequent `INSERT` operations safely allocate IDs starting at `MAX(id) + 1`.

---

## 4. HikariCP Connection Pool Sizing & Leak Detection

### 4.1 Production Pool Sizing
Connection pooling is governed by HikariCP in `application-prod.yml`:
```yaml
spring:
  datasource:
    hikari:
      pool-name: SentinelGridHikariPool
      maximum-pool-size: ${HIKARI_MAX_POOL_SIZE:20}
      minimum-idle: ${HIKARI_MIN_IDLE:5}
      connection-timeout: ${HIKARI_CONNECTION_TIMEOUT:30000}
      idle-timeout: ${HIKARI_IDLE_TIMEOUT:300000}
      max-lifetime: ${HIKARI_MAX_LIFETIME:1200000}
      leak-detection-threshold: ${HIKARI_LEAK_DETECTION_THRESHOLD:60000}
```

### 4.2 Sizing Formula & Leak Detection
- **Formula:** Connection pool sizing follows PostgreSQL best practice:
  $$\text{connections} = ((\text{CPU cores} \times 2) + \text{effective spindle count})$$
  For a 4-vCPU containerized application server, 10–20 connections deliver maximum throughput without incurring database CPU thrashing or context-switching overhead.
- **Connection Leak Detection:** Set to `60000` (60 seconds). If an application thread holds a database connection longer than 60s without releasing it back to the pool, HikariCP logs a warning accompanied by the full stack trace of the offending caller.
- **Max Lifetime:** Set to 20 minutes (`1200000ms`), safely below PostgreSQL's server-side idle timeout, preventing dead socket reconnections.

---

## 5. Container Lifecycle, Restart Policies & Healthchecks

### 5.1 Docker Compose Configuration
All service containers in `docker-compose.yml` are protected by:
- `restart: unless-stopped`: Prevents permanent outages if an unexpected OOM or runtime panic occurs. The Docker daemon automatically restarts failed containers unless explicitly stopped by operations.

### 5.2 Deep Healthcheck Probes
- **Backend Application (`sentinelgrid-app`):**
  Uses Spring Boot Actuator's dedicated liveness probe:
  ```yaml
  healthcheck:
    test: ["CMD-SHELL", "wget -q --spider http://localhost:8080/actuator/health || exit 1"]
    interval: 15s
    timeout: 5s
    retries: 3
    start_period: 30s
  ```
- **PostgreSQL Database (`sentinelgrid-postgres`):**
  Uses `pg_isready -U $$POSTGRES_USER -d $$POSTGRES_DB` with 5s intervals. Dependent containers delay initialization until PostgreSQL reports healthy (`condition: service_healthy`).
- **Frontend Nginx (`sentinelgrid-frontend`):**
  Uses `wget -q --spider http://localhost:80/ || exit 1` to verify the static web server is serving assets.

---

## 6. Reservation Expiry & Downtime Recovery

### 6.1 Lifecycle Guarantee
- Reservations are created in `PENDING` or `CONFIRMED` status with a default TTL of 30 minutes (`expires_at = now() + 30m`).
- A background scheduler (`ReservationExpiryScheduler`) runs every minute (`0 */1 * * * *`).

### 6.2 Resiliency Across Service Downtime
If the application experiences maintenance downtime or a container restart:
1. Expired reservations accumulate safely with `expires_at < current_timestamp`.
2. Upon restart, the first invocation of `ReservationExpiryScheduler.processExpiredReservations()` queries all overdue pending reservations:
   ```sql
   SELECT * FROM reservations WHERE status = 'PENDING' AND expires_at < :now
   ```
3. Each expired reservation is atomically transitioned to `EXPIRED`.
4. Its reserved quantity is returned to the corresponding `inventory.reserved_quantity`, restoring available stock.
5. An audit trail record (`RESERVATION_EXPIRED`) is written to `audit_logs`.
6. A `ReservationExpiredEvent` is published to Kafka (or logged if Kafka is optional).
7. Stock is never stuck or stranded, ensuring full inventory integrity even after extended outages.

---

## 7. Audit Log Retention & Scalability Strategy

### 7.1 Retention Policy (90 Days)
To maintain constant query performance and manage disk growth, `scripts/maintenance/audit-log-retention.sql` implements bounded batch purging:
- Cutoff window: 90 days (`CURRENT_TIMESTAMP - INTERVAL '90 days'`).
- Batch size: 5,000 records per loop utilizing `FOR UPDATE SKIP LOCKED`.
- Releasing row locks and allowing PostgreSQL autovacuum to reclaim dead tuples without blocking active application writes.

### 7.2 High-Volume Partitioning Roadmap
When transaction volume exceeds 10,000,000 audit records:
- Transition `audit_logs` to declarative range partitioning:
  ```sql
  CREATE TABLE audit_logs_partitioned (
      LIKE audit_logs INCLUDING ALL
  ) PARTITION BY RANGE ("timestamp");
  ```
- With monthly partitions (e.g. `audit_logs_y2026m01`), purging historical data executes in $O(1)$ time via `DROP TABLE audit_logs_y2025m10;`, completely eliminating transaction log (WAL) generation and table locks.

