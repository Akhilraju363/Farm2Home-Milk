package com.farm2home.payment.gateway.mock;

import com.farm2home.payment.exception.PaymentGatewayException;
import com.farm2home.payment.gateway.GatewayOrderRequest;
import com.farm2home.payment.gateway.GatewayPaymentState;
import com.farm2home.payment.gateway.GatewayRefundRequest;
import com.farm2home.payment.gateway.GatewayVerificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockPaymentGatewayProviderTest {

    private final MockPaymentGatewayProvider provider = new MockPaymentGatewayProvider();

    @Test
    @DisplayName("getName() reports MOCK")
    void getName_reportsMock() {
        assertThat(provider.getName()).isEqualTo("MOCK");
    }

    @Nested
    @DisplayName("createOrder()")
    class CreateOrder {

        @Test
        @DisplayName("returns a synthetic order id and no real checkout key")
        void returnsSyntheticOrder() {
            var order = provider.createOrder(new GatewayOrderRequest("PAY-1", new BigDecimal("150.00"), "test"));

            assertThat(order.gatewayOrderId()).startsWith("mock_order_");
            assertThat(order.checkoutKeyId()).isNotBlank();
            assertThat(order.amount()).isEqualByComparingTo("150.00");
        }
    }

    @Nested
    @DisplayName("verifyPayment()")
    class VerifyPayment {

        @Test
        @DisplayName("magic valid signature → valid, CAPTURED")
        void validSignature_valid() {
            var result = provider.verifyPayment(new GatewayVerificationRequest(
                    "mock_order_1", "mock_pay_1", MockPaymentGatewayProvider.MOCK_VALID_SIGNATURE));

            assertThat(result.valid()).isTrue();
            assertThat(result.state()).isEqualTo(GatewayPaymentState.CAPTURED);
            assertThat(result.gatewayPaymentId()).isEqualTo("mock_pay_1");
        }

        @Test
        @DisplayName("any other signature → invalid, FAILED")
        void wrongSignature_invalid() {
            var result = provider.verifyPayment(new GatewayVerificationRequest(
                    "mock_order_1", "mock_pay_1", "not-the-magic-value"));

            assertThat(result.valid()).isFalse();
            assertThat(result.state()).isEqualTo(GatewayPaymentState.FAILED);
        }
    }

    @Nested
    @DisplayName("refund()")
    class Refund {

        @Test
        @DisplayName("always succeeds with a synthetic refund id")
        void alwaysSucceeds() {
            var result = provider.refund(new GatewayRefundRequest("mock_pay_1", new BigDecimal("50.00"), "PAY-1"));

            assertThat(result.gatewayRefundId()).startsWith("mock_rfnd_");
            assertThat(result.state()).isEqualTo(GatewayPaymentState.REFUNDED);
        }
    }

    @Nested
    @DisplayName("fetchPaymentStatus() / fetchOrderStatus()")
    class FetchStatus {

        @Test
        @DisplayName("fetchPaymentStatus() never auto-resolves — always UNKNOWN")
        void fetchPaymentStatus_alwaysUnknown() {
            assertThat(provider.fetchPaymentStatus("mock_pay_1").state()).isEqualTo(GatewayPaymentState.UNKNOWN);
        }

        @Test
        @DisplayName("fetchOrderStatus() never auto-resolves — always UNKNOWN")
        void fetchOrderStatus_alwaysUnknown() {
            assertThat(provider.fetchOrderStatus("mock_order_1").state()).isEqualTo(GatewayPaymentState.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("parseWebhookEvent()")
    class ParseWebhookEvent {

        @Test
        @DisplayName("throws — mock provider has no webhook delivery to simulate")
        void throwsUnsupported() {
            assertThatThrownBy(() -> provider.parseWebhookEvent("{}", "sig"))
                    .isInstanceOf(PaymentGatewayException.class)
                    .hasMessageContaining("mock payment gateway");
        }
    }
}
