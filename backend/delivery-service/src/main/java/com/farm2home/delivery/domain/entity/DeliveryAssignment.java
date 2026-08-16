package com.farm2home.delivery.domain.entity;

import com.farm2home.delivery.domain.enums.AssignmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "delivery_assignments", schema = "delivery")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class DeliveryAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    // Sourced from OrderEvent when this assignment is auto-created by OrderEventConsumer; null
    // for manually-assigned deliveries (ManualAssignRequest carries no customer/order-number
    // info), which means those don't trigger customer notifications - see DeliveryEventProducer.
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "order_number", length = 50)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "delivery_partner_id", nullable = false)
    private DeliveryPartner deliveryPartner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "route_id", nullable = false)
    private DeliveryRoute route;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private AssignmentStatus status = AssignmentStatus.ASSIGNED;

    // true only for OrderEventConsumer's automatic assignment; manualAssign() always leaves this
    // false (the default) - see PartnerSelectionServiceImpl for the one authoritative selection
    // algorithm both paths' route-matching/eligibility rules derive from conceptually, even though
    // only the automatic path actually calls it (manualAssign lets an admin pick any partner).
    @Column(name = "auto_assigned", nullable = false)
    @Builder.Default
    private boolean autoAssigned = false;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "delivery_proof", length = 500)
    private String deliveryProof;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        assignedAt = createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
