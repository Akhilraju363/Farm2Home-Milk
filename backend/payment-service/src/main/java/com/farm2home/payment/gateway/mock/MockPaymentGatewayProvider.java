package com.farm2home.payment.gateway.mock;

import com.farm2home.payment.exception.PaymentGatewayException;
import com.farm2home.payment.gateway.GatewayOrder;
import com.farm2home.payment.gateway.GatewayOrderRequest;
import com.farm2home.payment.gateway.GatewayPaymentState;
import com.farm2home.payment.gateway.GatewayPaymentStatus;
import com.farm2home.payment.gateway.GatewayPaymentVerification;
import com.farm2home.payment.gateway.GatewayRefund;
import com.farm2home.payment.gateway.GatewayRefundRequest;
import com.farm2home.payment.gateway.GatewayVerificationRequest;
import com.farm2home.payment.gateway.GatewayWebhookEvent;
import com.farm2home.payment.gateway.PaymentGatewayProvider;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Default, credential-free provider used for local development and every automated test —
 * active whenever {@code payment.gateway.provider} is unset or {@code mock} (see
 * {@code PaymentGatewayConfig}). Makes no external calls and never auto-resolves a payment on its
 * own: {@link #fetchPaymentStatus} and {@link #fetchOrderStatus} always report
 * {@link GatewayPaymentState#UNKNOWN}, so the reconciliation job is a harmless no-op under mock
 * and PENDING payments only move forward the same way they always have in this codebase — a
 * manual {@code POST /payments/callback}, or verify() called with {@link #MOCK_VALID_SIGNATURE}.
 */
public class MockPaymentGatewayProvider implements PaymentGatewayProvider {

    /** The only signature verify() treats as authentic — lets tests exercise both the success and
     *  failure branches of {@link #verifyPayment} deterministically, the same way a real
     *  provider's HMAC would only validate a genuine signature. */
    public static final String MOCK_VALID_SIGNATURE = "MOCK-VALID-SIGNATURE";

    /** Returned as the checkout key so a client can tell it is talking to the mock gateway (a real
     *  Razorpay key always starts with {@code rzp_}) and must not try to open Razorpay Checkout. */
    public static final String MOCK_CHECKOUT_KEY = "mock_checkout_key";

    @Override
    public String getName() {
        return "MOCK";
    }

    @Override
    public String checkoutKeyId() {
        return MOCK_CHECKOUT_KEY;
    }

    @Override
    public GatewayOrder createOrder(GatewayOrderRequest request) {
        String gatewayOrderId = "mock_order_" + UUID.randomUUID().toString().replace("-", "");
        return new GatewayOrder(gatewayOrderId, MOCK_CHECKOUT_KEY, request.amount(), "INR");
    }

    @Override
    public GatewayPaymentVerification verifyPayment(GatewayVerificationRequest request) {
        boolean valid = MOCK_VALID_SIGNATURE.equals(request.signature())
                && StringUtils.hasText(request.gatewayPaymentId());
        String gatewayPaymentId = StringUtils.hasText(request.gatewayPaymentId())
                ? request.gatewayPaymentId()
                : "mock_pay_" + UUID.randomUUID();
        return new GatewayPaymentVerification(valid, gatewayPaymentId,
                valid ? GatewayPaymentState.CAPTURED : GatewayPaymentState.FAILED,
                "{\"mock\":true,\"valid\":" + valid + "}");
    }

    @Override
    public GatewayRefund refund(GatewayRefundRequest request) {
        return new GatewayRefund("mock_rfnd_" + UUID.randomUUID(), GatewayPaymentState.REFUNDED,
                "{\"mock\":true}");
    }

    @Override
    public GatewayPaymentStatus fetchPaymentStatus(String gatewayPaymentId) {
        return new GatewayPaymentStatus(gatewayPaymentId, GatewayPaymentState.UNKNOWN, "{\"mock\":true}");
    }

    @Override
    public GatewayPaymentStatus fetchOrderStatus(String gatewayOrderId) {
        return new GatewayPaymentStatus(null, GatewayPaymentState.UNKNOWN, "{\"mock\":true}");
    }

    @Override
    public GatewayWebhookEvent parseWebhookEvent(String rawPayload, String signatureHeader) {
        throw new PaymentGatewayException(
                "Webhook delivery is not supported by the mock payment gateway; use POST /payments/callback "
                        + "for local testing instead.");
    }
}
