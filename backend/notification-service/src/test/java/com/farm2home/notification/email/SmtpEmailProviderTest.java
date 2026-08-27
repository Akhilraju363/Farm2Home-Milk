package com.farm2home.notification.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmtpEmailProviderTest {

    @Mock private JavaMailSender mailSender;

    private MimeMessage newMimeMessage() {
        return new MimeMessage(Session.getDefaultInstance(new Properties()));
    }

    @Nested
    @DisplayName("send()")
    class Send {

        @Test
        @DisplayName("getName() reports SMTP")
        void getName_reportsSmtp() {
            SmtpEmailProvider provider = new SmtpEmailProvider(Optional.of(mailSender), "no-reply@farm2homemilk.example", "Farm2Home");

            assertThat(provider.getName()).isEqualTo("SMTP");
        }

        @Test
        @DisplayName("mail sender configured, send succeeds → returns success")
        void mailSenderConfigured_sendSucceeds() {
            when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());
            SmtpEmailProvider provider = new SmtpEmailProvider(Optional.of(mailSender), "no-reply@farm2homemilk.example", "Farm2Home");

            EmailSendResult result = provider.send(
                    new EmailMessage("customer@example.com", "Order Confirmed", "<p>body</p>", "ORDER_CREATED"));

            assertThat(result.success()).isTrue();
            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("mail sender not configured (Optional.empty) → graceful failure result, never throws")
        void mailSenderNotConfigured_gracefulFailure() {
            SmtpEmailProvider provider = new SmtpEmailProvider(Optional.empty(), "no-reply@farm2homemilk.example", "Farm2Home");

            EmailSendResult result = provider.send(
                    new EmailMessage("customer@example.com", "Order Confirmed", "<p>body</p>", "ORDER_CREATED"));

            assertThat(result.success()).isFalse();
            assertThat(result.failureReason()).isEqualTo("Mail sender not configured");
        }

        @Test
        @DisplayName("JavaMailSender.send() throws (e.g. auth failure) → graceful failure result, never propagates")
        void sendThrows_gracefulFailure() {
            when(mailSender.createMimeMessage()).thenReturn(newMimeMessage());
            doThrow(new org.springframework.mail.MailSendException("535 Authentication failed"))
                    .when(mailSender).send(any(MimeMessage.class));
            SmtpEmailProvider provider = new SmtpEmailProvider(Optional.of(mailSender), "no-reply@farm2homemilk.example", "Farm2Home");

            EmailSendResult result = provider.send(
                    new EmailMessage("customer@example.com", "Order Confirmed", "<p>body</p>", "ORDER_CREATED"));

            assertThat(result.success()).isFalse();
            assertThat(result.failureReason()).contains("535 Authentication failed");
        }
    }
}
