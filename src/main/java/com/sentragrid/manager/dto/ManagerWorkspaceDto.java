package com.sentragrid.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerWorkspaceDto {
    private ManagerProfileDto profile;
    private ManagerRulesDto rules;
    private List<ManagerStaffDto> staff;
    private ManagerReportDto reports;
}
