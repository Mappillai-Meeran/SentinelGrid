package com.sentragrid.manager.entity;

import com.sentragrid.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "manager_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerSettings extends BaseEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private String pharmacyName;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String hours;

    @Column(nullable = false, length = 500)
    private String coverage;

    @Column(nullable = false)
    private String verificationStatus;

    @Column(nullable = false)
    private Integer holdExpiryMinutes;

    @Column(nullable = false)
    private Integer lowStockThreshold;

    @Column(nullable = false)
    private Integer maxEmergencyHoldQuantity;
}
