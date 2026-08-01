package com.farm2home.payment.gateway.razorpay;

import com.farm2home.payment.exception.PaymentGatewayException;
import com.farm2home.payment.gateway.GatewayOrderRequest;
import com.farm2home.payment.gateway.GatewayPaymentState;
import com.farm2home.payment.gateway.GatewayPaymentStatus;
import com.farm2home.payment.gateway.GatewayRefundRequest;
import com.farm2home.payment.gateway.GatewayVerificationRequest;
import com.farm2home.payment.gateway.GatewayWebhookEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Same stubbed-ExchangeFunction technique as OrderServiceClientTest/ProductionServiceClientTest —
 * a canned JSON body stands in for a real Razorpay response, exercising the real request-building/
 * response-parsing code without a network dependency. Signatures are computed with the exact same
 * HMAC-SHA256 algorithm RazorpaySignature uses internally, over a fixed test key secret, so these
 * tests never need to see production credentials.
 */
class RazorpayPaymentGatewayProviderTest {

    private static final String KEY_ID = "rzp_test_key_id";
    private static final String KEY_SECRET = "test_key_secret";
    private static final String WEBHOOK_SECRET = "test_webhook_secret";

    private RazorpayProperties properties() {
        RazorpayProperties props = new RazorpayProperties();
        props.setKeyId(KEY_ID);
        props.setKeySecret(KEY_SECRET);
        props.setWebhookSecret(WEBHOOK_SECRET);
        props.setBaseUrl("https://api.razorpay.com/v1");
        props.setCurrency("INR");
        return props;
    }

    private RazorpayPaymentGatewayProvider providerReturning(String jsonBody) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(jsonBody)
                        .build()));
        return new RazorpayPaymentGatewayProvider(builder, properties(), new ObjectMapper());
    }

    private RazorpayPaymentGatewayProvider providerReturningStatus(HttpStatus status, String jsonBody) {
        WebClient.Builder builder = WebClient.builder().exchangeFunction(request ->
                Mono.just(ClientResponse.create(status)
                        .header("Content-Type", "application/json")
                        .body(jsonBody)
                        .build()));
        return new RazorpayPaymentGatewayProvider(builder, properties(), new ObjectMapper());
    }

    private static String hmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    @DisplayName("getName() reports RAZORPAY")
    void getName_reportsRazorpay() {
        assertThat(providerReturning("{}").getName()).isEqualTo("RAZORPAY");
    }

    @Nested
    @DisplayName("createOrder()")
    class CreateOrder {

        @Test
        @DisplayName("parses the gateway order id and echoes the public key id")
        void parsesOrder() {
            var provider = providerReturning("{\"id\":\"order_ABC123\",\"currency\":\"INR\",\"amount\":15000}");

            var order = provider.createOrder(new GatewayOrderRequest("PAY-1", new BigDecimal("150.00"), "Order test"));

            assertThat(order.gatewayOrderId()).isEqualTo("order_ABC123");
            assertThat(order.checkoutKeyId()).isEqualTo(KEY_ID);
            assertThat(order.amount()).isEqualByComparingTo("150.00");
        }

        @Test
        @DisplayName("gateway unreachable → PaymentGatewayException")
        void unreachable_throws() {
            // WebClient itself throws WebClientRequestException (wrapping the real cause) for a
            // connection failure — a raw ConnectException would get wrapped by Reactor into
            // Exceptions$ReactiveException instead, which wouldn't satisfy our WebClientException
            // catch clause, so this mirrors what actually happens over the wire.
            WebClient.Builder builder = WebClient.builder().exchangeFunction(request ->
                    Mono.error(new org.springframework.web.reactive.function.client.WebClientRequestException(
                            new java.net.ConnectException("refused"),
                            org.springframework.http.HttpMethod.POST,
                            java.net.URI.create("https://api.razorpay.com/v1/orders"),
                            new org.springframework.http.HttpHeaders())));
            var provider = new RazorpayPaymentGatewayProvider(builder, properties(), new ObjectMapper());

            assertThatThrownBy(() -> provider.createOrder(new GatewayOrderRequest("PAY-1", BigDecimal.TEN, "x")))
                    .isInstanceOf(PaymentGatewayException.class);
        }
    }

    @Nested
    @DisplayName("verifyPayment()")
    class VerifyPayment {

        @Test
        @DisplayName("valid signature + gateway reports captured → valid")
        void validSignatureAndCaptured_valid() {
            String signature = hmac("order_ABC123|pay_XYZ789", KEY_SECRET);
            var provider = providerReturning("{\"id\":\"pay_XYZ789\",\"status\":\"captured\"}");

            var result = provider.verifyPayment(new GatewayVerificationRequest("order_ABC123", "pay_XYZ789", signature));

            assertThat(result.valid()).isTrue();
            assertThat(result.state()).isEqualTo(GatewayPaymentState.CAPTURED);
        }

        @Test
        @DisplayName("valid signature but gateway reports failed → invalid")
        void validSignatureButFailed_invalid() {
            String signature = hmac("order_ABC123|pay_XYZ789", KEY_SECRET);
            var provider = providerReturning("{\"id\":\"pay_XYZ789\",\"status\":\"failed\"}");

            var result = provider.verifyPayment(new GatewayVerificationRequest("order_ABC123", "pay_XYZ789", signature));

            assertThat(result.valid()).isFalse();
        }

        @Test
        @DisplayName("forged signature → invalid, never calls the gateway")
        void forgedSignature_invalid() {
            WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
                throw new AssertionError("Should not call the gateway for a forged signature");
            });
            var provider = new RazorpayPaymentGatewayProvider(builder, properties(), new ObjectMapper());

            var result = provider.verifyPayment(new GatewayVerificationRequest("order_ABC123", "pay_XYZ789", "forged"));

            assertThat(result.valid()).isFalse();
            assertThat(result.state()).isEqualTo(GatewayPaymentState.FAILED);
        }
    }

    @Nested
    @DisplayName("refund()")
    class Refund {

        @Test
        @DisplayName("parses the refund id and status")
        void parsesRefund() {
            var provider = providerReturning("{\"id\":\"rfnd_111\",\"status\":\"processed\"}");

            var result = provider.refund(new GatewayRefundRequest("pay_XYZ789", new BigDecimal("50.00"), "PAY-1"));

            assertThat(result.gatewayRefundId()).isEqualTo("rfnd_111");
            assertThat(result.state()).isEqualTo(GatewayPaymentState.REFUNDED);
        }

        @Test
        @DisplayName("no gateway payment id recorded → PaymentGatewayException, never calls the gateway")
        void noGatewayPaymentId_throws() {
            WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
                throw new AssertionError("Should not call the gateway without a payment id");
            });
            var provider = new RazorpayPaymentGatewayProvider(builder, properties(), new ObjectMapper());

            assertThatThrownBy(() -> provider.refund(new GatewayRefundRequest(null, BigDecimal.TEN, "PAY-1")))
                    .isInstanceOf(PaymentGatewayException.class)
                    .hasMessageContaining("no gateway payment id");
        }
    }

    @Nested
    @DisplayName("fetchPaymentStatus()")
    class FetchPaymentStatus {

        @Test
        @DisplayName("maps captured/authorized/failed/refunded/created/unknown statuses")
        void mapsStatuses() {
            assertThat(providerReturning("{\"status\":\"captured\"}").fetchPaymentStatus("p").state())
                    .isEqualTo(GatewayPaymentState.CAPTURED);
            assertThat(providerReturning("{\"status\":\"authorized\"}").fetchPaymentStatus("p").state())
                    .isEqualTo(GatewayPaymentState.AUTHORIZED);
            assertThat(providerReturning("{\"status\":\"failed\"}").fetchPaymentStatus("p").state())
                    .isEqualTo(GatewayPaymentState.FAILED);
            assertThat(providerReturning("{\"status\":\"refunded\"}").fetchPaymentStatus("p").state())
                    .isEqualTo(GatewayPaymentState.REFUNDED);
            assertThat(providerReturning("{\"status\":\"created\"}").fetchPaymentStatus("p").state())
                    .isEqualTo(GatewayPaymentState.CREATED);
            assertThat(providerReturning("{\"status\":\"something_new\"}").fetchPaymentStatus("p").state())
                    .isEqualTo(GatewayPaymentState.UNKNOWN);
        }

        @Test
        @DisplayName("404 from gateway → UNKNOWN, not an exception")
        void notFound_unknown() {
            var provider = providerReturningStatus(HttpStatus.NOT_FOUND, "{\"error\":\"not found\"}");

            GatewayPaymentStatus status = provider.fetchPaymentStatus("missing");

            assertThat(status.state()).isEqualTo(GatewayPaymentState.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("fetchOrderStatus()")
    class FetchOrderStatus {

        @Test
        @DisplayName("returns the first terminal (captured/failed) payment in the order's payment list")
        void findsTerminalPayment() {
            var provider = providerReturning(
                    "{\"items\":[{\"id\":\"pay_1\",\"status\":\"created\"},"
                            + "{\"id\":\"pay_2\",\"status\":\"captured\"}]}");

            GatewayPaymentStatus status = provider.fetchOrderStatus("order_ABC123");

            assertThat(status.gatewayPaymentId()).isEqualTo("pay_2");
            assertThat(status.state()).isEqualTo(GatewayPaymentState.CAPTURED);
        }

        @Test
        @DisplayName("no terminal payment yet → UNKNOWN, no payment id")
        void noTerminalPayment_unknown() {
            var provider = providerReturning("{\"items\":[{\"id\":\"pay_1\",\"status\":\"created\"}]}");

            GatewayPaymentStatus status = provider.fetchOrderStatus("order_ABC123");

            assertThat(status.state()).isEqualTo(GatewayPaymentState.UNKNOWN);
            assertThat(status.gatewayPaymentId()).isNull();
        }
    }

    @Nested
    @DisplayName("parseWebhookEvent()")
    class ParseWebhookEvent {

        @Test
        @DisplayName("valid signature, payment.captured → CAPTURED event")
        void capturedEvent_parsed() {
            String payload = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
                    + "{\"id\":\"pay_XYZ789\",\"order_id\":\"order_ABC123\",\"status\":\"captured\"}}}}";
            String signature = hmac(payload, WEBHOOK_SECRET);
            var provider = providerReturning("{}");

            GatewayWebhookEvent event = provider.parseWebhookEvent(payload, signature);

            assertThat(event.eventType()).isEqualTo("payment.captured");
            assertThat(event.gatewayOrderId()).isEqualTo("order_ABC123");
            assertThat(event.gatewayPaymentId()).isEqualTo("pay_XYZ789");
            assertThat(event.state()).isEqualTo(GatewayPaymentState.CAPTURED);
        }

        @Test
        @DisplayName("valid signature, payment.failed → FAILED event")
        void failedEvent_parsed() {
            String payload = "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":"
                    + "{\"id\":\"pay_XYZ789\",\"order_id\":\"order_ABC123\",\"status\":\"failed\"}}}}";
            String signature = hmac(payload, WEBHOOK_SECRET);
            var provider = providerReturning("{}");

            GatewayWebhookEvent event = provider.parseWebhookEvent(payload, signature);

            assertThat(event.state()).isEqualTo(GatewayPaymentState.FAILED);
        }

        @Test
        @DisplayName("invalid signature → PaymentGatewayException, payload never parsed")
        void invalidSignature_throws() {
            var provider = providerReturning("{}");

            assertThatThrownBy(() -> provider.parseWebhookEvent("{\"event\":\"payment.captured\"}", "wrong-signature"))
                    .isInstanceOf(PaymentGatewayException.class)
                    .hasMessageContaining("Invalid Razorpay webhook signature");
        }

        @Test
        @DisplayName("missing signature header → PaymentGatewayException")
        void missingSignature_throws() {
            var provider = providerReturning("{}");

            assertThatThrownBy(() -> provider.parseWebhookEvent("{\"event\":\"payment.captured\"}", null))
                    .isInstanceOf(PaymentGatewayException.class);
        }

        @Test
        @DisplayName("valid signature but malformed JSON → PaymentGatewayException")
        void malformedPayload_throws() {
            String payload = "{not-json";
            String signature = hmac(payload, WEBHOOK_SECRET);
            var provider = providerReturning("{}");

            assertThatThrownBy(() -> provider.parseWebhookEvent(payload, signature))
                    .isInstanceOf(PaymentGatewayException.class)
                    .hasMessageContaining("Malformed");
        }
    }
}
