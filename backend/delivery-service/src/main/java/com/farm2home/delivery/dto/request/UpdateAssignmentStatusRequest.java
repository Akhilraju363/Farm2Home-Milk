package com.farm2home.delivery.dto.request;

import com.farm2home.delivery.domain.enums.AssignmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAssignmentStatusRequest {

    @NotNull(message = "Status is required")
    private AssignmentStatus status;

    @NotBlank(message = "Failure reason is required when status is FAILED")
    private String failureReason;

    @NotBlank(message = "Delivery proof is required when status is DELIVERED")
    private String deliveryProof;
}
