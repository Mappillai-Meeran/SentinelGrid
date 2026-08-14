package com.sentragrid.controller;

import com.sentragrid.common.ApiResponse;
import com.sentragrid.dto.InventoryDto;
import com.sentragrid.dto.InventoryUpdateDto;
import com.sentragrid.service.PharmacyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pharmacies")
@RequiredArgsConstructor
@Tag(name = "Pharmacies", description = "Pharmacy and Inventory Management API")
public class PharmacyController {

    private final PharmacyService pharmacyService;

    @GetMapping("/{id}/inventory")
    @Operation(summary = "Get inventory for a specific pharmacy")
    public ResponseEntity<ApiResponse<List<InventoryDto>>> getPharmacyInventory(@PathVariable Long id) {
        List<InventoryDto> inventory = pharmacyService.getPharmacyInventory(id);
        return ResponseEntity.ok(ApiResponse.success(inventory, "Pharmacy inventory retrieved"));
    }

    @PutMapping("/{id}/inventory")
    @PreAuthorize("hasRole('PHARMACIST') or hasRole('ADMIN')")
    @Operation(summary = "Update medicine inventory level for a pharmacy")
    public ResponseEntity<ApiResponse<InventoryDto>> updatePharmacyInventory(
            @PathVariable Long id,
            @Valid @RequestBody InventoryUpdateDto updateDto,
            Authentication authentication) {
        InventoryDto updated = pharmacyService.updatePharmacyInventory(id, updateDto, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(updated, "Inventory updated successfully"));
    }
}
