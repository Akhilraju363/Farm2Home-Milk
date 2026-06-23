package com.farm2home.order.dto.request;

import com.farm2home.order.domain.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Update order status")
public class UpdateOrderStatusRequest {

    @NotNull(message = "Status is required")
    @Schema(description = "New status. Valid transitions: PENDING→ASSIGNED, ASSIGNED→OUT_FOR_DELIVERY, "
                        + "OUT_FOR_DELIVERY→DELIVERED. Any non-terminal status can be CANCELLED.",
            example = "ASSIGNED")
    private OrderStatus status;

    @Schema(example = "Assigned to partner #12")
    private String notes;
}
