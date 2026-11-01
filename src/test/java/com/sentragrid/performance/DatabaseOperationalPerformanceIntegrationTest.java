package com.sentragrid.performance;

import com.sentragrid.audit.entity.AuditLog;
import com.sentragrid.audit.repository.AuditLogRepository;
import com.sentragrid.entity.Inventory;
import com.sentragrid.entity.Medicine;
import com.sentragrid.entity.Pharmacy;
import com.sentragrid.entity.Reservation;
import com.sentragrid.entity.User;
import com.sentragrid.entity.enums.ReservationStatus;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.MedicineRepository;
import com.sentragrid.repository.PharmacyRepository;
import com.sentragrid.repository.ReservationRepository;
import com.sentragrid.repository.UserRepository;
import com.sentragrid.scheduler.ReservationExpiryScheduler;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("Step 5D.6 - Operational Performance, Indexing & Reliability Integration Tests")
public class DatabaseOperationalPerformanceIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ReservationExpiryScheduler reservationExpiryScheduler;

    @Test
    @DisplayName("DB-06 / Perf-01: Verify all 5 operational indexes exist in PostgreSQL catalog")
    void testOperationalIndexesExistInDatabase() throws Exception {
        Set<String> expectedIndexes = Set.of(
                "idx_reservations_patient_status_created",
                "idx_reservations_pharmacy_status_created",
                "idx_audit_logs_entity",
                "idx_audit_logs_timestamp",
                "idx_inventory_pharmacy_qty"
        );

        Set<String> foundIndexes = new HashSet<>();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'")) {
            while (rs.next()) {
                foundIndexes.add(rs.getString("indexname"));
            }
        }

        for (String expectedIndex : expectedIndexes) {
            assertTrue(foundIndexes.contains(expectedIndex),
                    "Database must contain operational index: " + expectedIndex);
        }
    }

    @Test
    @DisplayName("DB-06 / Perf-02: Validate query plans and cost execution with EXPLAIN (BUFFERS)")
    void testQueryExecutionPlans() throws Exception {
        String[] queriesToTest = {
                "EXPLAIN (BUFFERS) SELECT * FROM reservations WHERE patient_id = 1 AND status = 'CONFIRMED' ORDER BY created_at DESC LIMIT 10",
                "EXPLAIN (BUFFERS) SELECT * FROM reservations WHERE pharmacy_id = 1 AND status = 'CONFIRMED' ORDER BY created_at DESC",
                "EXPLAIN (BUFFERS) SELECT * FROM audit_logs WHERE entity_name = 'Reservation' AND entity_id = 1",
                "EXPLAIN (BUFFERS) SELECT * FROM audit_logs ORDER BY \"timestamp\" DESC LIMIT 50",
                "EXPLAIN (BUFFERS) SELECT * FROM inventory WHERE pharmacy_id = 1 AND quantity > 0"
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String query : queriesToTest) {
                try (ResultSet rs = stmt.executeQuery(query)) {
                    StringBuilder planBuilder = new StringBuilder();
                    while (rs.next()) {
                        planBuilder.append(rs.getString(1)).append("\n");
                    }
                    String plan = planBuilder.toString();
                    assertFalse(plan.isBlank(), "Query plan must not be blank for query: " + query);
                    assertTrue(plan.contains("cost="), "Query plan must include cost analysis for: " + query);
                }
            }
        }
    }

    @Test
    @DisplayName("DB-06 / Perf-03: Verify sequence synchronization across all 8 sequence-backed tables")
    void testSequenceSynchronization() throws Exception {
        List<String> targetTables = List.of(
                "audit_logs",
                "idempotency_records",
                "inventory",
                "manager_staff",
                "medicines",
                "pharmacies",
                "reservations",
                "users"
        );

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String tableName : targetTables) {
                String checkSql = String.format(
                        "SELECT " +
                                "  COALESCE(MAX(id), 0) AS max_id, " +
                                "  (SELECT last_value FROM %s) AS last_val " +
                                "FROM %s",
                        tableName + "_id_seq",
                        tableName
                );

                try (ResultSet rs = stmt.executeQuery(checkSql)) {
                    assertTrue(rs.next(), "Must return sequence check row for table: " + tableName);
                    long maxId = rs.getLong("max_id");
                    long lastVal = rs.getLong("last_val");

                    assertTrue(lastVal >= maxId,
                            String.format("Sequence for table %s is behind data! last_val=%d, max_id=%d",
                                    tableName, lastVal, maxId));
                }
            }
        }
    }

    @Test
    @DisplayName("DB-06 / Perf-04: Verify HikariCP connection pool configuration and leak detection")
    void testHikariConnectionPoolConfiguration() {
        assertTrue(dataSource instanceof HikariDataSource, "DataSource must be an instance of HikariDataSource");
        HikariDataSource hikariDs = (HikariDataSource) dataSource;

        assertEquals("SentinelGridHikariPool", hikariDs.getPoolName(),
                "Hikari pool name must be SentinelGridHikariPool");
        assertTrue(hikariDs.getMaximumPoolSize() >= 10,
                "Maximum pool size must be at least 10 for production concurrency");
        assertTrue(hikariDs.getLeakDetectionThreshold() >= 30000,
                "Connection leak detection threshold must be active (>= 30s)");
    }

    @Test
    @DisplayName("DB-06 / Perf-05: Expiry scheduler recovers and releases stock for expired reservations on startup")
    @Transactional
    void testReservationExpirySchedulerRecovery() {
        Pharmacy pharmacy = pharmacyRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Pharmacy must exist"));
        Medicine medicine = medicineRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Medicine must exist"));
        User patient = userRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("User must exist"));

        Inventory inventory = inventoryRepository.findByPharmacyIdAndMedicineId(pharmacy.getId(), medicine.getId())
                .orElseGet(() -> {
                    Inventory inv = new Inventory();
                    inv.setPharmacy(pharmacy);
                    inv.setMedicine(medicine);
                    inv.setQuantity(50);
                    inv.setReservedQuantity(0);
                    return inventoryRepository.save(inv);
                });

        int initialReserved = inventory.getReservedQuantity();
        inventory.setReservedQuantity(initialReserved + 5);
        inventoryRepository.save(inventory);

        Reservation overdueReservation = Reservation.builder()
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(5)
                .status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().minusMinutes(10))
                .build();

        Reservation savedReservation = reservationRepository.save(overdueReservation);

        // Execute recovery process
        reservationExpiryScheduler.processExpiredReservations();

        // Verify status transition
        Reservation reloaded = reservationRepository.findById(savedReservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.EXPIRED, reloaded.getStatus(),
                "Overdue pending reservation must transition to EXPIRED");

        // Verify reserved inventory rolled back
        Inventory updatedInventory = inventoryRepository.findByPharmacyIdAndMedicineId(pharmacy.getId(), medicine.getId()).orElseThrow();
        assertEquals(initialReserved, updatedInventory.getReservedQuantity(),
                "Reserved inventory quantity must be decremented by released reservation units");

        // Verify audit log entry created
        List<AuditLog> logs = auditLogRepository.findByEntityNameAndEntityId("Reservation", savedReservation.getId());
        assertFalse(logs.isEmpty(), "Audit log must be recorded for expired reservation recovery");
        assertTrue(logs.stream().anyMatch(log -> "RESERVATION_EXPIRED".equals(log.getAction())),
                "Must log RESERVATION_EXPIRED action");
    }
}
