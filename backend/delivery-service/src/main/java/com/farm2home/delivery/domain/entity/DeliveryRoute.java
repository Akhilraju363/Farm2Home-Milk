package com.farm2home.delivery.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "delivery_routes", schema = "delivery")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class DeliveryRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "route_name", nullable = false, length = 100)
    private String routeName;

    @Column(name = "route_code", unique = true, nullable = false, length = 20)
    private String routeCode;

    @Column(name = "area", nullable = false, length = 100)
    private String area;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "pincode", nullable = false, length = 10)
    private String pincode;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    // Geographic routing fields - the ONLY input to automatic route selection (see
    // DeliveryRouteSelectionServiceImpl in customer-service). A route missing any of the three is
    // simply never selectable by that algorithm (still fully usable for manual assignment), which
    // is what keeps a route created before this feature existed from breaking anything.
    @Column(name = "center_latitude", precision = 10, scale = 8)
    private BigDecimal centerLatitude;

    @Column(name = "center_longitude", precision = 11, scale = 8)
    private BigDecimal centerLongitude;

    @Column(name = "radius_km", precision = 6, scale = 2)
    private BigDecimal radiusKm;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "is_deleted")
    @Builder.Default
    private boolean deleted = false;
}
