package com.farm2home.payment.gateway;

import java.math.BigDecimal;

public record GatewayRefundRequest(String gatewayPaymentId, BigDecimal amount, String internalReference) {
}
