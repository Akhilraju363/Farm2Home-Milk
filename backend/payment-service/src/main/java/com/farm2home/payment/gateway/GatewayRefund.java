package com.farm2home.payment.gateway;

public record GatewayRefund(String gatewayRefundId, GatewayPaymentState state, String rawResponse) {
}
