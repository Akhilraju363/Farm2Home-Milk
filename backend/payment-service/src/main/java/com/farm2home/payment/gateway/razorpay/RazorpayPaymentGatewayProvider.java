package com.farm2home.payment.gateway.razorpay;

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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Talks to Razorpay's plain REST API (https://api.razorpay.com/v1) over HTTPS with HTTP Basic
 * auth (key id : key secret) — Razorpay has no requirement to use its Java SDK, and a hand-rolled
 * client keeps this module's dependency footprint unchanged (spring-boot-starter-webflux is
 * already a payment-service dependency for the existing OrderServiceClient) and easy to unit test
 * the same way OrderServiceClient is (a stubbed WebClient ExchangeFunction, no real network).
 *
 * Amounts cross the wire in paise (Razorpay's smallest currency unit for INR), converted at the
 * boundary in {@link #toSmallestUnit}; everywhere else in this service amounts stay in rupees.
 */
@Slf4j
public class RazorpayPaymentGatewayProvider implements PaymentGatewayProvider {

    private final WebClient webClient;
    private final RazorpayProperties properties;
    private final ObjectMapper objectMapper;

    public RazorpayPaymentGatewayProvider(WebClient.Builder builder, RazorpayProperties properties,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = builder
                .baseUrl(properties.getBaseUrl())
                .defaultHeaders(h -> h.setBasicAuth(properties.getKeyId(), properties.getKeySecret()))
                .build();
    }

    @Override
    public String getName() {
        return "RAZORPAY";
    }

    @Override
    public GatewayOrder createOrder(GatewayOrderRequest request) {
        Map<String, Object> body = Map.of(
                "amount", toSmallestUnit(request.amount()),
                "currency", properties.getCurrency(),
                "receipt", request.internalReference(),
                // Auto-capture on authorization — this app has no separate "capture" step/UI, so
                // a payment is either fully captured or it never happened as far as we're concerned.
                "payment_capture", 1,
                "notes", Map.of("description", request.description() == null ? "" : request.description()));

        JsonNode response = post("/orders", body, "create order for " + request.internalReference());
        return new GatewayOrder(textOrNull(response, "id"), properties.getKeyId(), request.amount(),
                textOrNull(response, "currency"));
    }

    @Override
    public GatewayPaymentVerification verifyPayment(GatewayVerificationRequest request) {
        boolean signatureValid = RazorpaySignature.isValid(
                request.gatewayOrderId() + "|" + request.gatewayPaymentId(),
                request.signature(), properties.getKeySecret());

        if (!signatureValid) {
            return new GatewayPaymentVerification(false, request.gatewayPaymentId(), GatewayPaymentState.FAILED,
                    "{\"reason\":\"signature_mismatch\"}");
        }

        // A valid signature proves the response came from Razorpay, but not that funds were
        // actually captured — fetch the payment itself rather than trust the client's report.
        GatewayPaymentStatus status = fetchPaymentStatus(request.gatewayPaymentId());
        return new GatewayPaymentVerification(status.state() == GatewayPaymentState.CAPTURED,
                request.gatewayPaymentId(), status.state(), status.rawResponse());
    }

    @Override
    public GatewayRefund refund(GatewayRefundRequest request) {
        if (request.gatewayPaymentId() == null) {
            throw new PaymentGatewayException(
                    "Cannot refund via Razorpay: no gateway payment id recorded for " + request.internalReference());
        }
        Map<String, Object> body = Map.of(
                "amount", toSmallestUnit(request.amount()),
                "notes", Map.of("internalReference", request.internalReference()));

        JsonNode response = post("/payments/" + request.gatewayPaymentId() + "/refund", body,
                "refund payment " + request.gatewayPaymentId());
        return new GatewayRefund(textOrNull(response, "id"), mapRefundStatus(textOrNull(response, "status")),
                response.toString());
    }

    @Override
    public GatewayPaymentStatus fetchPaymentStatus(String gatewayPaymentId) {
        try {
            JsonNode response = webClient.get().uri("/payments/{paymentId}", gatewayPaymentId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            return new GatewayPaymentStatus(gatewayPaymentId, mapPaymentStatus(textOrNull(response, "status")),
                    response == null ? null : response.toString());
        } catch (WebClientResponseException.NotFound ex) {
            return new GatewayPaymentStatus(gatewayPaymentId, GatewayPaymentState.UNKNOWN, null);
        } catch (WebClientException ex) {
            throw new PaymentGatewayException(
                    "Could not fetch payment status from Razorpay right now. Please try again.", ex);
        }
    }

    @Override
    public GatewayPaymentStatus fetchOrderStatus(String gatewayOrderId) {
        try {
            JsonNode response = webClient.get().uri("/orders/{orderId}/payments", gatewayOrderId)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if (response != null) {
                for (JsonNode item : response.path("items")) {
                    GatewayPaymentState state = mapPaymentStatus(textOrNull(item, "status"));
                    if (state == GatewayPaymentState.CAPTURED || state == GatewayPaymentState.FAILED) {
                        return new GatewayPaymentStatus(textOrNull(item, "id"), state, item.toString());
                    }
                }
            }
            return new GatewayPaymentStatus(null, GatewayPaymentState.UNKNOWN, response == null ? null : response.toString());
        } catch (WebClientResponseException.NotFound ex) {
            return new GatewayPaymentStatus(null, GatewayPaymentState.UNKNOWN, null);
        } catch (WebClientException ex) {
            throw new PaymentGatewayException(
                    "Could not fetch order status from Razorpay right now. Please try again.", ex);
        }
    }

    @Override
    public GatewayWebhookEvent parseWebhookEvent(String rawPayload, String signatureHeader) {
        if (!RazorpaySignature.isValid(rawPayload, signatureHeader, properties.getWebhookSecret())) {
            throw new PaymentGatewayException("Invalid Razorpay webhook signature");
        }
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            String eventType = root.path("event").asText();
            JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
            GatewayPaymentState state = eventType.startsWith("payment.captured") ? GatewayPaymentState.CAPTURED
                    : eventType.startsWith("payment.failed") ? GatewayPaymentState.FAILED
                    : eventType.startsWith("refund.") ? GatewayPaymentState.REFUNDED
                    : mapPaymentStatus(textOrNull(paymentEntity, "status"));
            return new GatewayWebhookEvent(eventType, textOrNull(paymentEntity, "order_id"),
                    textOrNull(paymentEntity, "id"), state, rawPayload);
        } catch (JsonProcessingException ex) {
            throw new PaymentGatewayException("Malformed Razorpay webhook payload", ex);
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private JsonNode post(String uri, Map<String, Object> body, String action) {
        try {
            JsonNode response = webClient.post().uri(uri)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
            if (response == null) {
                throw new PaymentGatewayException("Razorpay returned an empty response for: " + action);
            }
            return response;
        } catch (WebClientException ex) {
            log.warn("Razorpay call failed [{}]: {}", action, ex.getMessage());
            throw new PaymentGatewayException("Could not " + action + " with Razorpay right now. Please try again.", ex);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return null;
        }
        return node.path(field).asText();
    }

    private static long toSmallestUnit(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static GatewayPaymentState mapPaymentStatus(String status) {
        if (status == null) {
            return GatewayPaymentState.UNKNOWN;
        }
        return switch (status) {
            case "created" -> GatewayPaymentState.CREATED;
            case "authorized" -> GatewayPaymentState.AUTHORIZED;
            case "captured" -> GatewayPaymentState.CAPTURED;
            case "failed" -> GatewayPaymentState.FAILED;
            case "refunded" -> GatewayPaymentState.REFUNDED;
            default -> GatewayPaymentState.UNKNOWN;
        };
    }

    private static GatewayPaymentState mapRefundStatus(String status) {
        return "processed".equals(status) ? GatewayPaymentState.REFUNDED : GatewayPaymentState.UNKNOWN;
    }
}
