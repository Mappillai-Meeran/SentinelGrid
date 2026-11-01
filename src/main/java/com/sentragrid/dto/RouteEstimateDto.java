package com.sentragrid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RouteEstimateDto {
    private Long pharmacyId;
    private Double distanceKm;
    private Integer durationMinutes;
    private String status; // "SUCCESS" or "UNAVAILABLE"
}
