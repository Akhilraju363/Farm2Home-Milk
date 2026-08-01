package com.farm2home.payment.gateway;

import java.math.BigDecimal;

/**
 * @param internalReference our own payment reference (e.g. "PAY-..."), passed to the gateway as
 *                           its receipt/notes field so a support agent can cross-reference a
 *                           gateway dashboard entry back to our records
 * @param amount             in the payment's normal currency unit (e.g. rupees, not paise) —
 *                           providers convert to their own smallest unit internally
 * @param description        human-readable note shown on the gateway's own dashboard/receipt
 */
public record GatewayOrderRequest(String internalReference, BigDecimal amount, String description) {
}
