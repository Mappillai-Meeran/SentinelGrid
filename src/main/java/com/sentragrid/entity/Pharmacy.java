package com.sentragrid.entity;

import com.sentragrid.common.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "pharmacies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pharmacy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Column(nullable = false)
    private String address;

    @NotBlank
    @Column(nullable = false)
    private String city;

    private Double latitude;

    private Double longitude;

    @NotBlank
    @Column(name = "contact_number", nullable = false)
    private String contactNumber;

    @Column(name = "opening_time")
    private java.time.LocalTime openingTime;

    @Column(name = "closing_time")
    private java.time.LocalTime closingTime;

    @Column(name = "is_24_seven", nullable = false)
    @Builder.Default
    private boolean is24Seven = false;

    @Column(name = "timezone")
    @Builder.Default
    private String timezone = "Asia/Kolkata";
}
