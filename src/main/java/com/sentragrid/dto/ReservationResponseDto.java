package com.sentragrid.dto;

import com.sentragrid.entity.enums.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponseDto {
    private Long id;
    private Long patientId;
    private String patientUsername;
    private PharmacyDto pharmacy;
    private MedicineDto medicine;
    private Integer quantity;
    private ReservationStatus status;
    private LocalDateTime expiresAt;
    private Long remainingSeconds;
    private Long remainingMinutes;
    private Boolean expired;
    private LocalDateTime confirmedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime createdAt;
    private Long version;
}
