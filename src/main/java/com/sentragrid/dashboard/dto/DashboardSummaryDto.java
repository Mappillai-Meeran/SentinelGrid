package com.sentragrid.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDto {
    private long totalUsers;
    private long totalPharmacies;
    private long totalMedicines;
    private long totalReservations;
    private long pendingReservations;
    private long confirmedReservations;
    private long expiredReservations;
    private long cancelledReservations;
    private long totalInventoryUnits;
}
