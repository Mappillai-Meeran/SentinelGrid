package com.sentragrid.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerProfileDto {
    private String name;
    private String address;
    private String city;
    private String phone;
    private String hours;
    private String coverage;
    private String verificationStatus;
}
