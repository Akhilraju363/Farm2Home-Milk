package com.farm2home.notification.email;

import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Mirrors {@code com.farm2home.common.core.sms.SmsServiceTest} exactly - EmailService is the
 *  same retry/audit facade shape as SmsService/PushService, just for a different channel. */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock private EmailProvider provider;
    @Mock private AuditLogService auditLogService;

    /** 1ms backoff keeps failure-path tests fast without changing the retry behavior itself. */
    private EmailService newService(int maxAttempts) {
        return new EmailService(provider, auditLogService, maxAttempts, 1);
    }

    @Nested
    @DisplayName("sendEmail()")
    class SendEmail {

        @Test
        @DisplayName("first attempt succeeds → returns success, provider called once, one EMAIL_SENT audit")
        void firstAttemptSucceeds() {
            when(provider.getName()).thenReturn("SMTP");
            when(provider.send(any())).thenReturn(EmailSendResult.success("msg-1"));

            EmailSendResult result = newService(3).sendEmail(
                    "customer@example.com", "Order Confirmed", "<p>body</p>", "ORDER_CREATED");

            assertThat(result.success()).isTrue();
            assertThat(result.providerMessageId()).isEqualTo("msg-1");
            verify(provider, times(1)).send(any());

            ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
            verify(auditLogService, times(1)).record(captor.capture());
            AuditEntry entry = captor.getValue();
            assertThat(entry.getAction()).isEqualTo(AuditAction.EMAIL_SENT);
            assertThat(entry.getEntityType()).isEqualTo("Email");
            assertThat(entry.getEntityId()).isEqualTo("customer@example.com");
            assertThat(entry.isSuccess()).isTrue();
            assertThat(entry.getDetails()).contains("ORDER_CREATED", "SMTP", "1 attempt");
        }

        @Test
        @DisplayName("fails once then succeeds → retries and returns success, only the final outcome is audited")
        void failsThenSucceeds() {
            when(provider.getName()).thenReturn("SMTP");
            when(provider.send(any()))
                    .thenReturn(EmailSendResult.failure("temporary SMTP error"))
                    .thenReturn(EmailSendResult.success("msg-2"));

            EmailSendResult result = newService(3).sendEmail(
                    "customer@example.com", "subject", "body", "PAYMENT_SUCCESS");

            assertThat(result.success()).isTrue();
            verify(provider, times(2)).send(any());
            verify(auditLogService, times(1)).record(any());
        }

        @Test
        @DisplayName("every attempt fails → returns failure after maxAttempts, one EMAIL_FAILED audit")
        void everyAttemptFails() {
            when(provider.getName()).thenReturn("SMTP");
            when(provider.send(any())).thenReturn(EmailSendResult.failure("provider down"));

            EmailSendResult result = newService(3).sendEmail(
                    "customer@example.com", "subject", "body", "DELIVERY_ASSIGNED");

            assertThat(result.success()).isFalse();
            assertThat(result.failureReason()).isEqualTo("provider down");
            verify(provider, times(3)).send(any());

            ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
            verify(auditLogService, times(1)).record(captor.capture());
            AuditEntry entry = captor.getValue();
            assertThat(entry.getAction()).isEqualTo(AuditAction.EMAIL_FAILED);
            assertThat(entry.isSuccess()).isFalse();
            assertThat(entry.getFailureReason()).isEqualTo("provider down");
            assertThat(entry.getDetails()).contains("3 attempt");
        }

        @Test
        @DisplayName("provider throws instead of returning a result → treated as a failure, retried")
        void providerThrows_treatedAsFailure() {
            when(provider.getName()).thenReturn("SMTP");
            when(provider.send(any())).thenThrow(new RuntimeException("connection refused"));

            EmailSendResult result = newService(2).sendEmail(
                    "customer@example.com", "subject", "body", "OTP");

            assertThat(result.success()).isFalse();
            assertThat(result.failureReason()).isEqualTo("connection refused");
            verify(provider, times(2)).send(any());
        }

        @Test
        @DisplayName("maxAttempts=1 → no retries even on failure")
        void singleAttempt_noRetries() {
            when(provider.getName()).thenReturn("SMTP");
            when(provider.send(any())).thenReturn(EmailSendResult.failure("down"));

            newService(1).sendEmail("customer@example.com", "subject", "body", "OTP");

            verify(provider, times(1)).send(any());
        }

        @Test
        @DisplayName("never throws, even when the provider always fails")
        void neverThrows() {
            when(provider.getName()).thenReturn("SMTP");
            when(provider.send(any())).thenThrow(new RuntimeException("boom"));

            org.assertj.core.api.Assertions.assertThatNoException()
                    .isThrownBy(() -> newService(2).sendEmail("customer@example.com", "subject", "body", "OTP"));
        }
    }
}
