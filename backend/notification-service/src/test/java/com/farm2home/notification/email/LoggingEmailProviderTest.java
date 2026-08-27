package com.farm2home.notification.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingEmailProviderTest {

    private final LoggingEmailProvider provider = new LoggingEmailProvider();

    @Test
    @DisplayName("getName() reports LOGGING")
    void getName_reportsLogging() {
        assertThat(provider.getName()).isEqualTo("LOGGING");
    }

    @Test
    @DisplayName("send() always succeeds with a synthetic provider message id - never a real SMTP call")
    void send_alwaysSucceeds() {
        EmailSendResult result = provider.send(
                new EmailMessage("customer@example.com", "Order Confirmed", "<p>body</p>", "ORDER_CREATED"));

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).startsWith("logged-");
        assertThat(result.failureReason()).isNull();
    }
}
