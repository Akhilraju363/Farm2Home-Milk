package com.farm2home.subscription.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import com.farm2home.subscription.domain.enums.DeliveryDay;
import com.farm2home.subscription.domain.enums.MilkType;
import com.farm2home.subscription.domain.enums.ScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@Schema(description = "Request payload for updating an existing subscription. All fields are optional.")
public class UpdateSubscriptionRequest {

    @Schema(description = "Change milk type", example = "TONED")
    private MilkType milkType;

    @DecimalMin(value = ValidationConstants.MIN_SUBSCRIPTION_QUANTITY, message = "Minimum quantity is 0.5 litres")
    @DecimalMax(value = "10.0", message = "Maximum quantity is 10 litres")
    @Schema(description = "New daily quantity in litres", example = "2.0")
    private BigDecimal quantity;

    @Schema(description = "Change delivery schedule", example = "ALTERNATE_DAY")
    private ScheduleType scheduleType;

    @Size(max = 7, message = "Delivery days must contain at most 7 entries")
    @Schema(description = "Updated delivery days (required if changing to WEEKLY)")
    private List<DeliveryDay> deliveryDays;

    @Future(message = "End date must be in the future")
    @Schema(description = "New end date (must be after current start date)", example = "2027-06-30")
    private LocalDate endDate;
}
