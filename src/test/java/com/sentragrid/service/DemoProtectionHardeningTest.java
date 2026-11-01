package com.sentragrid.service;

import com.sentragrid.audit.service.AuditLogService;
import com.sentragrid.entity.Pharmacy;
import com.sentragrid.entity.User;
import com.sentragrid.entity.enums.UserRole;
import com.sentragrid.exception.BadRequestException;
import com.sentragrid.manager.entity.ManagerStaff;
import com.sentragrid.manager.repository.ManagerStaffRepository;
import com.sentragrid.manager.service.ManagerWorkspaceService;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.MedicineRepository;
import com.sentragrid.repository.PharmacyRepository;
import com.sentragrid.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phase 5C - Demo Protection Hardening: Destructive Deletions Blocked")
class DemoProtectionHardeningTest {

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
    private ManagerStaffRepository staffRepository;

    @InjectMocks
    private PharmacyService pharmacyService;

    @InjectMocks
    private ManagerWorkspaceService managerWorkspaceService;

    @Test
    @DisplayName("Public Demo Protection: Deleting pharmacy inventory is strictly blocked when demo protection is enabled")
    void testDeleteInventoryBlockedInDemoMode() {
        ReflectionTestUtils.setField(pharmacyService, "demoProtectionEnabled", true);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> pharmacyService.deleteInventory(1L, 100L, "admin1"));

        assertEquals("Deleting pharmacy inventory is disabled in the public demonstration.", ex.getMessage());
        verify(inventoryRepository, never()).delete(any());
        verify(auditLogService, never()).logAction(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Public Demo Protection: Removing pharmacy staff is strictly blocked when demo protection is enabled")
    void testDeleteStaffBlockedInDemoMode() {
        ReflectionTestUtils.setField(managerWorkspaceService, "demoProtectionEnabled", true);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> managerWorkspaceService.deleteStaff(50L, "manager1"));

        assertEquals("Removing pharmacy staff is disabled in the public demonstration.", ex.getMessage());
        verify(staffRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Normal Development Mode: Deleting inventory proceeds when demo protection is disabled")
    void testDeleteInventoryAllowedWhenProtectionDisabled() {
        ReflectionTestUtils.setField(pharmacyService, "demoProtectionEnabled", false);

        User adminUser = User.builder().id(3L).username("admin1").role(UserRole.ADMIN).build();
        when(userRepository.findByUsername("admin1")).thenReturn(Optional.of(adminUser));
        when(pharmacyRepository.existsById(1L)).thenReturn(true);

        com.sentragrid.entity.Medicine med = com.sentragrid.entity.Medicine.builder().id(1L).name("TestMed").build();
        Pharmacy pharmacy = Pharmacy.builder().id(1L).name("Branch 1").build();
        com.sentragrid.entity.Inventory inv = com.sentragrid.entity.Inventory.builder()
                .id(100L).pharmacy(pharmacy).medicine(med).quantity(10).reservedQuantity(0).build();

        when(inventoryRepository.findById(100L)).thenReturn(Optional.of(inv));

        assertDoesNotThrow(() -> pharmacyService.deleteInventory(1L, 100L, "admin1"));
        verify(inventoryRepository, times(1)).delete(inv);
        verify(auditLogService, times(1)).logAction(eq("Inventory"), eq(100L), eq("INVENTORY_DELETED"), eq("admin1"), anyString());
    }

    @Test
    @DisplayName("Normal Development Mode: Removing staff proceeds when demo protection is disabled")
    void testDeleteStaffAllowedWhenProtectionDisabled() {
        ReflectionTestUtils.setField(managerWorkspaceService, "demoProtectionEnabled", false);

        Pharmacy pharmacy = Pharmacy.builder().id(1L).name("Branch 1").build();
        User managerUser = User.builder().id(2L).username("manager1").role(UserRole.PHARMACY_MANAGER).pharmacy(pharmacy).build();
        when(userRepository.findByUsername("manager1")).thenReturn(Optional.of(managerUser));

        ManagerStaff staff = ManagerStaff.builder()
                .id(50L)
                .pharmacy(pharmacy)
                .username("staff1")
                .role("PHARMACIST")
                .shift("Morning")
                .status("Active")
                .reservationsHandled(0)
                .build();
        when(staffRepository.findById(50L)).thenReturn(Optional.of(staff));

        assertDoesNotThrow(() -> managerWorkspaceService.deleteStaff(50L, "manager1"));
        verify(staffRepository, times(1)).delete(staff);
    }
}

