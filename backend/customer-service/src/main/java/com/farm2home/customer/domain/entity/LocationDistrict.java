package com.farm2home.customer.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "location_districts", schema = "customer")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class LocationDistrict {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "state_id", nullable = false)
    private UUID stateId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
