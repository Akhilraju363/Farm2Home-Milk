package com.farm2home.payment.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentEvent {

    private String eventType;   // PAYMENT_SUCCESS | PAYMENT_FAILED | PAYMENT_REFUNDED
    private UUID paymentId;
    private UUID orderId;
    private UUID customerId;
    private String paymentReference;
    private BigDecimal amount;
    private String paymentMethod;
    private LocalDateTime occurredAt;
}
