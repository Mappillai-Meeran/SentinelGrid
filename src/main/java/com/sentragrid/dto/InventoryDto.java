package com.sentragrid.dto;

import com.sentragrid.entity.enums.InventoryState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private PharmacyDto pharmacy;
    private MedicineDto medicine;
    private Integer totalQuantity;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private InventoryState state;
    private Long version;
}
