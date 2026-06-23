package com.farm2home.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Create a manual one-time order")
public class CreateOrderRequest {

    @Schema(description = "Target customer UUID. Required for FARM_MANAGER/SUPER_ADMIN; ignored for CUSTOMER role.")
    private UUID customerId;

    @NotNull(message = "Order date is required")
    @FutureOrPresent(message = "Order date must be today or a future date")
    @Schema(example = "2026-06-25")
    private LocalDate orderDate;

    @Schema(example = "Please leave at the door")
    private String notes;

    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<CreateOrderItemRequest> items;
}
