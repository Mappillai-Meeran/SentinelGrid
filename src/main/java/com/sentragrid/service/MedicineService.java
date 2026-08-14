package com.sentragrid.service;

import com.sentragrid.common.AppConstants;
import com.sentragrid.dto.InventoryDto;
import com.sentragrid.dto.MedicineDto;
import com.sentragrid.dto.PharmacyDto;
import com.sentragrid.entity.Inventory;
import com.sentragrid.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicineService {

    private final InventoryRepository inventoryRepository;

    @Transactional(readOnly = true)
    @Cacheable(value = AppConstants.CACHE_MEDICINE_SEARCH, key = "#name + '-' + #city", unless = "#result.isEmpty()")
    public List<InventoryDto> searchMedicines(String name, String city) {
        List<Inventory> inventories = inventoryRepository.searchAvailableInventory(name, city);
        return inventories.stream()
                .map(this::mapToInventoryDto)
                .collect(Collectors.toList());
    }

    public InventoryDto mapToInventoryDto(Inventory inventory) {
        PharmacyDto pharmacyDto = PharmacyDto.builder()
                .id(inventory.getPharmacy().getId())
                .name(inventory.getPharmacy().getName())
                .address(inventory.getPharmacy().getAddress())
                .city(inventory.getPharmacy().getCity())
                .latitude(inventory.getPharmacy().getLatitude())
                .longitude(inventory.getPharmacy().getLongitude())
                .contactNumber(inventory.getPharmacy().getContactNumber())
                .build();

        MedicineDto medicineDto = MedicineDto.builder()
                .id(inventory.getMedicine().getId())
                .name(inventory.getMedicine().getName())
                .brand(inventory.getMedicine().getBrand())
                .category(inventory.getMedicine().getCategory())
                .description(inventory.getMedicine().getDescription())
                .dosage(inventory.getMedicine().getDosage())
                .requiresPrescription(inventory.getMedicine().isRequiresPrescription())
                .build();

        return InventoryDto.builder()
                .id(inventory.getId())
                .pharmacy(pharmacyDto)
                .medicine(medicineDto)
                .totalQuantity(inventory.getQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .availableQuantity(inventory.getAvailableQuantity())
                .state(inventory.getInventoryState())
                .version(inventory.getVersion())
                .build();
    }
}
