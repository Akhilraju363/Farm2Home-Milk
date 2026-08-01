package com.farm2home.payment.gateway;

/** Provider-agnostic view of a gateway payment's lifecycle state, so the service layer never
 *  branches on a specific provider's own status vocabulary (e.g. Razorpay's "captured"). */
public enum GatewayPaymentState {
    CREATED,
    AUTHORIZED,
    CAPTURED,
    FAILED,
    REFUNDED,
    UNKNOWN
}
