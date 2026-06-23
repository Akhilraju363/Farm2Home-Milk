package com.farm2home.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {
    private UUID id;
    private UUID orderId;
    private UUID customerId;
    private String paymentReference;
    private BigDecimal amount;
    private String paymentMethod;
    private String paymentStatus;
    private String gatewayResponse;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
