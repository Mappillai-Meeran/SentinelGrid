package com.sentragrid.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerRulesDto {
    private Integer holdExpiryMinutes;
    private Integer lowStockThreshold;
    private Integer maxEmergencyHoldQuantity;
}
