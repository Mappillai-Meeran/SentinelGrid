package com.sentragrid.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservabilityMetricDto {
    private String name;
    private String value;
    private String detail;
    private String status;
}
