package com.sentragrid.entity;

import com.sentragrid.common.BaseEntity;
import com.sentragrid.entity.enums.ReservationStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Reservation entity representing a medicine reservation by a patient.
 * Includes @Version for optimistic concurrency control.
 */
@Entity
@Table(name = "reservations", indexes = {
        @Index(name = "idx_reservations_status_expires_at", columnList = "status, expires_at"),
        @Index(name = "idx_reservations_patient_status_created", columnList = "patient_id, status, created_at DESC"),
        @Index(name = "idx_reservations_pharmacy_status_created", columnList = "pharmacy_id, status, created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private User patient;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "medicine_id", nullable = false)
    private Medicine medicine;

    @Min(1)
    @Column(nullable = false)
    private Integer quantity;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "prescription_number", length = 100)
    private String prescriptionNumber;

    @Column(name = "doctor_name", length = 100)
    private String doctorName;

    @Column(name = "doctor_reg_number", length = 100)
    private String doctorRegNumber;

    @Builder.Default
    @Column(name = "prescription_verified", nullable = false)
    private boolean prescriptionVerified = false;

    @Column(name = "prescription_verified_by", length = 100)
    private String prescriptionVerifiedBy;

    @Column(name = "prescription_verified_at")
    private LocalDateTime prescriptionVerifiedAt;

    @Version
    private Long version;
}
