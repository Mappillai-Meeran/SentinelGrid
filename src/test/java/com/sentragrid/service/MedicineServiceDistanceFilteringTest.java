package com.sentragrid.service;

import com.sentragrid.controller.MedicineController;
import com.sentragrid.common.ApiResponse;
import com.sentragrid.dto.InventoryDto;
import com.sentragrid.dto.PharmacyDto;
import com.sentragrid.entity.Inventory;
import com.sentragrid.entity.Medicine;
import com.sentragrid.entity.Pharmacy;
import com.sentragrid.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MedicineServiceDistanceFilteringTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private MedicineService medicineService;

    private MedicineController medicineController;

    private Pharmacy puducherryPharm1;
    private Pharmacy puducherryPharm2;
    private Inventory inv1;
    private Inventory inv2;

    @BeforeEach
    void setUp() {
        medicineController = new MedicineController(medicineService);

        // Seeded coordinate Puducherry Center (11.93, 79.83)
        puducherryPharm1 = Pharmacy.builder()
                .id(1L)
                .name("Central Pharmacy")
                .city("Puducherry")
                .latitude(11.9320)
                .longitude(79.8310)
                .is24Seven(true)
                .build();

        // Pharmacy 35km away in Villupuram (11.94, 79.49)
        puducherryPharm2 = Pharmacy.builder()
                .id(2L)
                .name("Distant Pharmacy")
                .city("Puducherry")
                .latitude(11.9400)
                .longitude(79.4900)
                .is24Seven(true)
                .build();

        Medicine med = Medicine.builder()
                .id(10L)
                .name("Paracetamol 500mg")
                .brand("Generic")
                .category("Analgesic")
                .build();

        inv1 = Inventory.builder()
                .id(101L)
                .pharmacy(puducherryPharm1)
                .medicine(med)
                .quantity(10)
                .reservedQuantity(0)
                .build();

        inv2 = Inventory.builder()
                .id(102L)
                .pharmacy(puducherryPharm2)
                .medicine(med)
                .quantity(15)
                .reservedQuantity(0)
                .build();
    }

    @Test
    @DisplayName("TEST A: Existing behavior without location parameters returns all city inventory")
    void testSearchWithoutLocation() {
        when(inventoryRepository.searchAvailableInventory("Paracetamol", "Puducherry"))
                .thenReturn(Arrays.asList(inv1, inv2));

        List<InventoryDto> results = medicineService.searchMedicines("Paracetamol", "Puducherry");

        assertEquals(2, results.size());
        verify(inventoryRepository, times(1)).searchAvailableInventory("Paracetamol", "Puducherry");
        verify(inventoryRepository, never()).searchAvailableInventoryWithLocation(any(), any(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("TEST B: Valid location & radius filters distant pharmacies outside requested radius")
    void testSearchWithLocationFiltersDistant() {
        when(inventoryRepository.searchAvailableInventoryWithLocation(eq("Paracetamol"), eq("Puducherry"), eq(11.93), eq(79.83), eq(10.0)))
                .thenReturn(Collections.singletonList(inv1));

        List<InventoryDto> results = medicineService.searchMedicines("Paracetamol", "Puducherry", 11.93, 79.83, 10.0);

        assertEquals(1, results.size());
        assertEquals(1L, results.get(0).getPharmacy().getId());
        verify(inventoryRepository, times(1)).searchAvailableInventoryWithLocation("Paracetamol", "Puducherry", 11.93, 79.83, 10.0);
    }

    @Test
    @DisplayName("TEST C: Controller rejects single coordinate request with HTTP 400 Bad Request")
    void testControllerSingleCoordinateValidation() {
        ResponseEntity<ApiResponse<List<InventoryDto>>> responseLatOnly = medicineController.searchMedicines("Paracetamol", "Puducherry", 11.93, null, 25.0);
        assertEquals(HttpStatus.BAD_REQUEST, responseLatOnly.getStatusCode());
        assertFalse(responseLatOnly.getBody().isSuccess());

        ResponseEntity<ApiResponse<List<InventoryDto>>> responseLngOnly = medicineController.searchMedicines("Paracetamol", "Puducherry", null, 79.83, 25.0);
        assertEquals(HttpStatus.BAD_REQUEST, responseLngOnly.getStatusCode());
        assertFalse(responseLngOnly.getBody().isSuccess());
    }

    @Test
    @DisplayName("TEST D: Controller rejects invalid latitude/longitude ranges with HTTP 400")
    void testControllerCoordinateRangeValidation() {
        ResponseEntity<ApiResponse<List<InventoryDto>>> resInvalidLat = medicineController.searchMedicines("Paracetamol", "Puducherry", 95.0, 79.83, 25.0);
        assertEquals(HttpStatus.BAD_REQUEST, resInvalidLat.getStatusCode());

        ResponseEntity<ApiResponse<List<InventoryDto>>> resInvalidLng = medicineController.searchMedicines("Paracetamol", "Puducherry", 11.93, 190.0, 25.0);
        assertEquals(HttpStatus.BAD_REQUEST, resInvalidLng.getStatusCode());
    }

    @Test
    @DisplayName("TEST E: Controller rejects non-positive radius (radius <= 0) with HTTP 400")
    void testControllerInvalidRadiusValidation() {
        ResponseEntity<ApiResponse<List<InventoryDto>>> resZeroRadius = medicineController.searchMedicines("Paracetamol", "Puducherry", 11.93, 79.83, 0.0);
        assertEquals(HttpStatus.BAD_REQUEST, resZeroRadius.getStatusCode());

        ResponseEntity<ApiResponse<List<InventoryDto>>> resNegRadius = medicineController.searchMedicines("Paracetamol", "Puducherry", 11.93, 79.83, -5.0);
        assertEquals(HttpStatus.BAD_REQUEST, resNegRadius.getStatusCode());
    }

    @Test
    @DisplayName("TEST F: Omitted radius defaults to 25.0 km default constant")
    void testDefaultRadiusWhenOmitted() {
        when(inventoryRepository.searchAvailableInventoryWithLocation(eq("Paracetamol"), eq("Puducherry"), eq(11.93), eq(79.83), eq(25.0)))
                .thenReturn(Arrays.asList(inv1, inv2));

        medicineService.searchMedicines("Paracetamol", "Puducherry", 11.93, 79.83, null);

        verify(inventoryRepository, times(1)).searchAvailableInventoryWithLocation("Paracetamol", "Puducherry", 11.93, 79.83, 25.0);
    }
}
