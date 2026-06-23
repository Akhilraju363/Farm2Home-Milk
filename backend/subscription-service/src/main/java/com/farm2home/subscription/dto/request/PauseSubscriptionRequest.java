package com.farm2home.subscription.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for pausing a subscription")
public class PauseSubscriptionRequest {

    @NotNull(message = "Pause end date is required")
    @Future(message = "Pause end date must be a future date")
    @Schema(description = "Date when the subscription should automatically resume", example = "2026-08-01")
    private LocalDate pauseEnd;
}
