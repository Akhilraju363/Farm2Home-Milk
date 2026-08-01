package com.farm2home.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** What a client-side checkout widget (e.g. Razorpay Checkout) hands back to the frontend once
 *  the customer completes payment - forwarded here as-is for server-side verification. */
@Data
public class VerifyPaymentRequest {

    @NotBlank(message = "Gateway order ID is required")
    @Schema(description = "The gateway order ID this payment was initiated against. Must exactly match the "
            + "payment's stored gatewayOrderId or verification is rejected before the signature is even checked.",
            example = "order_NxHhkjb2C4gY7a")
    private String gatewayOrderId;

    @NotBlank(message = "Gateway payment ID is required")
    @Schema(description = "The gateway's ID for the completed payment attempt, as reported by the checkout widget.",
            example = "pay_NxHiab12C4gY7b")
    private String gatewayPaymentId;

    @NotBlank(message = "Signature is required")
    @Schema(description = "HMAC signature from the checkout widget, verified server-side against the "
            + "gateway's secret before the payment is trusted as successful.",
            example = "9ef4dffbfd84f1318f6739a3ce19f9d85851857ae648f114332d8401e0949a3")
    private String signature;
}
