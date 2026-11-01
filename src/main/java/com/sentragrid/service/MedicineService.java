package com.sentragrid.service;

import com.sentragrid.common.AppConstants;
import com.sentragrid.dto.InventoryDto;
import com.sentragrid.dto.MedicineDto;
import com.sentragrid.dto.PharmacyDto;
import com.sentragrid.entity.Inventory;
import com.sentragrid.entity.Medicine;
import com.sentragrid.entity.Pharmacy;
import com.sentragrid.repository.InventoryRepository;
import com.sentragrid.repository.MedicineRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicineService {

    private final InventoryRepository inventoryRepository;
    private final MedicineRepository medicineRepository;

    @Data
    @AllArgsConstructor
    public static class OperatingHoursInfo {
        private String status; // "OPEN", "CLOSED", "UNKNOWN"
        private Boolean isOpen;
        private String operatingHours;
    }

    public static OperatingHoursInfo calculateOperatingHoursInfo(Pharmacy pharmacy, LocalTime targetTime) {
        if (pharmacy == null) {
            return new OperatingHoursInfo("UNKNOWN", null, "Hours unavailable");
        }

        if (pharmacy.is24Seven()) {
            return new OperatingHoursInfo("OPEN", true, "24x7");
        }

        LocalTime opening = pharmacy.getOpeningTime();
        LocalTime closing = pharmacy.getClosingTime();
        String tz = pharmacy.getTimezone();

        if (opening == null || closing == null || tz == null || tz.trim().isEmpty()) {
            return new OperatingHoursInfo("UNKNOWN", null, "Hours unavailable");
        }

        if (opening.equals(closing)) {
            return new OperatingHoursInfo("UNKNOWN", null, "Hours unavailable");
        }

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(tz.trim());
        } catch (Exception e) {
            return new OperatingHoursInfo("UNKNOWN", null, "Hours unavailable");
        }

        LocalTime currentTime = targetTime != null ? targetTime : LocalTime.now(zoneId);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
        String hoursStr = opening.format(formatter) + " - " + closing.format(formatter);

        boolean isOpen;
        if (opening.isBefore(closing)) {
            isOpen = !currentTime.isBefore(opening) && currentTime.isBefore(closing);
        } else {
            // Overnight shift e.g. 20:00 to 04:00
            isOpen = !currentTime.isBefore(opening) || currentTime.isBefore(closing);
        }

        return new OperatingHoursInfo(isOpen ? "OPEN" : "CLOSED", isOpen, hoursStr);
    }

    public static OperatingHoursInfo calculateOperatingHoursInfo(Pharmacy pharmacy) {
        return calculateOperatingHoursInfo(pharmacy, null);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = AppConstants.CACHE_MEDICINE_SEARCH, key = "(#name == null ? '' : #name.trim().toLowerCase()) + '-' + (#city == null ? '' : #city.trim().toLowerCase())", unless = "#result.isEmpty()")
    public List<InventoryDto> searchMedicines(String name, String city) {
        return searchMedicines(name, city, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<InventoryDto> searchMedicines(String name, String city, Double latitude, Double longitude, Double radiusKm) {
        String sanitizedName = (name != null && !name.trim().isEmpty()) ? name.trim() : null;
        String sanitizedCity = (city != null && !city.trim().isEmpty()) ? city.trim() : null;

        List<Inventory> inventories;
        if (latitude != null && longitude != null) {
            double effectiveRadius = (radiusKm != null && radiusKm > 0) ? radiusKm : AppConstants.DEFAULT_SEARCH_RADIUS_KM;
            inventories = inventoryRepository.searchAvailableInventoryWithLocation(sanitizedName, sanitizedCity, latitude, longitude, effectiveRadius);
        } else {
            inventories = inventoryRepository.searchAvailableInventory(sanitizedName, sanitizedCity);
        }

        return inventories.stream()
                .map(this::mapToInventoryDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Medicine> findCatalogMatches(String term) {
        if (term == null || term.trim().isEmpty() || medicineRepository == null) {
            return List.of();
        }
        return medicineRepository.searchByNameOrBrand(term.trim());
    }

    public InventoryDto mapToInventoryDto(Inventory inventory) {
        Pharmacy pharmacy = inventory.getPharmacy();
        OperatingHoursInfo hoursInfo = calculateOperatingHoursInfo(pharmacy);

        PharmacyDto pharmacyDto = PharmacyDto.builder()
                .id(pharmacy.getId())
                .name(pharmacy.getName())
                .address(pharmacy.getAddress())
                .city(pharmacy.getCity())
                .latitude(pharmacy.getLatitude())
                .longitude(pharmacy.getLongitude())
                .contactNumber(pharmacy.getContactNumber())
                .openingTime(pharmacy.getOpeningTime())
                .closingTime(pharmacy.getClosingTime())
                .is24Seven(pharmacy.is24Seven())
                .timezone(pharmacy.getTimezone())
                .operatingHours(hoursInfo.getOperatingHours())
                .status(hoursInfo.getStatus())
                .isOpen(hoursInfo.getIsOpen())
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
