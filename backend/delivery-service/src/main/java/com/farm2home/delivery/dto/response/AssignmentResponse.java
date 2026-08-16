package com.farm2home.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssignmentResponse {

    @Schema(description = "Assignment id.", example = "c1a2b3d4-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
    private UUID id;

    @Schema(description = "Order this assignment delivers.", example = "b3f1a2e4-5c6d-4e7f-8a9b-0c1d2e3f4a5b")
    private UUID orderId;

    @Schema(description = "Delivery partner's own id (DeliveryPartner.id) - not the partner's "
            + "auth-service user id.", example = "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b")
    private UUID deliveryPartnerId;

    @Schema(description = "Delivery partner's display name, denormalized at read time.", example = "Ramesh Kumar")
    private String deliveryPartnerName;

    @Schema(description = "Delivery partner's contact mobile number, denormalized at read time.", example = "9876543210")
    private String deliveryPartnerMobile;

    @Schema(description = "Delivery route id.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID routeId;

    @Schema(description = "Delivery route's short code, denormalized at read time.", example = "NZ-01")
    private String routeCode;

    @Schema(description = "Delivery route's display name, denormalized at read time.", example = "North Zone Route 1")
    private String routeName;

    @Schema(description = "Current status: ASSIGNED, OUT_FOR_DELIVERY, DELIVERED, or FAILED. "
            + "See AssignmentStatus.canTransitionTo() for legal transitions.", example = "OUT_FOR_DELIVERY")
    private String status;

    @Schema(description = "true if OrderEventConsumer created this automatically (least-loaded "
            + "eligible partner on the order's own route); false if an admin created it via "
            + "manualAssign (including an explicit route override).", example = "true")
    private boolean autoAssigned;

    @Schema(description = "The assigned partner's current count of non-terminal "
            + "(ASSIGNED/OUT_FOR_DELIVERY) assignments, including this one - live, not a snapshot "
            + "from assignment time.", example = "2")
    private long partnerActiveDeliveries;

    @Schema(description = "When the order was assigned to the delivery partner.", example = "2026-07-30T09:15:00")
    private LocalDateTime assignedAt;

    @Schema(description = "When status transitioned to DELIVERED. Null until then.", example = "2026-07-31T14:20:00")
    private LocalDateTime deliveredAt;

    @Schema(description = "Reason set when status transitions to FAILED. Null otherwise.",
            example = "Customer unreachable at delivery address")
    private String failureReason;

    @Schema(description = "Proof of delivery captured when status transitions to DELIVERED. Null "
            + "otherwise.", example = "signed-by-customer")
    private String deliveryProof;

    @Schema(description = "When the assignment record was created.", example = "2026-07-30T09:15:00")
    private LocalDateTime createdAt;
}
