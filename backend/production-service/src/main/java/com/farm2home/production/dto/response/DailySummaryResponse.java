package com.farm2home.production.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data @NoArgsConstructor @AllArgsConstructor
public class DailySummaryResponse {
    private LocalDate date;
    private BigDecimal totalLiters;
    private long recordCount;
}
