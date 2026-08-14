package com.sentragrid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PharmacyDto {
    private Long id;
    private String name;
    private String address;
    private String city;
    private Double latitude;
    private Double longitude;
    private String contactNumber;
}
