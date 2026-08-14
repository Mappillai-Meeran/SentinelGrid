package com.sentragrid.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowStockInventoryDto {
    private Long inventoryId;
    private String pharmacyName;
    private String medicineName;
    private Integer quantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
}
