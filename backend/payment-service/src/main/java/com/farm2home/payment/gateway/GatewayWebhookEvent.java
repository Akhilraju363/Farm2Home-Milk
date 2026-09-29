package com.farm2home.payment.gateway;

import java.math.BigDecimal;

/** A parsed, signature-verified webhook notification. {@code eventType} is kept as the
 *  provider's own raw string (e.g. "payment.captured") purely for logging/audit — all business
 *  logic branches on the normalized {@code state} instead. {@code amount} is the gateway-reported
 *  payment amount in rupees, or null when the event carries none. */
public record GatewayWebhookEvent(String eventType, String gatewayOrderId, String gatewayPaymentId,
                                   GatewayPaymentState state, String rawPayload, BigDecimal amount) {
}
