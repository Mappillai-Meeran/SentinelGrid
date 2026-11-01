package com.sentragrid.service;

import com.sentragrid.dto.InventoryDto;
import com.sentragrid.dto.PharmacyDto;
import lombok.Builder;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class PharmacyRecommendationTest {

    @Data
    @Builder
    static class TestInventoryItem {
        private InventoryDto inventory;
        private Integer estimatedMinutes;
        private Double roadDistanceKm;
        private Double distanceKm;
    }

    // Simulates the full multi-tier ranking priority exported by rankPharmacies in recommendation.ts
    private List<TestInventoryItem> sortInventories(List<TestInventoryItem> list, Double userLat, Double userLng) {
        return list.stream().sorted((a, b) -> {
            InventoryDto invA = a.getInventory();
            InventoryDto invB = b.getInventory();

            // Factor 1: Stock availability (in stock > out of stock)
            int aInStock = (invA.getAvailableQuantity() != null && invA.getAvailableQuantity() > 0) ? 1 : 0;
            int bInStock = (invB.getAvailableQuantity() != null && invB.getAvailableQuantity() > 0) ? 1 : 0;
            if (aInStock != bInStock) return Integer.compare(bInStock, aInStock);

            // Factor 2: Operating status (OPEN=3, UNKNOWN=2, CLOSED=1)
            int aStatus = getStatusScore(invA.getPharmacy() != null ? invA.getPharmacy().getStatus() : null);
            int bStatus = getStatusScore(invB.getPharmacy() != null ? invB.getPharmacy().getStatus() : null);
            if (aStatus != bStatus) return Integer.compare(bStatus, aStatus);

            // Factors 3-5: Travel ETA / Road Distance / Haversine distance
            if (userLat != null && userLng != null) {
                // Estimated travel minutes
                if (a.getEstimatedMinutes() != null && b.getEstimatedMinutes() != null) {
                    if (!a.getEstimatedMinutes().equals(b.getEstimatedMinutes())) {
                        return Integer.compare(a.getEstimatedMinutes(), b.getEstimatedMinutes());
                    }
                }
                if (a.getEstimatedMinutes() != null && b.getEstimatedMinutes() == null) return -1;
                if (a.getEstimatedMinutes() == null && b.getEstimatedMinutes() != null) return 1;

                // Road distance
                if (a.getRoadDistanceKm() != null && b.getRoadDistanceKm() != null) {
                    if (!a.getRoadDistanceKm().equals(b.getRoadDistanceKm())) {
                        return Double.compare(a.getRoadDistanceKm(), b.getRoadDistanceKm());
                    }
                }
                if (a.getRoadDistanceKm() != null && b.getRoadDistanceKm() == null) return -1;
                if (a.getRoadDistanceKm() == null && b.getRoadDistanceKm() != null) return 1;

                // Haversine distance
                if (a.getDistanceKm() != null && b.getDistanceKm() != null) {
                    if (!a.getDistanceKm().equals(b.getDistanceKm())) {
                        return Double.compare(a.getDistanceKm(), b.getDistanceKm());
                    }
                }
                if (a.getDistanceKm() != null && b.getDistanceKm() == null) return -1;
                if (a.getDistanceKm() == null && b.getDistanceKm() != null) return 1;
            }

            return 0;
        }).collect(Collectors.toList());
    }

    private int getStatusScore(String status) {
        if ("OPEN".equalsIgnoreCase(status)) return 3;
        if ("UNKNOWN".equalsIgnoreCase(status)) return 2;
        return 1;
    }

    @Test
    @DisplayName("TEST 1: In-stock + OPEN pharmacy beats out-of-stock + OPEN pharmacy")
    void testInStockBeatsOutOfStock() {
        PharmacyDto pOpenInStock = PharmacyDto.builder().id(1L).name("Pharm A").status("OPEN").build();
        TestInventoryItem itemInStock = TestInventoryItem.builder()
                .inventory(InventoryDto.builder().id(101L).pharmacy(pOpenInStock).availableQuantity(5).build())
                .build();

        PharmacyDto pOpenOut = PharmacyDto.builder().id(2L).name("Pharm B").status("OPEN").build();
        TestInventoryItem itemOut = TestInventoryItem.builder()
                .inventory(InventoryDto.builder().id(102L).pharmacy(pOpenOut).availableQuantity(0).build())
                .build();

        List<TestInventoryItem> sorted = sortInventories(Arrays.asList(itemOut, itemInStock), 11.93, 79.83);
        assertEquals(1L, sorted.get(0).getInventory().getPharmacy().getId());
    }

    @Test
    @DisplayName("TEST 2: In-stock + OPEN pharmacy beats in-stock + CLOSED pharmacy")
    void testOpenBeatsClosed() {
        PharmacyDto pClosed = PharmacyDto.builder().id(1L).name("Closed Pharm").status("CLOSED").build();
        TestInventoryItem itemClosed = TestInventoryItem.builder()
                .inventory(InventoryDto.builder().id(101L).pharmacy(pClosed).availableQuantity(10).build())
                .build();

        PharmacyDto pOpen = PharmacyDto.builder().id(2L).name("Open Pharm").status("OPEN").build();
        TestInventoryItem itemOpen = TestInventoryItem.builder()
                .inventory(InventoryDto.builder().id(102L).pharmacy(pOpen).availableQuantity(5).build())
                .build();

        List<TestInventoryItem> sorted = sortInventories(Arrays.asList(itemClosed, itemOpen), 11.93, 79.83);
        assertEquals(2L, sorted.get(0).getInventory().getPharmacy().getId());
    }

    @Test
    @DisplayName("TEST 3: Unknown operating hours are NOT treated as OPEN")
    void testUnknownNotTreatedAsOpen() {
        PharmacyDto pUnknown = PharmacyDto.builder().id(1L).name("Unknown Pharm").status("UNKNOWN").build();
        TestInventoryItem itemUnknown = TestInventoryItem.builder()
                .inventory(InventoryDto.builder().id(101L).pharmacy(pUnknown).availableQuantity(10).build())
                .build();

        PharmacyDto pOpen = PharmacyDto.builder().id(2L).name("Open Pharm").status("OPEN").build();
        TestInventoryItem itemOpen = TestInventoryItem.builder()
                .inventory(InventoryDto.builder().id(102L).pharmacy(pOpen).availableQuantity(10).build())
                .build();

        List<TestInventoryItem> sorted = sortInventories(Arrays.asList(itemUnknown, itemOpen), 11.93, 79.83);
        assertEquals(2L, sorted.get(0).getInventory().getPharmacy().getId());
    }

    @Test
    @DisplayName("TEST 4: Closed & Out of Stock pharmacies remain visible in results list")
    void testClosedAndOutRemainingVisible() {
        PharmacyDto pClosed = PharmacyDto.builder().id(1L).name("Closed Pharm").status("CLOSED").build();
        TestInventoryItem itemClosed = TestInventoryItem.builder()
                .inventory(InventoryDto.builder().id(101L).pharmacy(pClosed).availableQuantity(0).build())
                .build();

        List<TestInventoryItem> sorted = sortInventories(Collections.singletonList(itemClosed), 11.93, 79.83);
        assertEquals(1, sorted.size());
        assertEquals(1L, sorted.get(0).getInventory().getPharmacy().getId());
    }

    @Test
    @DisplayName("TEST 5: Shortest estimated driving time wins among open in-stock pharmacies")
    void testShorterTravelTimeWins() {
        PharmacyDto pFar = PharmacyDto.builder().id(1L).name("Far Pharm").status("OPEN").build();
        TestInventoryItem itemFar = TestInventoryItem.builder()
                .inventory(InventoryDto.builder().id(101L).pharmacy(pFar).availableQuantity(10).build())
                .estimatedMinutes(25)
                .roadDistanceKm(12.0)
                .build();

        PharmacyDto pNear = PharmacyDto.builder().id(2L).name("Near Pharm").status("OPEN").build();
        TestInventoryItem itemNear = TestInventoryItem.builder()
                .inventory(InventoryDto.builder().id(102L).pharmacy(pNear).availableQuantity(10).build())
                .estimatedMinutes(8)
                .roadDistanceKm(3.5)
                .build();

        List<TestInventoryItem> sorted = sortInventories(Arrays.asList(itemFar, itemNear), 11.93, 79.83);
        assertEquals(2L, sorted.get(0).getInventory().getPharmacy().getId());
    }

    @Test
    @DisplayName("TEST 6: Haversine distance fallback is used when road routing fails")
    void testHaversineFallbackWhenRoutingFails() {
        PharmacyDto pFallbackNear = PharmacyDto.builder().id(1L).name("Near Fallback").status("OPEN").build();
        TestInventoryItem itemNear = TestInventoryItem.builder()
                .inventory(InventoryDto.builder().id(101L).pharmacy(pFallbackNear).availableQuantity(10).build())
                .distanceKm(2.1)
                .build();

        PharmacyDto pFallbackFar = PharmacyDto.builder().id(2L).name("Far Fallback").status("OPEN").build();
        TestInventoryItem itemFar = TestInventoryItem.builder()
                .inventory(InventoryDto.builder().id(102L).pharmacy(pFallbackFar).availableQuantity(10).build())
                .distanceKm(8.5)
                .build();

        List<TestInventoryItem> sorted = sortInventories(Arrays.asList(itemFar, itemNear), 11.93, 79.83);
        assertEquals(1L, sorted.get(0).getInventory().getPharmacy().getId());
    }

    @Test
    @DisplayName("TEST 7: Missing location preserves deterministic ordering")
    void testNoLocationPreservesOrder() {
        PharmacyDto p1 = PharmacyDto.builder().id(1L).name("Pharm 1").status("OPEN").build();
        TestInventoryItem item1 = TestInventoryItem.builder()
                .inventory(InventoryDto.builder().id(101L).pharmacy(p1).availableQuantity(10).build())
                .build();

        PharmacyDto p2 = PharmacyDto.builder().id(2L).name("Pharm 2").status("OPEN").build();
        TestInventoryItem item2 = TestInventoryItem.builder()
                .inventory(InventoryDto.builder().id(102L).pharmacy(p2).availableQuantity(10).build())
                .build();

        List<TestInventoryItem> sorted = sortInventories(Arrays.asList(item1, item2), null, null);
        assertEquals(1L, sorted.get(0).getInventory().getPharmacy().getId());
        assertEquals(2L, sorted.get(1).getInventory().getPharmacy().getId());
    }
}


