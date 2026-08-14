package com.sentragrid.entity;

import com.sentragrid.common.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "medicines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Medicine extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    private String brand;

    @NotBlank
    @Column(nullable = false)
    private String category;

    @Column(length = 1000)
    private String description;

    private String dosage;

    @Column(name = "requires_prescription", nullable = false)
    private boolean requiresPrescription = false;
}
