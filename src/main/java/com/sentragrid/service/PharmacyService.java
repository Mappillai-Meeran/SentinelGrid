package com.sentragrid.service;

import com.sentragrid.audit.service.AuditLogService;
import com.sentragrid.dto.InventoryDto;
import com.sentragrid.dto.InventoryUpdateDto;
import com.sentragrid.entity.Inventory;
import com.sentragrid.entity.Medicine;
import com.sentragrid.entity.Pharmacy;
import com.sentragrid.exception.ResourceNotFoundException;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.MedicineRepository;
import com.sentragrid.repository.PharmacyRepository;
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
    private final MedicineService medicineService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<InventoryDto> getPharmacyInventory(Long pharmacyId) {
        if (!pharmacyRepository.existsById(pharmacyId)) {
            throw new ResourceNotFoundException("Pharmacy not found with ID: " + pharmacyId);
        }
        return inventoryRepository.findByPharmacyId(pharmacyId).stream()
                .map(medicineService::mapToInventoryDto)
                .collect(Collectors.toList());
    }

    @Transactional
    @CacheEvict(value = "medicineSearchCache", allEntries = true)
    public InventoryDto updatePharmacyInventory(Long pharmacyId, InventoryUpdateDto dto, String updatedBy) {
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

        inventory.setQuantity(dto.getQuantity());
        Inventory savedInventory = inventoryRepository.save(inventory);

        auditLogService.logAction("Inventory", savedInventory.getId(), "INVENTORY_UPDATED", updatedBy,
                "Updated quantity to " + dto.getQuantity() + " for medicine " + medicine.getName());

        return medicineService.mapToInventoryDto(savedInventory);
    }
}
