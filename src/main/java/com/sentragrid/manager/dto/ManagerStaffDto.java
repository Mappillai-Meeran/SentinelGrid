package com.sentragrid.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerStaffDto {
    private Long id;
    private Long pharmacyId;
    private String username;
    private String role;
    private String shift;
    private String status;
    private Integer reservationsHandled;
}
