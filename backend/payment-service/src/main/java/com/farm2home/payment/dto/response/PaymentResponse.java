package com.farm2home.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentResponse {
    @Schema(description = "Payment's own UUID (distinct from paymentReference and from orderId).",
            example = "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b")
    private UUID id;

    @Schema(description = "Order this payment was made for.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID orderId;

    @Schema(description = "Customer the payment is billed to.", example = "1a2b3c4d-5e6f-4a1b-8c9d-0e1f2a3b4c5d")
    private UUID customerId;

    @Schema(description = "Human-readable payment reference generated at initiation time, in the form "
            + "PAY-<epoch millis>-<8 hex chars>.", example = "PAY-1753900000000-A1B2C3D4")
    private String paymentReference;

    @Schema(description = "Payment amount in rupees.", example = "499.00")
    private BigDecimal amount;

    @Schema(description = "One of UPI, RAZORPAY, WALLET, CASH.", example = "RAZORPAY")
    private String paymentMethod;

    @Schema(description = "One of PENDING, SUCCESS, FAILED, REFUNDED.", example = "SUCCESS")
    private String paymentStatus;

    @Schema(description = "Raw response/notes captured from the gateway, manual callback, or webhook that last "
            + "updated this payment's status. Format varies by source and is not guaranteed to be JSON.")
    private String gatewayResponse;

    @Schema(description = "Gateway's own order ID, present once a gateway order has been created for a "
            + "UPI/RAZORPAY payment (null for WALLET/CASH).", example = "order_NxHhkjb2C4gY7a")
    private String gatewayOrderId;

    @Schema(description = "Gateway's own payment ID, populated once verify() or the webhook confirms the "
            + "gateway-side payment attempt (null for WALLET/CASH and for still-PENDING gateway payments).",
            example = "pay_NxHiab12C4gY7b")
    private String gatewayPaymentId;

    @Schema(description = "Gateway's PUBLIC checkout key — only present on the response to POST "
            + "/payments (initiate) for UPI/RAZORPAY methods; needed by a client-side checkout widget. "
            + "Never a secret, never returned on subsequent reads of the same payment.",
            example = "rzp_test_1DP5mmOlF5G5ag")
    private String gatewayCheckoutKeyId;

    @Schema(description = "When the payment transitioned to SUCCESS. Null while PENDING/FAILED.",
            example = "2026-07-31T10:16:05")
    private LocalDateTime paidAt;

    @Schema(description = "When the payment record was created.", example = "2026-07-31T10:15:30")
    private LocalDateTime createdAt;
}
