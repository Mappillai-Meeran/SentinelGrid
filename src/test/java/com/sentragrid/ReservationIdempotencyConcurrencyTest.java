package com.sentragrid;

import com.sentragrid.dto.ReservationRequestDto;
import com.sentragrid.dto.ReservationResponseDto;
import com.sentragrid.entity.IdempotencyRecord;
import com.sentragrid.entity.Inventory;
import com.sentragrid.entity.Medicine;
import com.sentragrid.entity.Pharmacy;
import com.sentragrid.entity.Reservation;
import com.sentragrid.entity.User;
import com.sentragrid.entity.enums.UserRole;
import com.sentragrid.repository.IdempotencyRecordRepository;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.MedicineRepository;
import com.sentragrid.repository.PharmacyRepository;
import com.sentragrid.repository.ReservationRepository;
import com.sentragrid.repository.UserRepository;
import com.sentragrid.service.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class ReservationIdempotencyConcurrencyTest {

    @Autowired
    private ReservationService reservationService;

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
    private IdempotencyRecordRepository idempotencyRecordRepository;

    @Test
    @DisplayName("Concurrency Test - Simultaneous POST requests with same Idempotency-Key create exactly ONE reservation and increment stock ONCE")
    void testConcurrentSameKeyRequests_CreatesSingleReservationAndSingleStockIncrement() throws InterruptedException {
        // Fetch existing seeded patient or create a fresh isolated user
        User patient = userRepository.findByUsername("patient_john").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("idempotency_patient_" + UUID.randomUUID())
                        .password("password123")
                        .role(UserRole.PATIENT)
                        .email("idempotency_" + UUID.randomUUID() + "@example.com")
                        .build()));

        Pharmacy pharmacy = pharmacyRepository.findById(1L).orElseThrow();
        Medicine medicine = medicineRepository.findById(1L).orElseThrow();

        Inventory initialInventory = inventoryRepository.findByPharmacyIdAndMedicineId(pharmacy.getId(), medicine.getId())
                .orElseGet(() -> inventoryRepository.save(Inventory.builder()
                        .pharmacy(pharmacy)
                        .medicine(medicine)
                        .quantity(100)
                        .reservedQuantity(0)
                        .build()));

        if (initialInventory.getAvailableQuantity() < 10) {
            initialInventory.setQuantity(initialInventory.getReservedQuantity() + 50);
            initialInventory = inventoryRepository.saveAndFlush(initialInventory);
        }

        int initialReserved = initialInventory.getReservedQuantity();

        ReservationRequestDto request = ReservationRequestDto.builder()
                .pharmacyId(pharmacy.getId())
                .medicineId(medicine.getId())
                .quantity(2)
                .prescriptionNumber("RX-CONC-123")
                .doctorName("Dr. Concurrent")
                .doctorRegNumber("MCI-CONC-99")
                .build();

        String idempotencyKey = "concurrent-key-" + UUID.randomUUID();

        int numberOfThreads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        ReservationResponseDto[] results = new ReservationResponseDto[numberOfThreads];
        Throwable[] exceptions = new Throwable[numberOfThreads];

        for (int i = 0; i < numberOfThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    latch.await(); // Synchronize start across threads
                    results[index] = reservationService.createReservation(patient.getId(), request, idempotencyKey);
                } catch (Throwable t) {
                    exceptions[index] = t;
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Release latch to start all threads concurrently
        latch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All concurrent requests should complete within timeout");

        // Verify zero uncaught exceptions
        for (int i = 0; i < numberOfThreads; i++) {
            assertNull(exceptions[i], "Request thread " + i + " failed with exception: " + (exceptions[i] != null ? exceptions[i].getMessage() : ""));
            assertNotNull(results[i], "Request thread " + i + " should return a valid ReservationResponseDto");
        }

        // 1. All concurrent calls must return the EXACT same reservation ID
        Long expectedReservationId = results[0].getId();
        for (int i = 1; i < numberOfThreads; i++) {
            assertEquals(expectedReservationId, results[i].getId(), "All concurrent requests must return the exact same reservation ID");
        }

        // 2. Stock reserved quantity must be incremented EXACTLY ONCE (+2, not +2 * 4 = +8)
        Inventory updatedInventory = inventoryRepository.findById(initialInventory.getId()).orElseThrow();
        assertEquals(initialReserved + 2, updatedInventory.getReservedQuantity(), "Reserved quantity must be incremented exactly once by 2");

        // 3. Exactly ONE IdempotencyRecord in database for this key
        List<IdempotencyRecord> records = idempotencyRecordRepository.findAll()
                .stream()
                .filter(r -> r.getUser().getId().equals(patient.getId()) && r.getIdempotencyKey().equals(idempotencyKey))
                .toList();
        assertEquals(1, records.size(), "Exactly 1 idempotency record must exist for this key");
        assertEquals(expectedReservationId, records.get(0).getReservation().getId());
    }
}
