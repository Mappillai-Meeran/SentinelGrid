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
    @Operation(summary = "Search available medicines by name and city")
    public ResponseEntity<ApiResponse<List<InventoryDto>>> searchMedicines(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String city) {
        List<InventoryDto> results = medicineService.searchMedicines(name, city);
        return ResponseEntity.ok(ApiResponse.success(results, "Found " + results.size() + " available medicine inventory records"));
    }
}
