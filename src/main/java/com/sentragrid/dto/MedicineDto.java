package com.sentragrid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicineDto {
    private Long id;
    private String name;
    private String brand;
    private String category;
    private String description;
    private String dosage;
    private boolean requiresPrescription;
}
