package com.farm2home.payment.gateway;

/** Result of verifying a client-reported checkout completion. {@code valid} reflects both
 *  signature authenticity and (where the provider supports it) a confirmatory server-side status
 *  fetch — a forged or stale signature never yields {@code valid=true}. */
public record GatewayPaymentVerification(boolean valid, String gatewayPaymentId, GatewayPaymentState state,
                                          String rawResponse) {
}
