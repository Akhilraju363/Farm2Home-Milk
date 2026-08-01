package com.farm2home.common.core.reports;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One row of the Sales Report (order-service). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesReportRow {

    @Schema(example = "a1b2c3d4-e5f6-4789-9abc-1234567890ab")
    private UUID orderId;

    @Schema(example = "ORD-2026-100042")
    private String orderNumber;

    @Schema(example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID customerId;

    @Schema(example = "2026-06-25")
    private LocalDate orderDate;

    @Schema(example = "DELIVERED")
    private String status;

    @Schema(description = "Order total in rupees", example = "120.00")
    private BigDecimal totalAmount;
}
