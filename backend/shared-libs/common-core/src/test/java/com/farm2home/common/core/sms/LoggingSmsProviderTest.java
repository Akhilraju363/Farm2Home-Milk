package com.farm2home.common.core.sms;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingSmsProviderTest {

    private final LoggingSmsProvider provider = new LoggingSmsProvider();

    @Test
    @DisplayName("getName() reports LOGGING")
    void getName_reportsLogging() {
        assertThat(provider.getName()).isEqualTo("LOGGING");
    }

    @Test
    @DisplayName("send() always succeeds with a synthetic provider message id")
    void send_alwaysSucceeds() {
        SmsSendResult result = provider.send(new SmsMessage("9876543210", "Your OTP is 123456", "OTP"));

        assertThat(result.success()).isTrue();
        assertThat(result.providerMessageId()).startsWith("logged-");
        assertThat(result.failureReason()).isNull();
    }
}
