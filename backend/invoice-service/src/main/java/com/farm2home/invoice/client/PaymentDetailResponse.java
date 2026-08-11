package com.farm2home.invoice.client;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Minimal local projection of payment-service's real PaymentResponse - only the fields an
 *  invoice's payment section renders. */
@Data
public class PaymentDetailResponse {
    private UUID id;
    private UUID orderId;
    private String paymentReference;
    private BigDecimal amount;
    private String paymentMethod;
    private String paymentStatus;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
