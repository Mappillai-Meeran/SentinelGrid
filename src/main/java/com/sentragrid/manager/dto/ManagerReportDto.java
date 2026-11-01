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
public class ManagerReportDto {
    private List<StockRiskDto> stockRisks;
    private List<ReservationOutcomeDto> reservationOutcomes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StockRiskDto {
        private String medicine;
        private Integer available;
        private Integer threshold;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReservationOutcomeDto {
        private String name;
        private Integer value;
        private String color;
    }
}
