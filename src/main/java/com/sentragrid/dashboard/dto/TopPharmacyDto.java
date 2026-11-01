package com.sentragrid.dashboard.dto;

public record TopPharmacyDto(
    String pharmacyName,
    Long reservationCount,
    Long completedCount,
    Long concludedCount,
    Double fulfillmentRate
) {
    public TopPharmacyDto(String pharmacyName, Long reservationCount) {
        this(pharmacyName, reservationCount, null, null, null);
    }
}
