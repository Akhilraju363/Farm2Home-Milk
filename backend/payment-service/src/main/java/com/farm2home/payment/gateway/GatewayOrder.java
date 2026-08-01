package com.farm2home.payment.gateway;

import java.math.BigDecimal;

/**
 * @param gatewayOrderId the provider's order identifier — stored on {@code Payment} to correlate
 *                        later verification/webhook/reconciliation calls back to this attempt
 * @param checkoutKeyId   the provider's PUBLIC key/identifier a client-side checkout widget needs
 *                        to launch (e.g. Razorpay's "Key ID"). Never a secret — providers document
 *                        this value as safe to ship to the browser — so it's safe to return in an
 *                        API response.
 */
public record GatewayOrder(String gatewayOrderId, String checkoutKeyId, BigDecimal amount, String currency) {
}
