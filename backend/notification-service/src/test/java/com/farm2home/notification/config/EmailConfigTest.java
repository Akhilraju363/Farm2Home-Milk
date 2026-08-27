package com.farm2home.notification.config;

import com.farm2home.notification.email.EmailProperties;
import com.farm2home.notification.email.EmailProvider;
import com.farm2home.notification.email.LoggingEmailProvider;
import com.farm2home.notification.email.SmtpEmailProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** EmailConfig.emailProvider() is the whole EMAIL_ENABLED/EMAIL_PROVIDER contract - tested
 *  directly (no Spring context needed) rather than via a slow @SpringBootTest. */
class EmailConfigTest {

    private EmailConfig newConfig() {
        EmailConfig config = new EmailConfig();
        ReflectionTestUtils.setField(config, "mailFrom", "no-reply@farm2homemilk.example");
        return config;
    }

    private EmailProperties properties(boolean enabled, String provider) {
        EmailProperties properties = new EmailProperties();
        properties.setEnabled(enabled);
        properties.setProvider(provider);
        return properties;
    }

    @Nested
    @DisplayName("emailProvider()")
    class EmailProviderBean {

        @Test
        @DisplayName("email.enabled=false (default) → LoggingEmailProvider, regardless of email.provider")
        void disabled_selectsLogging() {
            EmailProvider provider = newConfig().emailProvider(Optional.empty(), properties(false, "smtp"));

            assertThat(provider).isInstanceOf(LoggingEmailProvider.class);
        }

        @Test
        @DisplayName("email.enabled=true, email.provider=smtp → SmtpEmailProvider")
        void enabledSmtp_selectsSmtp() {
            EmailProvider provider = newConfig().emailProvider(Optional.of(org.mockito.Mockito.mock(JavaMailSender.class)), properties(true, "smtp"));

            assertThat(provider).isInstanceOf(SmtpEmailProvider.class);
        }

        @Test
        @DisplayName("email.enabled=true but email.provider is not smtp → falls back to LoggingEmailProvider")
        void enabledUnknownProvider_fallsBackToLogging() {
            EmailProvider provider = newConfig().emailProvider(Optional.empty(), properties(true, "unknown"));

            assertThat(provider).isInstanceOf(LoggingEmailProvider.class);
        }
    }
}
