package com.farm2home.payment.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import com.farm2home.payment.domain.enums.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class InitiatePaymentRequest {

    @Schema(description = "Customer ID — omit to use the authenticated user's ID (admins can set explicitly)",
            example = "1a2b3c4d-5e6f-4a1b-8c9d-0e1f2a3b4c5d")
    private UUID customerId;

    @NotNull(message = "Order ID is required")
    @Schema(description = "Order this payment is for. Must belong to an order that is not CANCELLED and does "
            + "not already have a SUCCESS/PENDING payment.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID orderId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = ValidationConstants.MIN_POSITIVE_AMOUNT, message = "Amount must be greater than zero")
    @Schema(description = "Amount to charge, in rupees. Must be greater than zero.", example = "499.00")
    private BigDecimal amount;

    @NotNull(message = "Payment method is required")
    @Schema(description = "How the payment is collected. WALLET debits the customer's in-app wallet "
            + "immediately; CASH is cash-on-delivery (stays PENDING until delivery staff mark it paid); "
            + "UPI/RAZORPAY create a gateway checkout order and stay PENDING until verified.",
            example = "RAZORPAY")
    private PaymentMethod paymentMethod;

    @jakarta.validation.constraints.Size(max = ValidationConstants.NOTES_MAX_LENGTH, message = "Notes must be at most 255 characters")
    @Schema(description = "Optional free-text note. Must be at most 255 characters.",
            example = "Weekly milk subscription payment")
    private String notes;
}
