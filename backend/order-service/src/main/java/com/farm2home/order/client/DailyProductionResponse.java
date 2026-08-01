package com.farm2home.order.client;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Minimal local projection of production-service's DailySummaryResponse - only the fields
 *  order-service actually needs to validate a same-day order against recorded production.
 *  Jackson ignores the rest of the real response body (Spring Boot's default ObjectMapper does
 *  not fail on unknown properties). */
@Data
public class DailyProductionResponse {
    private LocalDate date;
    private BigDecimal totalLiters;
}
