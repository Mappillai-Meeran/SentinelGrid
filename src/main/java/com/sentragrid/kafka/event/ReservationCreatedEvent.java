package com.sentragrid.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationCreatedEvent {
    private Long reservationId;
    private Long patientId;
    private Long pharmacyId;
    private Long medicineId;
    private Integer quantity;
    private LocalDateTime expiresAt;
    private LocalDateTime timestamp;
}
