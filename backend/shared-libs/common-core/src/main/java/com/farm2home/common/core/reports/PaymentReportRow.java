package com.farm2home.common.core.reports;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One row of the Payment Report (payment-service). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReportRow {
    @Schema(example = "9a4b5c6d-7e8f-490a-1b2c-3d4e5f60718a")
    private UUID paymentId;
    @Schema(example = "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b")
    private UUID orderId;
    @Schema(example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID customerId;
    @Schema(example = "450.00")
    private BigDecimal amount;
    @Schema(example = "UPI")
    private String paymentMethod;
    @Schema(description = "payment-service PaymentStatus enum name", example = "SUCCESS")
    private String paymentStatus;
    @Schema(description = "Null until the payment reaches a terminal SUCCESS status", example = "2026-07-15T10:05:00")
    private LocalDateTime paidAt;
    @Schema(example = "2026-07-15T10:00:00")
    private LocalDateTime createdAt;
}
