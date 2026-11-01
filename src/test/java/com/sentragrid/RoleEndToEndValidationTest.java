package com.sentragrid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentragrid.dto.InventoryAdjustRequest;
import com.sentragrid.dto.ReservationRequestDto;
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
import com.sentragrid.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class RoleEndToEndValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User e2ePatient1;
    private User e2ePatient2;
    private User pharmacist1;
    private User manager1;
    private User admin1;
    private Pharmacy pharmacy1;
    private Pharmacy pharmacy2;
    private Medicine medicine1; // Remdesivir (requires prescription, category ANTIVIRAL)
    private Medicine medicine4; // Favipiravir (OTC, category ANTIVIRAL)
    private Medicine medicine5; // Oxygen Cylinder 10L (OTC, category RESPIRATORY_SUPPORT)

    @BeforeEach
    void setUp() {
        pharmacy1 = pharmacyRepository.findById(1L).orElseThrow();
        pharmacy2 = pharmacyRepository.findById(2L).orElseThrow();
        medicine1 = medicineRepository.findById(1L).orElseThrow();
        medicine4 = medicineRepository.findById(4L).orElseThrow();
        medicine5 = medicineRepository.findById(5L).orElseThrow();

        // Dedicated test patients for RoleEndToEndValidationTest to prevent anti-hoarding pollution
        e2ePatient1 = userRepository.findByUsername("e2e_patient1").orElseGet(() -> {
            User p = User.builder()
                    .username("e2e_patient1")
                    .email("e2e_patient1@sentinel.com")
                    .password(passwordEncoder.encode("password123"))
                    .role(UserRole.PATIENT)
                    .build();
            return userRepository.save(p);
        });

        e2ePatient2 = userRepository.findByUsername("e2e_patient2").orElseGet(() -> {
            User p = User.builder()
                    .username("e2e_patient2")
                    .email("e2e_patient2@sentinel.com")
                    .password(passwordEncoder.encode("password123"))
                    .role(UserRole.PATIENT)
                    .build();
            return userRepository.save(p);
        });

        // Clean up any uncancelled reservations for dedicated test patients to ensure anti-hoarding isolation
        for (User testPatient : java.util.List.of(e2ePatient1, e2ePatient2)) {
            java.util.List<Reservation> leftovers = reservationRepository.findByPatientIdAndStatus(testPatient.getId(), ReservationStatus.PENDING);
            for (Reservation r : leftovers) {
                try {
                    reservationService.cancelReservation(r.getId(), testPatient.getUsername());
                } catch (Exception ignored) {
                }
            }
        }

        // Ensure inventory exists for medicine5 at pharmacy1 for oversell test
        inventoryRepository.findByPharmacyIdAndMedicineId(pharmacy1.getId(), medicine5.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .pharmacy(pharmacy1)
                        .medicine(medicine5)
                        .quantity(15)
                        .reservedQuantity(0)
                        .version(0L)
                        .build()));

        pharmacist1 = userRepository.findByUsername("pharmacist1").orElseThrow();
        manager1 = userRepository.findByUsername("manager1").orElseThrow();
        admin1 = userRepository.findByUsername("admin1").orElseThrow();

        // Ensure pharmacist1 is assigned to pharmacy 1
        if (pharmacist1.getPharmacy() == null || !pharmacist1.getPharmacy().getId().equals(1L)) {
            pharmacist1.setPharmacy(pharmacy1);
            userRepository.save(pharmacist1);
        }

        // Ensure manager1 is assigned to pharmacy 1
        if (manager1.getPharmacy() == null || !manager1.getPharmacy().getId().equals(1L)) {
            manager1.setPharmacy(pharmacy1);
            userRepository.save(manager1);
        }
    }

    // ==========================================
    // 1. PATIENT JOURNEY TESTS
    // ==========================================

    @Test
    @DisplayName("Patient Journey - Complete Discovery, Idempotent Hold, 30-min Timer, and Verification")
    void testPatientJourney_DiscoveryToReservationHold() throws Exception {
        // Step 1: Search Medicines in Puducherry
        mockMvc.perform(get("/api/medicines/search")
                .param("city", "Puducherry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", not(empty())));

        // Step 2: Check Initial Stock of Medicine 4 at Pharmacy 2
        Inventory invBefore = inventoryRepository.findByPharmacyIdAndMedicineId(pharmacy2.getId(), medicine4.getId())
                .orElseThrow();
        int initialAvailable = invBefore.getQuantity() - invBefore.getReservedQuantity();
        int initialReserved = invBefore.getReservedQuantity();

        // Step 3: Create Reservation with Idempotency Key
        String idempotencyKey = "E2E-KEY-" + UUID.randomUUID();
        ReservationRequestDto req = ReservationRequestDto.builder()
                .pharmacyId(pharmacy2.getId())
                .medicineId(medicine4.getId())
                .quantity(1)
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/reservations")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .with(user("e2e_patient1").roles("PATIENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.quantity").value(1))
                .andExpect(jsonPath("$.data.remainingSeconds", greaterThan(1700))) // ~30 minutes (1800s)
                .andExpect(jsonPath("$.data.remainingSeconds", lessThanOrEqualTo(1800)))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long reservationId = jsonNode.path("data").path("id").asLong();

        // Step 4: Verify Database Stock Allocation
        Inventory invAfter = inventoryRepository.findByPharmacyIdAndMedicineId(pharmacy2.getId(), medicine4.getId())
                .orElseThrow();
        assertThat(invAfter.getReservedQuantity()).isEqualTo(initialReserved + 1);
        assertThat(invAfter.getQuantity() - invAfter.getReservedQuantity()).isEqualTo(initialAvailable - 1);

        // Step 5: Test Idempotency - Repeat request with same key returns identical reservation (201 Created)
        mockMvc.perform(post("/api/reservations")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .with(user("e2e_patient1").roles("PATIENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(reservationId));

        // Step 6: Patient Listing - Verify newly created reservation is in patient's list
        mockMvc.perform(get("/api/reservations/my")
                .with(user("e2e_patient1").roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + reservationId + ")].status").value("PENDING"));

        // Step 7: Patient Cancellation and Stock Release
        mockMvc.perform(post("/api/reservations/" + reservationId + "/cancel")
                .with(user("e2e_patient1").roles("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        // Step 8: Verify Stock Restored Immediately
        Inventory invRestored = inventoryRepository.findByPharmacyIdAndMedicineId(pharmacy2.getId(), medicine4.getId())
                .orElseThrow();
        assertThat(invRestored.getReservedQuantity()).isEqualTo(initialReserved);
        assertThat(invRestored.getQuantity() - invRestored.getReservedQuantity()).isEqualTo(initialAvailable);
    }

    // ==========================================
    // 2. PHARMACIST JOURNEY TESTS
    // ==========================================

    @Test
    @DisplayName("Pharmacist Journey - Queue Ingestion, Prescription Verification, and Final Dispensing")
    void testPharmacistJourney_PrescriptionVerificationAndPickup() throws Exception {
        // Step 1: Patient reserves Prescription-Required Medicine 1 at Pharmacy 1
        ReservationRequestDto req = ReservationRequestDto.builder()
                .pharmacyId(pharmacy1.getId())
                .medicineId(medicine1.getId())
                .quantity(1)
                .prescriptionNumber("RX-HERO-777")
                .doctorName("Dr. Rachel Green")
                .doctorRegNumber("REG-8821")
                .build();

        MvcResult resResult = mockMvc.perform(post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .with(user("e2e_patient1").roles("PATIENT")))
                .andExpect(status().isCreated())
                .andReturn();

        long resId = objectMapper.readTree(resResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Step 2: Pharmacist loads incoming reservation queue for Pharmacy 1
        mockMvc.perform(get("/api/pharmacies/1/reservations?status=PENDING")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + resId + ")].prescriptionNumber").value("RX-HERO-777"))
                .andExpect(jsonPath("$.data[?(@.id == " + resId + ")].prescriptionVerified").value(false));

        // Step 3: Pharmacist attempts Pickup WITHOUT verification -> REJECTED (400)
        mockMvc.perform(post("/api/reservations/" + resId + "/confirm")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Prescription verification is pending")));

        // Step 4: Pharmacist verifies prescription -> SUCCESS (200)
        mockMvc.perform(post("/api/reservations/" + resId + "/verify-prescription")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prescriptionVerified").value(true));

        // Step 5: Check initial physical quantity before confirmation
        Inventory invBeforeConfirm = inventoryRepository.findByPharmacyIdAndMedicineId(pharmacy1.getId(), medicine1.getId())
                .orElseThrow();
        int physQtyBefore = invBeforeConfirm.getQuantity();
        int reservedBefore = invBeforeConfirm.getReservedQuantity();

        // Step 6: Pharmacist confirms pickup -> SUCCESS (200)
        mockMvc.perform(post("/api/reservations/" + resId + "/confirm")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        // Step 7: Verify Permanent Physical Inventory Deduction
        Inventory invAfterConfirm = inventoryRepository.findByPharmacyIdAndMedicineId(pharmacy1.getId(), medicine1.getId())
                .orElseThrow();
        assertThat(invAfterConfirm.getQuantity()).isEqualTo(physQtyBefore - 1);
        assertThat(invAfterConfirm.getReservedQuantity()).isEqualTo(reservedBefore - 1);

        // Step 8: Confirm reservation is cleared from pending queue
        mockMvc.perform(get("/api/pharmacies/1/reservations?status=PENDING")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + resId + ")]").doesNotExist());
    }

    // ==========================================
    // 3. PHARMACY MANAGER JOURNEY TESTS
    // ==========================================

    @Test
    @DisplayName("Manager Journey - Stock Adjustments, Workspace Access, and Cross-Branch Denial")
    void testManagerJourney_InventoryAdjustmentAndRBAC() throws Exception {
        // Step 1: Manager accesses workspace for assigned pharmacy
        mockMvc.perform(get("/api/manager/workspace")
                .with(user("manager1").roles("PHARMACY_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Step 2: Manager adjusts stock for assigned Pharmacy 1
        Inventory inv = inventoryRepository.findByPharmacyIdAndMedicineId(pharmacy1.getId(), medicine4.getId())
                .orElseGet(() -> {
                    Inventory newInv = Inventory.builder()
                            .pharmacy(pharmacy1)
                            .medicine(medicine4)
                            .quantity(20)
                            .reservedQuantity(0)
                            .version(0L)
                            .build();
                    return inventoryRepository.save(newInv);
                });

        int originalQuantity = inv.getQuantity();
        InventoryAdjustRequest adjustReq = new InventoryAdjustRequest();
        adjustReq.setDelta(5);

        mockMvc.perform(patch("/api/pharmacies/1/inventory/" + inv.getId() + "/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adjustReq))
                .with(user("manager1").roles("PHARMACY_MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQuantity").value(originalQuantity + 5));

        // Step 3: Manager denied from accessing Pharmacy 2 inventory (Cross-branch 403)
        mockMvc.perform(get("/api/pharmacies/2/inventory")
                .with(user("manager1").roles("PHARMACY_MANAGER")))
                .andExpect(status().isForbidden());

        // Step 4: Manager denied from accessing global admin observability (403)
        mockMvc.perform(get("/api/system/observability")
                .with(user("manager1").roles("PHARMACY_MANAGER")))
                .andExpect(status().isForbidden());

        // Step 5: Manager denied from accessing global admin dashboard (403)
        mockMvc.perform(get("/api/admin/dashboard/summary")
                .with(user("manager1").roles("PHARMACY_MANAGER")))
                .andExpect(status().isForbidden());
    }

    // ==========================================
    // 4. ADMIN ENTERPRISE JOURNEY TESTS
    // ==========================================

    @Test
    @DisplayName("Admin Enterprise Journey - Summary Telemetry, Operating Breakdown, Leaderboard & Audit Stream")
    void testAdminJourney_EnterpriseDashboardTelemetry() throws Exception {
        // Step 1: Executive Summary Metrics with 24/7 Breakdown
        mockMvc.perform(get("/api/admin/dashboard/summary")
                .with(user("admin1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPharmacies", greaterThan(0)))
                .andExpect(jsonPath("$.data.twentyFourSevenPharmacies", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.standardHoursPharmacies", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.totalInventoryUnits", greaterThan(0)));

        // Step 2: Branch Performance Leaderboard with genuine Fulfillment Rate
        mockMvc.perform(get("/api/admin/dashboard/pharmacies/top?limit=5")
                .with(user("admin1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", not(empty())))
                .andExpect(jsonPath("$.data[0].pharmacyName", notNullValue()))
                .andExpect(jsonPath("$.data[0].reservationCount", greaterThan(0)));

        // Step 3: Audit Log Telemetry
        mockMvc.perform(get("/api/admin/dashboard/audit-logs")
                .with(user("admin1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", notNullValue()));

        // Step 4: System Observability Health
        mockMvc.perform(get("/api/system/observability")
                .with(user("admin1").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.healthStatus").value("UP"));
    }

    // ==========================================
    // 5. NEGATIVE AND ADVERSARIAL SECURITY TESTS
    // ==========================================

    @Test
    @DisplayName("Negative Security - Patient A cannot read or cancel Patient B's reservation")
    void testCrossPatient_IsolationEnforced() throws Exception {
        // Patient 1 creates a reservation
        ReservationRequestDto req = ReservationRequestDto.builder()
                .pharmacyId(pharmacy1.getId())
                .medicineId(medicine4.getId())
                .quantity(1)
                .build();

        MvcResult result = mockMvc.perform(post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .with(user("e2e_patient1").roles("PATIENT")))
                .andExpect(status().isCreated())
                .andReturn();

        long resId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Patient 2 attempts to read Patient 1's reservation -> 403 Forbidden
        mockMvc.perform(get("/api/reservations/" + resId)
                .with(user("e2e_patient2").roles("PATIENT")))
                .andExpect(status().isForbidden());

        // Patient 2 attempts to cancel Patient 1's reservation -> 403 Forbidden
        mockMvc.perform(post("/api/reservations/" + resId + "/cancel")
                .with(user("e2e_patient2").roles("PATIENT")))
                .andExpect(status().isForbidden());

        // Clean up reservation at the end of test via patient cancel API
        mockMvc.perform(post("/api/reservations/" + resId + "/cancel")
                .with(user("e2e_patient1").roles("PATIENT")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Negative Security - Pharmacist cannot access or confirm reservations for another branch")
    void testCrossBranch_PharmacistIsolationEnforced() throws Exception {
        // Create reservation at Pharmacy 2
        ReservationRequestDto req = ReservationRequestDto.builder()
                .pharmacyId(pharmacy2.getId())
                .medicineId(medicine4.getId())
                .quantity(1)
                .build();

        MvcResult result = mockMvc.perform(post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .with(user("e2e_patient1").roles("PATIENT")))
                .andExpect(status().isCreated())
                .andReturn();

        long resId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Pharmacist 1 (Pharmacy 1) attempts to confirm Pharmacy 2's reservation -> 403 Forbidden
        mockMvc.perform(post("/api/reservations/" + resId + "/confirm")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isForbidden());

        // Pharmacist 1 attempts to access Pharmacy 2's inventory -> 403 Forbidden
        mockMvc.perform(get("/api/pharmacies/2/inventory")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isForbidden());

        // Clean up reservation via patient cancel API
        mockMvc.perform(post("/api/reservations/" + resId + "/cancel")
                .with(user("e2e_patient1").roles("PATIENT")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Negative Security - Cannot confirm pickup on CANCELLED or EXPIRED reservations")
    void testStateTerminality_CannotConfirmCancelledOrExpired() throws Exception {
        // Create reservation
        ReservationRequestDto req = ReservationRequestDto.builder()
                .pharmacyId(pharmacy1.getId())
                .medicineId(medicine4.getId())
                .quantity(1)
                .build();

        MvcResult result = mockMvc.perform(post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))
                .with(user("e2e_patient1").roles("PATIENT")))
                .andExpect(status().isCreated())
                .andReturn();

        long resId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // Cancel it
        mockMvc.perform(post("/api/reservations/" + resId + "/cancel")
                .with(user("e2e_patient1").roles("PATIENT")))
                .andExpect(status().isOk());

        // Attempt pickup on CANCELLED reservation -> 400 BadRequest
        mockMvc.perform(post("/api/reservations/" + resId + "/confirm")
                .with(user("pharmacist1").roles("PHARMACIST")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Only PENDING reservations can be confirmed")));

        // Create an expired reservation directly
        Reservation expiredRes = reservationRepository.save(Reservation.builder()
                .patient(e2ePatient1)
                .pharmacy(pharmacy1)
                .medicine(medicine4)
                .quantity(1)
                .status(ReservationStatus.EXPIRED)
                .expiresAt(LocalDateTime.now().minusMinutes(5))
                .build());

        try {
            // Attempt pickup on EXPIRED reservation -> 400 BadRequest
            mockMvc.perform(post("/api/reservations/" + expiredRes.getId() + "/confirm")
                    .with(user("pharmacist1").roles("PHARMACIST")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("Only PENDING reservations can be confirmed")));
        } finally {
            reservationRepository.delete(expiredRes);
        }
    }

    @Test
    @DisplayName("Lifecycle Invariant - Cannot reserve more than available physical stock")
    void testStockInvariants_CannotOversell() throws Exception {
        Inventory inv = inventoryRepository.findByPharmacyIdAndMedicineId(pharmacy1.getId(), medicine5.getId())
                .orElseThrow();
        int initialQty = inv.getQuantity();
        int initialReserved = inv.getReservedQuantity();
        int available = initialQty - initialReserved;

        // Attempt to request available + 5 units of non-prescription Medicine 5
        ReservationRequestDto overReq = ReservationRequestDto.builder()
                .pharmacyId(pharmacy1.getId())
                .medicineId(medicine5.getId())
                .quantity(available + 5)
                .build();

        mockMvc.perform(post("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(overReq))
                .with(user("e2e_patient1").roles("PATIENT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Insufficient stock available")));

        // Assert physical inventory and reserved quantity remain unchanged
        Inventory invAfter = inventoryRepository.findByPharmacyIdAndMedicineId(pharmacy1.getId(), medicine5.getId())
                .orElseThrow();
        assertThat(invAfter.getQuantity()).isEqualTo(initialQty);
        assertThat(invAfter.getReservedQuantity()).isEqualTo(initialReserved);
    }

    @Test
    @DisplayName("Negative Security - Non-existent resource IDs return HTTP 404")
    void testResourceNotFound_InvalidIds() throws Exception {
        mockMvc.perform(get("/api/reservations/99999999")
                .with(user("admin1").roles("ADMIN")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/reservations/99999999/confirm")
                .with(user("admin1").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }
}
