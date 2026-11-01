package com.sentragrid.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservabilityDto {
    private String healthStatus;
    private List<ObservabilityMetricDto> metrics;
}
