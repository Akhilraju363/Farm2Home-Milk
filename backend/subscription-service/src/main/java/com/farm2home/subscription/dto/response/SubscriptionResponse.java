package com.farm2home.subscription.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Subscription details")
public class SubscriptionResponse {

    private UUID id;
    private UUID customerId;
    private String milkType;
    private BigDecimal quantity;
    private String scheduleType;
    private List<String> deliveryDays;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private LocalDate pauseStart;
    private LocalDate pauseEnd;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
