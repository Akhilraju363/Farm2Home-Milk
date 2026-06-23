package com.farm2home.order.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class GenerationResultResponse {
    private LocalDate date;
    private int ordersCreated;
    private int subscriptionsProcessed;
    private int skipped;
    private String message;
}
