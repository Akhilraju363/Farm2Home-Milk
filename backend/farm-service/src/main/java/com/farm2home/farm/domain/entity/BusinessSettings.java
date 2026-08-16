package com.farm2home.farm.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Singleton (always id=1, enforced by the DB's singleton_guard CHECK constraint - see
 * V6__create_business_settings.sql) holding the Farm2Home business's own delivery origin.
 * Deliberately separate from Farm, which is the multi-row registered supplier-farm registry,
 * not the company's own delivery hub - see the delivery-radius feature's audit notes.
 */
@Entity
@Table(name = "business_settings", schema = "farm")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BusinessSettings {

    @Id
    private Short id;

    @Column(name = "farm_latitude", nullable = false, precision = 10, scale = 8)
    private BigDecimal farmLatitude;

    @Column(name = "farm_longitude", nullable = false, precision = 11, scale = 8)
    private BigDecimal farmLongitude;

    @Column(name = "delivery_radius_km", nullable = false, precision = 6, scale = 2)
    private BigDecimal deliveryRadiusKm;

    // Farm2Home's real business address - display-only (not part of the delivery-eligibility
    // calculation, which uses farmLatitude/farmLongitude exclusively - see GeoDistanceUtil). The
    // single authoritative source for this text; nothing else in this codebase should store its
    // own copy of it.
    @Column(name = "business_name", length = 150)
    private String businessName;

    @Column(name = "address_line", length = 255)
    private String addressLine;

    @Column(name = "locality", length = 150)
    private String locality;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "state", length = 100)
    private String state;

    @Column(name = "pincode", length = 10)
    private String pincode;

    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    @LastModifiedBy
    private String updatedBy;
}
