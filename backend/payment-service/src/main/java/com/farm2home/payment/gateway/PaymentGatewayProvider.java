package com.farm2home.payment.gateway;

/**
 * Provider abstraction over a real payment gateway (e.g. Razorpay) so {@code PaymentServiceImpl}
 * never talks to a specific vendor's API directly. Exactly one implementation is active per
 * environment, selected via the {@code payment.gateway.provider} property (see
 * {@code PaymentGatewayConfig}): {@code mock} for local development/tests (default, no external
 * calls, no credentials required) or {@code razorpay} for production.
 *
 * All methods are synchronous/blocking, matching every other outbound call in this service
 * (e.g. {@code OrderServiceClient}) — payment initiation is infrequent enough per request that a
 * blocking round trip is a fair trade for keeping the transactional service method straight-line
 * readable.
 */
public interface PaymentGatewayProvider {

    /** Identifies the active provider for diagnostics/audit trails (e.g. "MOCK", "RAZORPAY"). */
    String getName();

    /** Creates an order on the gateway for a not-yet-collected payment. Called once per payment
     *  attempt, right after the local PENDING {@code Payment} row is built (before it's saved). */
    GatewayOrder createOrder(GatewayOrderRequest request);

    /** Verifies a client-reported successful checkout. Implementations must reject a forged or
     *  mismatched signature outright — never trust the client's claim of success alone. */
    GatewayPaymentVerification verifyPayment(GatewayVerificationRequest request);

    /** Issues a refund for a previously captured payment, identified by the gateway's own
     *  payment id (not our internal payment id). */
    GatewayRefund refund(GatewayRefundRequest request);

    /** Fetches the gateway's current view of a specific payment — used when a payment id is
     *  already known (e.g. admin/support tooling). */
    GatewayPaymentStatus fetchPaymentStatus(String gatewayPaymentId);

    /** Fetches the gateway's current view of an order that may not have an associated payment id
     *  yet (the customer may have abandoned checkout, or never called back at all). This is the
     *  primary signal the reconciliation job uses for payments stuck in PENDING with no webhook
     *  ever received. */
    GatewayPaymentStatus fetchOrderStatus(String gatewayOrderId);

    /** Validates the webhook request's signature and parses it into a normalized event. Must
     *  throw {@link com.farm2home.payment.exception.PaymentGatewayException} rather than return a
     *  "best guess" if the signature doesn't check out, so the controller never acts on an
     *  unverified payload. */
    GatewayWebhookEvent parseWebhookEvent(String rawPayload, String signatureHeader);
}
