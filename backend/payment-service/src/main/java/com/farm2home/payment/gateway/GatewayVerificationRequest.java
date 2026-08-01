package com.farm2home.payment.gateway;

/** What a client-side checkout widget reports back after the customer completes payment. */
public record GatewayVerificationRequest(String gatewayOrderId, String gatewayPaymentId, String signature) {
}
