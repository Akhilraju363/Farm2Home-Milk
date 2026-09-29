package com.farm2home.payment.gateway;

import java.math.BigDecimal;

/** What a client-side checkout widget reports back after the customer completes payment, plus
 *  the amount this service expects to have been collected (always the payment's own stored
 *  amount, never a client-supplied value) so the provider can reject a mismatched payment. */
public record GatewayVerificationRequest(String gatewayOrderId, String gatewayPaymentId, String signature,
                                         BigDecimal expectedAmount) {
}
