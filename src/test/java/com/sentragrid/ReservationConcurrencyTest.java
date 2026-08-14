package com.sentragrid;

import com.sentragrid.entity.Inventory;
import com.sentragrid.entity.Medicine;
import com.sentragrid.entity.Pharmacy;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.MedicineRepository;
import com.sentragrid.repository.PharmacyRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class ReservationConcurrencyTest {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private PharmacyRepository pharmacyRepository;

    @Autowired
    private MedicineRepository medicineRepository;

    @Test
    @DisplayName("Concurrency Test - Optimistic Locking prevents double booking")
    void testOptimisticLockingOnConcurrentStockReservation() throws InterruptedException {
        // Setup initial entities
        Pharmacy pharmacy = pharmacyRepository.save(Pharmacy.builder()
                .name("Concurrency Pharmacy")
                .address("100 Tech Park")
                .city("Bangalore")
                .contactNumber("9999999999")
                .build());

        Medicine medicine = medicineRepository.save(Medicine.builder()
                .name("ConcurrentMed 500mg")
                .category("CRITICAL")
                .requiresPrescription(false)
                .build());

        Inventory initialInventory = inventoryRepository.save(Inventory.builder()
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(1)
                .reservedQuantity(0)
                .build());

        Long inventoryId = initialInventory.getId();

        int numberOfThreads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger optimisticLockFailureCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            executor.submit(() -> {
                try {
                    latch.await(); // Wait for sync start signal

                    // Fetch inventory state
                    Inventory inv = inventoryRepository.findById(inventoryId).orElseThrow();

                    // Attempt to reserve quantity
                    inv.setReservedQuantity(inv.getReservedQuantity() + 1);

                    // Save with @Version check
                    inventoryRepository.save(inv);
                    successCount.incrementAndGet();

                } catch (ObjectOptimisticLockingFailureException e) {
                    optimisticLockFailureCount.incrementAndGet();
                } catch (Exception e) {
                    // unexpected exception
                }
            });
        }

        // Trigger both threads simultaneously
        latch.countDown();
        executor.shutdown();
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);

        // Verify: Exactly one transaction succeeds, and concurrent transaction fails with optimistic lock failure
        assertEquals(1, successCount.get(), "Exactly one concurrent reservation should succeed");
        assertEquals(1, optimisticLockFailureCount.get(), "One concurrent reservation must fail with ObjectOptimisticLockingFailureException");

        Inventory finalInventory = inventoryRepository.findById(inventoryId).orElseThrow();
        assertEquals(1, finalInventory.getReservedQuantity(), "Reserved quantity should be updated exactly once");
    }
}
