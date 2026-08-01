package com.farm2home.delivery.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DelayAssignmentRequest {

    @NotBlank(message = "A reason is required to mark a delivery as delayed")
    @Schema(description = "Human-readable reason shown to the customer in the delay notification. "
            + "Notification-only - DeliveryAssignmentServiceImpl.markDelayed() does not change the "
            + "assignment's persisted status or any other field.",
            example = "Heavy traffic on the delivery route", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
