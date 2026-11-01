package com.sentragrid.service;

import com.sentragrid.audit.service.AuditLogService;
import com.sentragrid.dashboard.service.DashboardService;
import com.sentragrid.dto.InventoryAdjustRequest;
import com.sentragrid.dto.InventoryDto;
import com.sentragrid.dto.InventoryUpdateRequest;
import com.sentragrid.entity.Inventory;
import com.sentragrid.entity.Medicine;
import com.sentragrid.entity.Pharmacy;
import com.sentragrid.entity.User;
import com.sentragrid.entity.enums.ReservationStatus;
import com.sentragrid.entity.enums.UserRole;
import com.sentragrid.exception.BadRequestException;
import com.sentragrid.manager.dto.ManagerProfileDto;
import com.sentragrid.manager.dto.ManagerRulesDto;
import com.sentragrid.manager.dto.ManagerWorkspaceDto;
import com.sentragrid.manager.entity.ManagerSettings;
import com.sentragrid.manager.repository.ManagerSettingsRepository;
import com.sentragrid.manager.repository.ManagerStaffRepository;
import com.sentragrid.manager.service.ManagerWorkspaceService;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.MedicineRepository;
import com.sentragrid.repository.PharmacyRepository;
import com.sentragrid.repository.ReservationRepository;
import com.sentragrid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PharmacyIsolationAndInventoryIntegrityTest {

    @Mock
    private PharmacyRepository pharmacyRepository;
    @Mock
    private MedicineRepository medicineRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MedicineService medicineService;
    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ManagerSettingsRepository managerSettingsRepository;
    @Mock
    private ManagerStaffRepository managerStaffRepository;
    @Mock
    private DashboardService dashboardService;
    @Mock
    private ReservationRepository reservationRepository;

    private PharmacyService pharmacyService;
    private ManagerWorkspaceService managerWorkspaceService;

    private Pharmacy pharmacy1;
    private Pharmacy pharmacy2;
    private User pharmacist1;
    private User unassignedPharmacist;
    private User manager1;
    private User manager2;
    private Medicine insulinMed;
    private Inventory testInventory;

    @BeforeEach
    void setUp() {
        pharmacyService = new PharmacyService(
                pharmacyRepository,
                medicineRepository,
                inventoryRepository,
                userRepository,
                medicineService,
                auditLogService
        );

        managerWorkspaceService = new ManagerWorkspaceService(
                managerSettingsRepository,
                managerStaffRepository,
                dashboardService,
                reservationRepository,
                userRepository,
                inventoryRepository,
                pharmacyRepository
        );

        pharmacy1 = Pharmacy.builder()
                .id(1L)
                .name("Apollo Pharmacy Branch 1")
                .address("MG Road")
                .city("Puducherry")
                .contactNumber("0413-2341001")
                .is24Seven(true)
                .build();

        pharmacy2 = Pharmacy.builder()
                .id(2L)
                .name("HealthPlus Pharmacy Branch 2")
                .address("Anna Salai")
                .city("Chennai")
                .contactNumber("044-2341002")
                .is24Seven(false)
                .build();

        pharmacist1 = User.builder()
                .id(10L)
                .username("pharmacist1")
                .role(UserRole.PHARMACIST)
                .pharmacy(pharmacy1)
                .build();

        unassignedPharmacist = User.builder()
                .id(11L)
                .username("unassigned_user")
                .role(UserRole.PHARMACIST)
                .pharmacy(null)
                .build();

        manager1 = User.builder()
                .id(20L)
                .username("manager1")
                .role(UserRole.PHARMACY_MANAGER)
                .pharmacy(pharmacy1)
                .build();

        manager2 = User.builder()
                .id(21L)
                .username("manager2")
                .role(UserRole.PHARMACY_MANAGER)
                .pharmacy(pharmacy2)
                .build();

        insulinMed = Medicine.builder()
                .id(100L)
                .name("Insulin Glargine 100IU")
                .category("CRITICAL")
                .build();

        testInventory = Inventory.builder()
                .id(500L)
                .pharmacy(pharmacy1)
                .medicine(insulinMed)
                .quantity(20)
                .reservedQuantity(8)
                .version(1L)
                .build();
    }

    // 1. A pharmacist can access their assigned pharmacy's inventory
    @Test
    @DisplayName("Pharmacist can successfully read assigned pharmacy inventory")
    void testPharmacistCanAccessOwnPharmacyInventory() {
        when(userRepository.findByUsername("pharmacist1")).thenReturn(Optional.of(pharmacist1));
        when(pharmacyRepository.existsById(1L)).thenReturn(true);
        when(inventoryRepository.findByPharmacyId(1L)).thenReturn(List.of(testInventory));
        when(medicineService.mapToInventoryDto(testInventory)).thenReturn(
                InventoryDto.builder()
                        .id(500L)
                        .totalQuantity(20)
                        .reservedQuantity(8)
                        .availableQuantity(12)
                        .build()
        );

        List<InventoryDto> result = pharmacyService.getPharmacyInventory(1L, "pharmacist1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(500L, result.get(0).getId());
        assertEquals(12, result.get(0).getAvailableQuantity());
    }

    // 2. A pharmacist cannot access or modify another pharmacy's inventory
    @Test
    @DisplayName("Pharmacist cannot read another pharmacy inventory - throws AccessDeniedException")
    void testPharmacistCannotReadAnotherPharmacyInventory() {
        when(userRepository.findByUsername("pharmacist1")).thenReturn(Optional.of(pharmacist1));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                pharmacyService.getPharmacyInventory(2L, "pharmacist1")
        );

        assertTrue(ex.getMessage().contains("authorized only for pharmacy ID 1"));
        verify(inventoryRepository, never()).findByPharmacyId(2L);
    }

    @Test
    @DisplayName("Pharmacist cannot update another pharmacy inventory - throws AccessDeniedException")
    void testPharmacistCannotUpdateAnotherPharmacyInventory() {
        when(userRepository.findByUsername("pharmacist1")).thenReturn(Optional.of(pharmacist1));

        InventoryUpdateRequest request = new InventoryUpdateRequest();
        request.setQuantity(25);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                pharmacyService.updateInventoryById(2L, 500L, request, "pharmacist1")
        );

        assertTrue(ex.getMessage().contains("authorized only for pharmacy ID 1"));
        verify(inventoryRepository, never()).save(any());
    }

    // 3. A missing pharmacy assignment does not fall back to Pharmacy 1
    @Test
    @DisplayName("Pharmacist without pharmacy assignment is denied access and does not fall back to Pharmacy 1")
    void testMissingPharmacyAssignmentDoesNotFallbackToPharmacy1() {
        when(userRepository.findByUsername("unassigned_user")).thenReturn(Optional.of(unassignedPharmacist));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                pharmacyService.getPharmacyInventory(1L, "unassigned_user")
        );

        assertTrue(ex.getMessage().contains("No pharmacy is assigned to account 'unassigned_user'"));
        verify(inventoryRepository, never()).findByPharmacyId(1L);
    }

    // 4. A stock update below the active reserved quantity is rejected
    @Test
    @DisplayName("Stock update below active reserved quantity is rejected with BadRequestException")
    void testStockUpdateBelowReservedQuantityIsRejected() {
        when(userRepository.findByUsername("pharmacist1")).thenReturn(Optional.of(pharmacist1));
        when(pharmacyRepository.existsById(1L)).thenReturn(true);
        when(inventoryRepository.findById(500L)).thenReturn(Optional.of(testInventory));

        // testInventory has quantity=20, reservedQuantity=8. Attempt update to 5 (< 8).
        InventoryUpdateRequest request = new InventoryUpdateRequest();
        request.setQuantity(5);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                pharmacyService.updateInventoryById(1L, 500L, request, "pharmacist1")
        );

        assertTrue(ex.getMessage().contains("cannot be less than currently reserved quantity (8)"));
        assertEquals(20, testInventory.getQuantity()); // Unchanged
        verify(inventoryRepository, never()).save(testInventory);
    }

    @Test
    @DisplayName("Stock adjustment resulting in quantity below reserved quantity is rejected")
    void testAdjustInventoryBelowReservedQuantityIsRejected() {
        when(userRepository.findByUsername("pharmacist1")).thenReturn(Optional.of(pharmacist1));
        when(pharmacyRepository.existsById(1L)).thenReturn(true);
        when(inventoryRepository.findById(500L)).thenReturn(Optional.of(testInventory));

        // testInventory has quantity=20, reservedQuantity=8. Delta -15 -> 5 (< 8).
        InventoryAdjustRequest request = new InventoryAdjustRequest();
        request.setDelta(-15);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                pharmacyService.adjustInventoryQuantity(1L, 500L, request, "pharmacist1")
        );

        assertTrue(ex.getMessage().contains("cannot be less than currently reserved quantity (8)"));
        assertEquals(20, testInventory.getQuantity());
        verify(inventoryRepository, never()).save(testInventory);
    }

    @Test
    @DisplayName("Negative stock update is rejected with BadRequestException")
    void testNegativeStockUpdateIsRejected() {
        when(userRepository.findByUsername("pharmacist1")).thenReturn(Optional.of(pharmacist1));

        InventoryUpdateRequest request = new InventoryUpdateRequest();
        request.setQuantity(-3);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                pharmacyService.updateInventoryById(1L, 500L, request, "pharmacist1")
        );

        assertTrue(ex.getMessage().contains("Quantity cannot be negative"));
        verify(inventoryRepository, never()).save(any());
    }

    // 5. A valid stock update that preserves reserved inventory succeeds
    @Test
    @DisplayName("Valid stock update that preserves reserved inventory succeeds")
    void testValidStockUpdatePreservingReservedInventorySucceeds() {
        when(userRepository.findByUsername("pharmacist1")).thenReturn(Optional.of(pharmacist1));
        when(pharmacyRepository.existsById(1L)).thenReturn(true);
        when(inventoryRepository.findById(500L)).thenReturn(Optional.of(testInventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(medicineService.mapToInventoryDto(any(Inventory.class))).thenAnswer(invocation -> {
            Inventory inv = invocation.getArgument(0);
            return InventoryDto.builder()
                    .id(inv.getId())
                    .totalQuantity(inv.getQuantity())
                    .reservedQuantity(inv.getReservedQuantity())
                    .availableQuantity(inv.getQuantity() - inv.getReservedQuantity())
                    .build();
        });

        // testInventory has quantity=20, reservedQuantity=8. Updating to 15 (>= 8).
        InventoryUpdateRequest request = new InventoryUpdateRequest();
        request.setQuantity(15);

        InventoryDto result = pharmacyService.updateInventoryById(1L, 500L, request, "pharmacist1");

        assertNotNull(result);
        assertEquals(15, result.getTotalQuantity());
        assertEquals(8, result.getReservedQuantity());
        assertEquals(7, result.getAvailableQuantity());
        verify(inventoryRepository).save(testInventory);
        verify(auditLogService).logAction(eq("Inventory"), eq(500L), eq("INVENTORY_UPDATED"), eq("pharmacist1"), any());
    }

    // 6. A failed stock update does not corrupt reservations or inventory
    @Test
    @DisplayName("Failed stock update leaves inventory quantity and reservedQuantity untouched")
    void testFailedStockUpdateDoesNotCorruptInventory() {
        when(userRepository.findByUsername("pharmacist1")).thenReturn(Optional.of(pharmacist1));
        when(pharmacyRepository.existsById(1L)).thenReturn(true);
        when(inventoryRepository.findById(500L)).thenReturn(Optional.of(testInventory));

        InventoryUpdateRequest invalidRequest = new InventoryUpdateRequest();
        invalidRequest.setQuantity(4); // < 8

        assertThrows(BadRequestException.class, () ->
                pharmacyService.updateInventoryById(1L, 500L, invalidRequest, "pharmacist1")
        );

        // Verify invariant quantity >= reservedQuantity >= 0 remains intact
        assertEquals(20, testInventory.getQuantity());
        assertEquals(8, testInventory.getReservedQuantity());
        assertTrue(testInventory.getQuantity() >= testInventory.getReservedQuantity());
        assertTrue(testInventory.getReservedQuantity() >= 0);
    }

    // 7. Manager settings are isolated according to the verified ownership model
    @Test
    @DisplayName("Manager settings are strictly scoped by pharmacy ID")
    void testManagerSettingsIsolatedByPharmacyId() {
        when(userRepository.findByUsername("manager1")).thenReturn(Optional.of(manager1));
        when(userRepository.findByUsername("manager2")).thenReturn(Optional.of(manager2));

        ManagerSettings settings1 = ManagerSettings.builder()
                .id(1L)
                .pharmacyName("Apollo Pharmacy Branch 1")
                .address("MG Road")
                .city("Puducherry")
                .phone("0413-2341001")
                .hours("Open 24/7")
                .coverage("Puducherry Area")
                .verificationStatus("Verified Hub")
                .holdExpiryMinutes(25)
                .lowStockThreshold(10)
                .maxEmergencyHoldQuantity(3)
                .build();

        ManagerSettings settings2 = ManagerSettings.builder()
                .id(2L)
                .pharmacyName("HealthPlus Pharmacy Branch 2")
                .address("Anna Salai")
                .city("Chennai")
                .phone("044-2341002")
                .hours("09:00 AM - 10:00 PM")
                .coverage("Chennai Area")
                .verificationStatus("Verified Hub")
                .holdExpiryMinutes(15)
                .lowStockThreshold(5)
                .maxEmergencyHoldQuantity(1)
                .build();

        when(managerSettingsRepository.findById(1L)).thenReturn(Optional.of(settings1));
        when(managerSettingsRepository.findById(2L)).thenReturn(Optional.of(settings2));
        when(managerStaffRepository.countByPharmacyId(1L)).thenReturn(1L);
        when(managerStaffRepository.countByPharmacyId(2L)).thenReturn(1L);
        when(managerStaffRepository.findByPharmacyIdOrderByUsernameAsc(1L)).thenReturn(Collections.emptyList());
        when(managerStaffRepository.findByPharmacyIdOrderByUsernameAsc(2L)).thenReturn(Collections.emptyList());
        when(inventoryRepository.findLowStockInventoryByPharmacyId(eq(1L), anyInt())).thenReturn(Collections.emptyList());
        when(inventoryRepository.findLowStockInventoryByPharmacyId(eq(2L), anyInt())).thenReturn(Collections.emptyList());
        when(reservationRepository.countByPharmacyIdAndStatus(eq(1L), any())).thenReturn(5L);
        when(reservationRepository.countByPharmacyIdAndStatus(eq(2L), any())).thenReturn(2L);

        ManagerWorkspaceDto ws1 = managerWorkspaceService.getWorkspace("manager1");
        ManagerWorkspaceDto ws2 = managerWorkspaceService.getWorkspace("manager2");

        // Validate isolation
        assertEquals("Apollo Pharmacy Branch 1", ws1.getProfile().getName());
        assertEquals(25, ws1.getRules().getHoldExpiryMinutes());
        assertEquals(10, ws1.getRules().getLowStockThreshold());

        assertEquals("HealthPlus Pharmacy Branch 2", ws2.getProfile().getName());
        assertEquals(15, ws2.getRules().getHoldExpiryMinutes());
        assertEquals(5, ws2.getRules().getLowStockThreshold());

        // Verify update isolation
        when(managerSettingsRepository.save(any(ManagerSettings.class))).thenAnswer(i -> i.getArgument(0));

        ManagerRulesDto updatedRules = ManagerRulesDto.builder()
                .holdExpiryMinutes(30)
                .lowStockThreshold(12)
                .maxEmergencyHoldQuantity(4)
                .build();

        ManagerRulesDto savedRules = managerWorkspaceService.updateRules(updatedRules, "manager1");

        assertEquals(30, savedRules.getHoldExpiryMinutes());
        assertEquals(30, settings1.getHoldExpiryMinutes());
        assertEquals(15, settings2.getHoldExpiryMinutes()); // Pharmacy 2 remains completely unchanged!
    }

    @Test
    @DisplayName("Manager workspace throws AccessDeniedException when manager has no assigned pharmacy")
    void testManagerWithoutAssignedPharmacyThrowsAccessDenied() {
        User unassignedManager = User.builder()
                .id(22L)
                .username("unassigned_mgr")
                .role(UserRole.PHARMACY_MANAGER)
                .pharmacy(null)
                .build();

        when(userRepository.findByUsername("unassigned_mgr")).thenReturn(Optional.of(unassignedManager));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                managerWorkspaceService.getWorkspace("unassigned_mgr")
        );

        assertTrue(ex.getMessage().contains("no assigned pharmacy branch"));
    }

    @Test
    @DisplayName("Patient cannot access pharmacy inventory directly - throws AccessDeniedException")
    void testPatientCannotAccessPharmacyInventory() {
        User patientUser = User.builder()
                .id(30L)
                .username("patient_user")
                .role(UserRole.PATIENT)
                .build();

        when(userRepository.findByUsername("patient_user")).thenReturn(Optional.of(patientUser));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                pharmacyService.getPharmacyInventory(1L, "patient_user")
        );

        assertTrue(ex.getMessage().contains("Role 'PATIENT' is not authorized to access internal pharmacy inventory"));
    }

    @Test
    @DisplayName("Manager staff records are strictly scoped by pharmacy ID")
    void testManagerStaffIsolatedByPharmacy() {
        when(userRepository.findByUsername("manager1")).thenReturn(Optional.of(manager1));
        when(userRepository.findByUsername("manager2")).thenReturn(Optional.of(manager2));

        com.sentragrid.manager.entity.ManagerStaff staff1 = com.sentragrid.manager.entity.ManagerStaff.builder()
                .id(101L)
                .pharmacy(pharmacy1)
                .username("pharmacist_branch1")
                .role("Lead Pharmacist")
                .shift("Morning")
                .status("Active")
                .reservationsHandled(10)
                .build();

        com.sentragrid.manager.entity.ManagerStaff staff2 = com.sentragrid.manager.entity.ManagerStaff.builder()
                .id(102L)
                .pharmacy(pharmacy2)
                .username("pharmacist_branch2")
                .role("Dispensing Pharmacist")
                .shift("Evening")
                .status("Active")
                .reservationsHandled(5)
                .build();

        when(managerStaffRepository.findByPharmacyIdOrderByUsernameAsc(1L)).thenReturn(List.of(staff1));
        when(managerStaffRepository.findByPharmacyIdOrderByUsernameAsc(2L)).thenReturn(List.of(staff2));

        var list1 = managerWorkspaceService.getStaff("manager1");
        var list2 = managerWorkspaceService.getStaff("manager2");

        assertEquals(1, list1.size());
        assertEquals("pharmacist_branch1", list1.get(0).getUsername());
        assertEquals(1L, list1.get(0).getPharmacyId());

        assertEquals(1, list2.size());
        assertEquals("pharmacist_branch2", list2.get(0).getUsername());
        assertEquals(2L, list2.get(0).getPharmacyId());
    }

    @Test
    @DisplayName("Manager cannot modify or delete staff belonging to another pharmacy")
    void testManagerCannotModifyOrDeleteOtherBranchStaff() {
        when(userRepository.findByUsername("manager1")).thenReturn(Optional.of(manager1));

        com.sentragrid.manager.entity.ManagerStaff otherBranchStaff = com.sentragrid.manager.entity.ManagerStaff.builder()
                .id(200L)
                .pharmacy(pharmacy2)
                .username("staff_branch2")
                .role("Support")
                .status("Active")
                .build();

        when(managerStaffRepository.findById(200L)).thenReturn(Optional.of(otherBranchStaff));

        // Attempt update by manager1 (Pharmacy 1) on Pharmacy 2 staff
        com.sentragrid.manager.dto.ManagerStaffDto updateRequest = com.sentragrid.manager.dto.ManagerStaffDto.builder()
                .id(200L)
                .username("staff_branch2")
                .role("Support")
                .status("Inactive")
                .build();

        AccessDeniedException updateEx = assertThrows(AccessDeniedException.class, () ->
                managerWorkspaceService.saveStaff(updateRequest, "manager1")
        );
        assertTrue(updateEx.getMessage().contains("does not belong to your pharmacy branch"));

        // Attempt delete by manager1 on Pharmacy 2 staff
        AccessDeniedException deleteEx = assertThrows(AccessDeniedException.class, () ->
                managerWorkspaceService.deleteStaff(200L, "manager1")
        );
        assertTrue(deleteEx.getMessage().contains("does not belong to your pharmacy branch"));
    }

    @Test
    @DisplayName("Staff with missing pharmacy ownership fails closed")
    void testMissingStaffOwnershipFailsClosed() {
        when(userRepository.findByUsername("manager1")).thenReturn(Optional.of(manager1));

        com.sentragrid.manager.entity.ManagerStaff orphanStaff = com.sentragrid.manager.entity.ManagerStaff.builder()
                .id(300L)
                .pharmacy(null)
                .username("orphan_staff")
                .role("Support")
                .status("Active")
                .build();

        when(managerStaffRepository.findById(300L)).thenReturn(Optional.of(orphanStaff));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () ->
                managerWorkspaceService.deleteStaff(300L, "manager1")
        );
        assertTrue(ex.getMessage().contains("does not belong to your pharmacy branch"));
    }
}
