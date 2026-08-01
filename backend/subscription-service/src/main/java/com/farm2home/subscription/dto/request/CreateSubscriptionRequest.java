package com.farm2home.subscription.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import com.farm2home.subscription.domain.enums.DeliveryDay;
import com.farm2home.subscription.domain.enums.MilkType;
import com.farm2home.subscription.domain.enums.ScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@Schema(description = "Request payload for creating a new subscription")
public class CreateSubscriptionRequest {

    @Schema(description = "Target customer UUID. Required for FARM_MANAGER/SUPER_ADMIN; ignored for CUSTOMER role.")
    private UUID customerId;

    @NotNull(message = "Milk type is required")
    @Schema(description = "Type of milk", example = "FULL_CREAM")
    private MilkType milkType;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = ValidationConstants.MIN_SUBSCRIPTION_QUANTITY, message = "Minimum quantity is 0.5 litres")
    @DecimalMax(value = "10.0", message = "Maximum quantity is 10 litres")
    @Schema(description = "Daily quantity in litres", example = "1.5")
    private BigDecimal quantity;

    @NotNull(message = "Schedule type is required")
    @Schema(description = "Delivery schedule", example = "DAILY")
    private ScheduleType scheduleType;

    @Schema(description = "Required when scheduleType is WEEKLY. E.g. [MON, WED, FRI]")
    private List<DeliveryDay> deliveryDays;

    @NotNull(message = "Start date is required")
    @FutureOrPresent(message = "Start date must be today or a future date")
    @Schema(description = "Subscription start date (today or future)", example = "2026-07-01")
    private LocalDate startDate;

    @Future(message = "End date must be in the future")
    @Schema(description = "Optional end date (must be after start date)", example = "2026-12-31")
    private LocalDate endDate;
}
