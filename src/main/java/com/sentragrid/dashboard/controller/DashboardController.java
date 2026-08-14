package com.sentragrid.dashboard.controller;

import com.sentragrid.common.ApiResponse;
import com.sentragrid.dashboard.dto.DailyReservationDto;
import com.sentragrid.dashboard.dto.DashboardSummaryDto;
import com.sentragrid.dashboard.dto.LowStockInventoryDto;
import com.sentragrid.dashboard.dto.TopMedicineDto;
import com.sentragrid.dashboard.dto.TopPharmacyDto;
import com.sentragrid.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Dashboard", description = "Analytics and Dashboard APIs for Admins")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    @Operation(summary = "Get high-level system summary metrics")
    public ResponseEntity<ApiResponse<DashboardSummaryDto>> getSummaryMetrics() {
        DashboardSummaryDto summary = dashboardService.getSummaryMetrics();
        return ResponseEntity.ok(ApiResponse.success(summary, "System summary metrics retrieved"));
    }

    @GetMapping("/top-medicines")
    @Operation(summary = "Get top reserved medicines ordered by count")
    public ResponseEntity<ApiResponse<List<TopMedicineDto>>> getTopMedicines(
            @RequestParam(defaultValue = "5") int limit) {
        List<TopMedicineDto> topMedicines = dashboardService.getTopReservedMedicines(limit);
        return ResponseEntity.ok(ApiResponse.success(topMedicines, "Top reserved medicines retrieved"));
    }

    @GetMapping("/reservations/daily")
    @Operation(summary = "Get daily reservation count trend for the last N days")
    public ResponseEntity<ApiResponse<List<DailyReservationDto>>> getDailyReservationTrend(
            @RequestParam(defaultValue = "7") int days) {
        List<DailyReservationDto> trend = dashboardService.getDailyReservationTrend(days);
        return ResponseEntity.ok(ApiResponse.success(trend, "Daily reservation trend retrieved"));
    }

    @GetMapping("/inventory/low-stock")
    @Operation(summary = "Get low stock inventory items where available quantity is below threshold")
    public ResponseEntity<ApiResponse<List<LowStockInventoryDto>>> getLowStockInventory(
            @RequestParam(defaultValue = "10") int threshold) {
        List<LowStockInventoryDto> lowStock = dashboardService.getLowStockInventory(threshold);
        return ResponseEntity.ok(ApiResponse.success(lowStock, "Low stock inventory retrieved"));
    }

    @GetMapping("/pharmacies/top")
    @Operation(summary = "Get top performing pharmacies by reservation count")
    public ResponseEntity<ApiResponse<List<TopPharmacyDto>>> getTopPharmacies(
            @RequestParam(defaultValue = "5") int limit) {
        List<TopPharmacyDto> topPharmacies = dashboardService.getTopPharmacies(limit);
        return ResponseEntity.ok(ApiResponse.success(topPharmacies, "Top pharmacies retrieved"));
    }
}
