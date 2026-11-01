package com.sentragrid.controller;

import com.sentragrid.common.ApiResponse;
import com.sentragrid.dto.RoutingEstimateRequest;
import com.sentragrid.dto.RoutingEstimateResponse;
import com.sentragrid.service.RoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routing")
@RequiredArgsConstructor
@Tag(name = "Routing", description = "Road Routing and ETA Estimation API")
public class RoutingController {

    private final RoutingService routingService;

    @PostMapping("/estimate")
    @Operation(summary = "Calculate driving road distance and travel time estimates to pharmacies")
    public ResponseEntity<ApiResponse<RoutingEstimateResponse>> estimateRoutes(
            @Valid @RequestBody RoutingEstimateRequest request) {
        RoutingEstimateResponse response = routingService.estimateRoutes(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Routing estimates processed successfully"));
    }
}
