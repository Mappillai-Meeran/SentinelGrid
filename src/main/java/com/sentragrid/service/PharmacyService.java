package com.sentragrid.service;

import com.sentragrid.audit.service.AuditLogService;
import com.sentragrid.dto.InventoryAdjustRequest;
import com.sentragrid.dto.InventoryCreateRequest;
import com.sentragrid.dto.InventoryDto;
import com.sentragrid.dto.InventoryUpdateDto;
import com.sentragrid.dto.InventoryUpdateRequest;
import com.sentragrid.entity.Inventory;
import com.sentragrid.entity.Medicine;
import com.sentragrid.entity.Pharmacy;
import com.sentragrid.exception.BadRequestException;
import com.sentragrid.exception.ResourceNotFoundException;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.MedicineRepository;
import com.sentragrid.repository.PharmacyRepository;
import com.sentragrid.entity.User;
import com.sentragrid.entity.enums.UserRole;
import com.sentragrid.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PharmacyService {

    private final PharmacyRepository pharmacyRepository;
    private final MedicineRepository medicineRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final MedicineService medicineService;
    private final AuditLogService auditLogService;

    @org.springframework.beans.factory.annotation.Value("${app.demo.protection-enabled:false}")
    private boolean demoProtectionEnabled;

    private void verifyPharmacyScope(String username, Long targetPharmacyId) {
        if (username == null || username.isBlank()) {
            throw new AccessDeniedException("Access denied: Authentication required to access pharmacy inventory.");
        }
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedException("Access denied: User account '" + username + "' not found."));
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }
        if (user.getRole() == UserRole.PHARMACIST || user.getRole() == UserRole.PHARMACY_MANAGER) {
            if (user.getPharmacy() == null) {
                throw new AccessDeniedException("Access denied: No pharmacy is assigned to account '" + username + "'.");
            }
            if (!user.getPharmacy().getId().equals(targetPharmacyId)) {
                throw new AccessDeniedException("Access denied: You are authorized only for pharmacy ID " + user.getPharmacy().getId() + ", but attempted access to pharmacy ID " + targetPharmacyId);
            }
            return;
        }
        throw new AccessDeniedException("Access denied: Role '" + user.getRole() + "' is not authorized to access internal pharmacy inventory.");
    }

    @Transactional(readOnly = true)
    public List<InventoryDto> getPharmacyInventory(Long pharmacyId, String username) {
        verifyPharmacyScope(username, pharmacyId);
        if (!pharmacyRepository.existsById(pharmacyId)) {
            throw new ResourceNotFoundException("Pharmacy not found with ID: " + pharmacyId);
        }
        return inventoryRepository.findByPharmacyId(pharmacyId).stream()
                .map(medicineService::mapToInventoryDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InventoryDto> getPharmacyInventory(Long pharmacyId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated()) ? auth.getName() : null;
        return getPharmacyInventory(pharmacyId, username);
    }

    @Transactional
    @CacheEvict(value = "medicineSearchCache", allEntries = true)
    public InventoryDto addInventory(Long pharmacyId, InventoryCreateRequest request, String createdBy) {
        verifyPharmacyScope(createdBy, pharmacyId);
        if (request.getQuantity() == null || request.getQuantity() < 0) {
            throw new BadRequestException("Initial quantity cannot be negative");
        }
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found with ID: " + pharmacyId));

        Medicine medicine = medicineRepository.findById(request.getMedicineId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found with ID: " + request.getMedicineId()));

        inventoryRepository.findByPharmacyIdAndMedicineId(pharmacyId, request.getMedicineId())
                .ifPresent(existing -> {
                    throw new BadRequestException("Inventory record already exists for medicine ID: " + request.getMedicineId() + ". Use update or adjust instead.");
                });

        Inventory inventory = Inventory.builder()
                .pharmacy(pharmacy)
                .medicine(medicine)
                .quantity(request.getQuantity())
                .reservedQuantity(0)
                .build();

        Inventory savedInventory = inventoryRepository.save(inventory);

        auditLogService.logAction("Inventory", savedInventory.getId(), "INVENTORY_CREATED", createdBy,
                "Created inventory with quantity " + request.getQuantity() + " for medicine " + medicine.getName());

        return medicineService.mapToInventoryDto(savedInventory);
    }

    @Transactional
    @CacheEvict(value = "medicineSearchCache", allEntries = true)
    public InventoryDto updatePharmacyInventory(Long pharmacyId, InventoryUpdateDto dto, String updatedBy) {
        verifyPharmacyScope(updatedBy, pharmacyId);
        if (dto.getQuantity() == null || dto.getQuantity() < 0) {
            throw new BadRequestException("Quantity cannot be negative");
        }
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy not found with ID: " + pharmacyId));

        Medicine medicine = medicineRepository.findById(dto.getMedicineId())
                .orElseThrow(() -> new ResourceNotFoundException("Medicine not found with ID: " + dto.getMedicineId()));

        Inventory inventory = inventoryRepository.findByPharmacyIdAndMedicineId(pharmacyId, dto.getMedicineId())
                .orElseGet(() -> Inventory.builder()
                        .pharmacy(pharmacy)
                        .medicine(medicine)
                        .quantity(0)
                        .reservedQuantity(0)
                        .build());

        if (dto.getQuantity() < inventory.getReservedQuantity()) {
            throw new BadRequestException("New quantity (" + dto.getQuantity() + ") cannot be less than currently reserved quantity (" + inventory.getReservedQuantity() + ")");
        }

        inventory.setQuantity(dto.getQuantity());
        Inventory savedInventory = inventoryRepository.save(inventory);

        auditLogService.logAction("Inventory", savedInventory.getId(), "INVENTORY_UPDATED", updatedBy,
                "Updated quantity to " + dto.getQuantity() + " for medicine " + medicine.getName());

        return medicineService.mapToInventoryDto(savedInventory);
    }

    @Transactional
    @CacheEvict(value = "medicineSearchCache", allEntries = true)
    public InventoryDto updateInventoryById(Long pharmacyId, Long inventoryId, InventoryUpdateRequest request, String updatedBy) {
        verifyPharmacyScope(updatedBy, pharmacyId);
        if (request.getQuantity() == null || request.getQuantity() < 0) {
            throw new BadRequestException("Quantity cannot be negative");
        }
        if (!pharmacyRepository.existsById(pharmacyId)) {
            throw new ResourceNotFoundException("Pharmacy not found with ID: " + pharmacyId);
        }

        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory record not found with ID: " + inventoryId));

        if (!inventory.getPharmacy().getId().equals(pharmacyId)) {
            throw new BadRequestException("Inventory ID " + inventoryId + " does not belong to pharmacy ID " + pharmacyId);
        }

        if (request.getQuantity() < inventory.getReservedQuantity()) {
            throw new BadRequestException("New quantity (" + request.getQuantity() + ") cannot be less than currently reserved quantity (" + inventory.getReservedQuantity() + ")");
        }

        inventory.setQuantity(request.getQuantity());
        Inventory savedInventory = inventoryRepository.save(inventory);

        auditLogService.logAction("Inventory", savedInventory.getId(), "INVENTORY_UPDATED", updatedBy,
                "Updated inventory ID " + inventoryId + " quantity to " + request.getQuantity());

        return medicineService.mapToInventoryDto(savedInventory);
    }

    @Transactional
    @CacheEvict(value = "medicineSearchCache", allEntries = true)
    public InventoryDto adjustInventoryQuantity(Long pharmacyId, Long inventoryId, InventoryAdjustRequest request, String adjustedBy) {
        verifyPharmacyScope(adjustedBy, pharmacyId);
        if (!pharmacyRepository.existsById(pharmacyId)) {
            throw new ResourceNotFoundException("Pharmacy not found with ID: " + pharmacyId);
        }

        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory record not found with ID: " + inventoryId));

        if (!inventory.getPharmacy().getId().equals(pharmacyId)) {
            throw new BadRequestException("Inventory ID " + inventoryId + " does not belong to pharmacy ID " + pharmacyId);
        }

        int newQuantity = inventory.getQuantity() + request.getDelta();
        if (newQuantity < 0) {
            throw new BadRequestException("Resulting quantity cannot be negative. Current: " + inventory.getQuantity() + ", Delta: " + request.getDelta());
        }

        if (newQuantity < inventory.getReservedQuantity()) {
            throw new BadRequestException("Resulting quantity (" + newQuantity + ") cannot be less than currently reserved quantity (" + inventory.getReservedQuantity() + ")");
        }

        inventory.setQuantity(newQuantity);
        Inventory savedInventory = inventoryRepository.save(inventory);

        auditLogService.logAction("Inventory", savedInventory.getId(), "INVENTORY_ADJUSTED", adjustedBy,
                "Adjusted inventory ID " + inventoryId + " by delta " + request.getDelta() + ". New quantity: " + newQuantity);

        return medicineService.mapToInventoryDto(savedInventory);
    }

    @Transactional
    @CacheEvict(value = "medicineSearchCache", allEntries = true)
    public void deleteInventory(Long pharmacyId, Long inventoryId, String deletedBy) {
        if (demoProtectionEnabled) {
            throw new BadRequestException("Deleting pharmacy inventory is disabled in the public demonstration.");
        }
        verifyPharmacyScope(deletedBy, pharmacyId);
        if (!pharmacyRepository.existsById(pharmacyId)) {
            throw new ResourceNotFoundException("Pharmacy not found with ID: " + pharmacyId);
        }

        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory record not found with ID: " + inventoryId));

        if (!inventory.getPharmacy().getId().equals(pharmacyId)) {
            throw new BadRequestException("Inventory ID " + inventoryId + " does not belong to pharmacy ID " + pharmacyId);
        }

        if (inventory.getReservedQuantity() > 0) {
            throw new BadRequestException("Cannot delete inventory record with active reserved stock (" + inventory.getReservedQuantity() + " units reserved)");
        }

        inventoryRepository.delete(inventory);

        auditLogService.logAction("Inventory", inventoryId, "INVENTORY_DELETED", deletedBy,
                "Deleted inventory ID " + inventoryId + " for medicine " + inventory.getMedicine().getName());
    }
}
