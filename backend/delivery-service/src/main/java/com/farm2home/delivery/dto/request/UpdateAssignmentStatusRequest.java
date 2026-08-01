package com.farm2home.delivery.dto.request;

import com.farm2home.delivery.domain.enums.AssignmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * {@code failureReason}/{@code deliveryProof} are deliberately NOT {@code @NotBlank} here -
 * bean validation has no clean way to make a field conditionally required based on a sibling
 * field's value, so that check lives in DeliveryAssignmentServiceImpl.updateStatus() instead
 * (failureReason is required, and rejected with a clear error, only when transitioning to
 * FAILED; deliveryProof is optional even for DELIVERED). An unconditional {@code @NotBlank}
 * here would reject every other transition (e.g. the common ASSIGNED -&gt; OUT_FOR_DELIVERY)
 * unless the caller supplied dummy values for both fields - a real bug this fixes.
 */
@Data
public class UpdateAssignmentStatusRequest {

    @NotNull(message = "Status is required")
    @Schema(description = "Target status. Must be a legal transition from the assignment's "
            + "current status per AssignmentStatus.canTransitionTo(): ASSIGNED -> OUT_FOR_DELIVERY "
            + "or FAILED; OUT_FOR_DELIVERY -> DELIVERED or FAILED; DELIVERED/FAILED are terminal "
            + "(no further transitions). Transitioning to OUT_FOR_DELIVERY additionally requires "
            + "the order to have a SUCCESS or PENDING payment in payment-service.",
            example = "OUT_FOR_DELIVERY", requiredMode = Schema.RequiredMode.REQUIRED)
    private AssignmentStatus status;

    @Schema(description = "Reason the delivery failed. Required (enforced server-side, not by "
            + "bean validation) only when status is FAILED; ignored for every other transition.",
            example = "Customer unreachable at delivery address")
    private String failureReason;

    @Schema(description = "Proof of delivery (e.g. signature reference, OTP, photo URL). "
            + "Optional even when status is DELIVERED; only persisted for that transition.",
            example = "signed-by-customer")
    private String deliveryProof;
}
