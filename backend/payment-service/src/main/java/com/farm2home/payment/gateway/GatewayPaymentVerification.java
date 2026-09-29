package com.farm2home.payment.gateway;

/** Result of verifying a client-reported checkout completion.
 *
 *  {@code signatureValid} only says the report is authentic (it really came from the gateway for
 *  this gateway order). Whether money was actually collected is {@code state}, which the provider
 *  confirms server-side where it can: only {@link GatewayPaymentState#CAPTURED} may mark a payment
 *  SUCCESS. An authentic report for a payment that is merely AUTHORIZED/CREATED is not a failure -
 *  the payment stays PENDING until the webhook or reconciliation job sees it captured. */
public record GatewayPaymentVerification(boolean signatureValid, String gatewayPaymentId, GatewayPaymentState state,
                                          String rawResponse) {
}
