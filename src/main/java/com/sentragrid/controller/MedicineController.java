package com.sentragrid.controller;

import com.sentragrid.common.ApiResponse;
import com.sentragrid.dto.InventoryDto;
import com.sentragrid.service.MedicineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/medicines")
@RequiredArgsConstructor
@Tag(name = "Medicines", description = "Medicine Search and Availability API")
public class MedicineController {

    private final MedicineService medicineService;

    @GetMapping("/search")
    @Operation(summary = "Search available medicines by name, city, and optional location radius")
    public ResponseEntity<ApiResponse<List<InventoryDto>>> searchMedicines(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radiusKm) {

        if ((latitude != null && longitude == null) || (latitude == null && longitude != null)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Both latitude and longitude must be provided for location filtering."));
        }

        if (latitude != null) {
            if (latitude < -90.0 || latitude > 90.0) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Latitude must be between -90 and 90 degrees."));
            }
            if (longitude < -180.0 || longitude > 180.0) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Longitude must be between -180 and 180 degrees."));
            }
        }

        if (radiusKm != null && radiusKm <= 0) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Search radius must be greater than 0 km."));
        }

        String sanitizedName = (name != null && !name.trim().isEmpty()) ? name.trim() : null;
        String sanitizedCity = (city != null && !city.trim().isEmpty()) ? city.trim() : null;

        List<InventoryDto> results = medicineService.searchMedicines(sanitizedName, sanitizedCity, latitude, longitude, radiusKm);
        return ResponseEntity.ok(ApiResponse.success(results, "Found " + results.size() + " available medicine inventory records"));
    }
}
