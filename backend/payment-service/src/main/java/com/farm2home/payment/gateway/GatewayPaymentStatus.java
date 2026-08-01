package com.farm2home.payment.gateway;

/** {@code gatewayPaymentId} may be {@code null} when the query was scoped by order rather than
 *  by payment (e.g. a still-unpaid order has no payment id yet) and the gateway found nothing
 *  terminal to report. */
public record GatewayPaymentStatus(String gatewayPaymentId, GatewayPaymentState state, String rawResponse) {
}
