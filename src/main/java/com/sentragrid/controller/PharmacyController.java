package com.sentragrid.controller;

import com.sentragrid.common.ApiResponse;
import com.sentragrid.dto.InventoryAdjustRequest;
import com.sentragrid.dto.InventoryCreateRequest;
import com.sentragrid.dto.InventoryDto;
import com.sentragrid.dto.InventoryUpdateDto;
import com.sentragrid.dto.InventoryUpdateRequest;
import com.sentragrid.service.PharmacyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

    @PostMapping("/{id}/inventory")
    @PreAuthorize("hasRole('PHARMACIST') or hasRole('ADMIN')")
    @Operation(summary = "Add a new medicine stock record to pharmacy inventory")
    public ResponseEntity<ApiResponse<InventoryDto>> addPharmacyInventory(
            @PathVariable Long id,
            @Valid @RequestBody InventoryCreateRequest request,
            Authentication authentication) {
        InventoryDto created = pharmacyService.addInventory(id, request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Inventory stock record created successfully"));
    }

    @PutMapping("/{id}/inventory")
    @PreAuthorize("hasRole('PHARMACIST') or hasRole('ADMIN')")
    @Operation(summary = "Upsert medicine inventory level for a pharmacy by medicineId")
    public ResponseEntity<ApiResponse<InventoryDto>> updatePharmacyInventory(
            @PathVariable Long id,
            @Valid @RequestBody InventoryUpdateDto updateDto,
            Authentication authentication) {
        InventoryDto updated = pharmacyService.updatePharmacyInventory(id, updateDto, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(updated, "Inventory updated successfully"));
    }

    @PutMapping("/{id}/inventory/{inventoryId}")
    @PreAuthorize("hasRole('PHARMACIST') or hasRole('ADMIN')")
    @Operation(summary = "Update absolute stock quantity for a specific inventory record ID")
    public ResponseEntity<ApiResponse<InventoryDto>> updateInventoryById(
            @PathVariable Long id,
            @PathVariable Long inventoryId,
            @Valid @RequestBody InventoryUpdateRequest request,
            Authentication authentication) {
        InventoryDto updated = pharmacyService.updateInventoryById(id, inventoryId, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(updated, "Inventory quantity updated successfully"));
    }

    @PatchMapping("/{id}/inventory/{inventoryId}/adjust")
    @PreAuthorize("hasRole('PHARMACIST') or hasRole('ADMIN')")
    @Operation(summary = "Adjust inventory stock quantity by delta (positive or negative)")
    public ResponseEntity<ApiResponse<InventoryDto>> adjustInventoryQuantity(
            @PathVariable Long id,
            @PathVariable Long inventoryId,
            @Valid @RequestBody InventoryAdjustRequest request,
            Authentication authentication) {
        InventoryDto adjusted = pharmacyService.adjustInventoryQuantity(id, inventoryId, request, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(adjusted, "Inventory stock quantity adjusted successfully"));
    }

    @DeleteMapping("/{id}/inventory/{inventoryId}")
    @PreAuthorize("hasRole('PHARMACIST') or hasRole('ADMIN')")
    @Operation(summary = "Delete an inventory record for a pharmacy")
    public ResponseEntity<ApiResponse<Void>> deleteInventory(
            @PathVariable Long id,
            @PathVariable Long inventoryId,
            Authentication authentication) {
        pharmacyService.deleteInventory(id, inventoryId, authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(null, "Inventory record deleted successfully"));
    }
}
