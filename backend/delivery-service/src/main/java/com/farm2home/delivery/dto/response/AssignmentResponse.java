package com.farm2home.delivery.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AssignmentResponse {
    private UUID id;
    private UUID orderId;
    private UUID deliveryPartnerId;
    private String deliveryPartnerName;
    private String deliveryPartnerMobile;
    private UUID routeId;
    private String routeCode;
    private String status;
    private LocalDateTime assignedAt;
    private LocalDateTime deliveredAt;
    private String failureReason;
    private String deliveryProof;
    private LocalDateTime createdAt;
}
