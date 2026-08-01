package com.farm2home.common.core.push;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingPushProviderTest {

    private final LoggingPushProvider provider = new LoggingPushProvider();

    @Test
    @DisplayName("getName() reports LOGGING")
    void getName_reportsLogging() {
        assertThat(provider.getName()).isEqualTo("LOGGING");
    }

    @Test
    @DisplayName("send() always succeeds with a synthetic provider message id")
    void send_alwaysSucceeds() {
        PushSendResult result = provider.send(
                new PushMessage("customer-1", "Order Confirmed", "Your order has been placed", "ORDER_CREATED"));

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).startsWith("logged-");
        assertThat(result.failureReason()).isNull();
    }
}
