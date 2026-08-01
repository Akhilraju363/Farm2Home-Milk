package com.farm2home.events.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Published to {@code payment.events}. eventType: PAYMENT_SUCCESS | PAYMENT_FAILED | PAYMENT_REFUNDED. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentEvent {

    private String eventType;
    private UUID paymentId;
    private UUID orderId;
    private UUID customerId;
    private String paymentReference;
    private BigDecimal amount;
    private String paymentMethod;
    private LocalDateTime occurredAt;
}
