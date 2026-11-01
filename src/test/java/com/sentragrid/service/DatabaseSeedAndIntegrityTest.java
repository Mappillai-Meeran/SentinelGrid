package com.sentragrid.service;

import com.sentragrid.entity.Inventory;
import com.sentragrid.entity.Medicine;
import com.sentragrid.entity.Pharmacy;
import com.sentragrid.entity.Reservation;
import com.sentragrid.entity.User;
import com.sentragrid.entity.enums.ReservationStatus;
import com.sentragrid.entity.enums.UserRole;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.MedicineRepository;
import com.sentragrid.repository.PharmacyRepository;
import com.sentragrid.repository.ReservationRepository;
import com.sentragrid.repository.UserRepository;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class DatabaseSeedAndIntegrityTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Autowired
    private DataSource dataSource;

    private void executeActualDataSql() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("data.sql"));
        populator.setContinueOnError(true);
        populator.execute(dataSource);
    }

    @Test
    @DisplayName("Step 5B.1 - Fresh development databases receive intended demo seed users and inventory")
    void testFreshDatabaseReceivesDemoSeedData() {
        Optional<User> patient1 = userRepository.findByUsername("patient1");
        Optional<User> pharmacist1 = userRepository.findByUsername("pharmacist1");
        Optional<User> admin1 = userRepository.findByUsername("admin1");
        Optional<User> manager1 = userRepository.findByUsername("manager1");

        assertTrue(patient1.isPresent(), "Demo patient1 must exist");
        assertTrue(pharmacist1.isPresent(), "Demo pharmacist1 must exist");
        assertTrue(admin1.isPresent(), "Demo admin1 must exist");
        assertTrue(manager1.isPresent(), "Demo manager1 must exist");

        assertEquals(UserRole.PATIENT, patient1.get().getRole());
        assertEquals(UserRole.PHARMACIST, pharmacist1.get().getRole());
        assertEquals(UserRole.ADMIN, admin1.get().getRole());
        assertEquals(UserRole.PHARMACY_MANAGER, manager1.get().getRole());

        // Verify inventory records are seeded
        assertTrue(inventoryRepository.count() >= 29, "At least 29 demo inventory records must be seeded");
    }

    @Test
    @DisplayName("Step 5B.1 - Existing user passwords remain unchanged when actual data.sql runs")
    @Transactional
    void testExistingUserPasswordsNotOverwrittenBySeedScript() {
        String customPasswordHash = "$2a$10$customUniquePasswordHashThatMustNeverBeOverwritten12345";
        User ordinaryUser = User.builder()
                .username("ordinary_patient_test")
                .email("ordinary_patient@example.com")
                .password(customPasswordHash)
                .role(UserRole.PATIENT)
                .build();
        User savedUser = userRepository.saveAndFlush(ordinaryUser);

        // Execute the actual data.sql script file via ResourceDatabasePopulator
        executeActualDataSql();

        User reloadedUser = userRepository.findById(savedUser.getId())
                .orElseThrow(() -> new AssertionError("User should exist"));

        assertEquals(customPasswordHash, reloadedUser.getPassword(),
                "Ordinary user password must NEVER be overwritten by actual data.sql execution");
        assertEquals(UserRole.PATIENT, reloadedUser.getRole(),
                "Ordinary user role must remain unchanged");
    }

    @Test
    @DisplayName("Step 5B.1 - Existing pharmacy assignments for other staff are not overwritten by actual data.sql")
    @Transactional
    void testOrdinaryStaffPharmacyAssignmentNotOverwritten() {
        Pharmacy pharmacy2 = pharmacyRepository.findById(2L)
                .orElseThrow(() -> new AssertionError("Pharmacy ID 2 must exist"));

        User staffUser = User.builder()
                .username("custom_pharmacist_branch2")
                .email("pharmacist_branch2@example.com")
                .password("$2a$10$dummyHash12345678901234567890123456789012345678901234567890")
                .role(UserRole.PHARMACIST)
                .pharmacy(pharmacy2)
                .build();
        User savedStaff = userRepository.saveAndFlush(staffUser);

        // Execute the actual data.sql script file
        executeActualDataSql();

        User reloadedStaff = userRepository.findById(savedStaff.getId())
                .orElseThrow(() -> new AssertionError("Staff user must exist"));

        assertEquals(2L, reloadedStaff.getPharmacy().getId(),
                "Staff member at Pharmacy 2 must NOT have their branch assignment changed by data.sql");
    }

    @Test
    @DisplayName("Step 5B.1 - Existing inventory and reserved quantities remain unchanged on actual data.sql execution")
    @Transactional
    void testExistingInventoryNotOverwrittenOnRepeatedSeed() {
        Inventory inventory = inventoryRepository.findById(1L)
                .orElseThrow(() -> new AssertionError("Inventory ID 1 must exist"));

        int originalQuantity = inventory.getQuantity();
        int originalReserved = inventory.getReservedQuantity();

        // Simulate live adjustments (e.g. active reservations and sales)
        inventory.setQuantity(420);
        inventory.setReservedQuantity(77);
        inventoryRepository.saveAndFlush(inventory);

        // Execute the actual data.sql script file
        executeActualDataSql();

        Inventory reloaded = inventoryRepository.findById(1L)
                .orElseThrow(() -> new AssertionError("Inventory ID 1 must exist"));

        assertEquals(420, reloaded.getQuantity(),
                "Live quantity must be preserved and NOT reset by actual data.sql execution");
        assertEquals(77, reloaded.getReservedQuantity(),
                "Active reserved quantity must be preserved and NOT reset by actual data.sql execution");

        // Restore original state
        inventory.setQuantity(originalQuantity);
        inventory.setReservedQuantity(originalReserved);
        inventoryRepository.saveAndFlush(inventory);
    }

    @Test
    @DisplayName("Step 5B.1 - PostgreSQL identity sequence generates collision-free IDs after seed inserts")
    @Transactional
    void testNewInventoryGetsUniqueGeneratedIdAfterExplicitSeedIds() {
        Pharmacy pharmacy = pharmacyRepository.findById(3L)
                .orElseThrow(() -> new AssertionError("Pharmacy 3 must exist"));
        Medicine medicine = medicineRepository.findById(1L)
                .orElseThrow(() -> new AssertionError("Medicine 1 must exist"));

        // Delete if already exists from earlier runs
        inventoryRepository.findByPharmacyIdAndMedicineId(3L, 1L).ifPresent(inventoryRepository::delete);
        inventoryRepository.flush();

        // Insert new inventory record using database-generated ID
        Inventory newInventory = Inventory.builder()
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(150)
                .reservedQuantity(0)
                .build();

        Inventory savedInventory = inventoryRepository.saveAndFlush(newInventory);

        assertNotNull(savedInventory.getId(), "Generated ID must not be null");
        assertTrue(savedInventory.getId() >= 30,
                "Newly generated inventory ID must be >= 30 to avoid collisions with seeded IDs 1-29. Actual: " + savedInventory.getId());
    }

    @Test
    @DisplayName("Step 5B.1 - Sequence synchronization never lowers an advanced sequence")
    @Transactional
    void testSequenceSynchronizationNeverLowersSequence() {
        // Explicitly advance sequence to 500
        jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('inventory', 'id'), 500, true)");

        // Execute actual data.sql which contains sequence synchronization
        executeActualDataSql();

        // Verify sequence is still at least 500 and was not lowered back to 29
        Long currentSeqVal = jdbcTemplate.queryForObject(
                "SELECT last_value FROM pg_sequences WHERE schemaname = 'public' AND sequencename = 'inventory_id_seq'",
                Long.class
        );
        assertNotNull(currentSeqVal);
        assertTrue(currentSeqVal >= 500,
                "Sequence synchronization must NEVER lower an advanced sequence. Current value: " + currentSeqVal);

        // Verify next generated ID is > 500
        Pharmacy pharmacy = pharmacyRepository.findById(2L).orElseThrow();
        Medicine medicine = medicineRepository.findById(2L).orElseThrow();
        inventoryRepository.findByPharmacyIdAndMedicineId(2L, 2L).ifPresent(inventoryRepository::delete);
        inventoryRepository.flush();

        Inventory inv = Inventory.builder()
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(50)
                .reservedQuantity(0)
                .build();
        Inventory saved = inventoryRepository.saveAndFlush(inv);
        assertTrue(saved.getId() > 500, "Generated ID after sequence at 500 must be > 500, actual: " + saved.getId());
    }

    @Test
    @DisplayName("Step 5B.1 - Repeated execution of actual data.sql is idempotent and safe")
    void testRepeatedExecutionOfActualDataSqlIsIdempotent() {
        long userCountBefore = userRepository.count();
        long inventoryCountBefore = inventoryRepository.count();
        long pharmacyCountBefore = pharmacyRepository.count();
        long medicineCountBefore = medicineRepository.count();

        // Run data.sql twice consecutively
        executeActualDataSql();
        executeActualDataSql();

        assertEquals(userCountBefore, userRepository.count(), "User count should be stable across repeated runs");
        assertEquals(inventoryCountBefore, inventoryRepository.count(), "Inventory count should be stable across repeated runs");
        assertEquals(pharmacyCountBefore, pharmacyRepository.count(), "Pharmacy count should be stable across repeated runs");
        assertEquals(medicineCountBefore, medicineRepository.count(), "Medicine count should be stable across repeated runs");
    }

    @Test
    @DisplayName("Step 5B.1 - Existing reservations and their stock holds survive actual data.sql operations")
    @Transactional
    void testExistingReservationsAndStockHoldsPreservedOnSeed() {
        User patient = userRepository.findByUsername("patient1").orElseThrow();
        Pharmacy pharmacy = pharmacyRepository.findById(1L).orElseThrow();
        Medicine medicine = medicineRepository.findById(1L).orElseThrow();

        Reservation reservation = Reservation.builder()
                .patient(patient)
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(3)
                .status(ReservationStatus.PENDING)
                .expiresAt(LocalDateTime.now().plusMinutes(25))
                .prescriptionVerified(true)
                .build();
        Reservation savedReservation = reservationRepository.saveAndFlush(reservation);

        // Execute the actual data.sql script file
        executeActualDataSql();

        Reservation reloadedReservation = reservationRepository.findById(savedReservation.getId()).orElseThrow();
        assertEquals(ReservationStatus.PENDING, reloadedReservation.getStatus());
        assertEquals(3, reloadedReservation.getQuantity());
    }

    @Test
    @DisplayName("Step 5B.1 - Reservation entity defines composite expiry index and database reflects it")
    void testReservationCompositeExpiryIndexDefined() {
        Table tableAnnotation = Reservation.class.getAnnotation(Table.class);
        assertNotNull(tableAnnotation, "Reservation entity must have @Table annotation");

        Index[] indexes = tableAnnotation.indexes();
        assertTrue(indexes.length > 0, "Reservation entity must define indexes");

        boolean hasExpiryIndex = Arrays.stream(indexes).anyMatch(idx ->
                "idx_reservations_status_expires_at".equalsIgnoreCase(idx.name()) &&
                "status, expires_at".equalsIgnoreCase(idx.columnList()));

        assertTrue(hasExpiryIndex, "Reservation entity must define idx_reservations_status_expires_at on (status, expires_at)");

        // Verify index in database schema
        List<Map<String, Object>> indexInfo = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'reservations' AND indexname = 'idx_reservations_status_expires_at'"
        );
        assertEquals(1, indexInfo.size(), "idx_reservations_status_expires_at must exist exactly once in the database schema");
    }

    @Test
    @DisplayName("Step 5B.1 - Effective SQL initialization mode is always in local profile")
    void testLocalProfileSqlInitMode() {
        assertEquals("always", environment.getProperty("spring.sql.init.mode"),
                "Local profile must configure spring.sql.init.mode=always for intentional demo seeding");
    }
}
