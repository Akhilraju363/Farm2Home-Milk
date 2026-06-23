package com.farm2home.delivery.dto.request;

import com.farm2home.delivery.domain.enums.AssignmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAssignmentStatusRequest {

    @NotNull(message = "Status is required")
    private AssignmentStatus status;

    private String failureReason;   // required when status = FAILED
    private String deliveryProof;   // optional image URL when status = DELIVERED
}
