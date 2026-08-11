package com.farm2home.delivery.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One GPS reading submitted by a delivery partner for an active assignment. Append-only history -
 * "current location" is simply the most recent row for an assignment (see
 * DeliveryLocationRepository#findTopByDeliveryAssignmentIdOrderByRecordedAtDesc), not a separate
 * synced table, since query volume per assignment is small enough that an indexed
 * ORDER BY recorded_at DESC LIMIT 1 is cheap.
 *
 * recordedAt is assigned by the server at persistence time (see DeliveryLocationServiceImpl), not
 * taken from any client-supplied timestamp - this is what gives every row a reliable, monotonic
 * ordering per assignment without needing separate out-of-order/duplicate-timestamp conflict
 * resolution logic (see Invoice/Notification-era precedent in this project of preferring
 * server-side authority over trusting client-supplied values for anything security/ordering
 * sensitive).
 */
@Entity
@Table(name = "delivery_locations", schema = "delivery")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class DeliveryLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "delivery_assignment_id", nullable = false)
    private UUID deliveryAssignmentId;

    @Column(name = "delivery_partner_id", nullable = false)
    private UUID deliveryPartnerId;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    /** Reported GPS accuracy radius in meters, if the device provided one. */
    @Column(name = "accuracy")
    private Double accuracy;

    /** Reported speed in meters/second, if the device provided one. */
    @Column(name = "speed")
    private Double speed;

    /** Reported heading in degrees (0-360, 0 = true north), if the device provided one. */
    @Column(name = "heading")
    private Double heading;

    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;
}
