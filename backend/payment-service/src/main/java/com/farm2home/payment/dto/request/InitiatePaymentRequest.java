package com.farm2home.payment.dto.request;

import com.farm2home.payment.domain.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class InitiatePaymentRequest {

    @Schema(description = "Customer ID — omit to use the authenticated user's ID (admins can set explicitly)")
    private UUID customerId;

    @NotNull(message = "Order ID is required")
    private UUID orderId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    @jakarta.validation.constraints.Size(max = 255, message = "Notes must be at most 255 characters")
    private String notes;
}
