package com.farm2home.common.core.sms;

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

@ExtendWith(MockitoExtension.class)
class SmsServiceTest {

    @Mock private SmsProvider provider;
    @Mock private AuditLogService auditLogService;

    /** 1ms backoff keeps failure-path tests fast without changing the retry behavior itself. */
    private SmsService newService(int maxAttempts) {
        return new SmsService(provider, auditLogService, maxAttempts, 1);
    }

    @Nested
    @DisplayName("sendSms()")
    class SendSms {

        @Test
        @DisplayName("first attempt succeeds → returns success, provider called once, one SMS_SENT audit")
        void firstAttemptSucceeds() {
            when(provider.getName()).thenReturn("LOGGING");
            when(provider.send(any())).thenReturn(SmsSendResult.success("msg-1"));

            SmsSendResult result = newService(3).sendSms("9876543210", "Your OTP is 123456", "OTP");

            assertThat(result.success()).isTrue();
            assertThat(result.providerMessageId()).isEqualTo("msg-1");
            verify(provider, times(1)).send(any());

            ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
            verify(auditLogService, times(1)).record(captor.capture());
            AuditEntry entry = captor.getValue();
            assertThat(entry.getAction()).isEqualTo(AuditAction.SMS_SENT);
            assertThat(entry.getEntityType()).isEqualTo("Sms");
            assertThat(entry.getEntityId()).isEqualTo("9876543210");
            assertThat(entry.isSuccess()).isTrue();
            assertThat(entry.getDetails()).contains("OTP", "LOGGING", "1 attempt");
        }

        @Test
        @DisplayName("fails once then succeeds → retries and returns success, only the final outcome is audited")
        void failsThenSucceeds() {
            when(provider.getName()).thenReturn("LOGGING");
            when(provider.send(any()))
                    .thenReturn(SmsSendResult.failure("temporary gateway error"))
                    .thenReturn(SmsSendResult.success("msg-2"));

            SmsSendResult result = newService(3).sendSms("9876543210", "body", "ORDER_CREATED");

            assertThat(result.success()).isTrue();
            verify(provider, times(2)).send(any());
            verify(auditLogService, times(1)).record(any());
        }

        @Test
        @DisplayName("every attempt fails → returns failure after maxAttempts, one SMS_FAILED audit")
        void everyAttemptFails() {
            when(provider.getName()).thenReturn("LOGGING");
            when(provider.send(any())).thenReturn(SmsSendResult.failure("provider down"));

            SmsSendResult result = newService(3).sendSms("9876543210", "body", "PAYMENT_SUCCESS");

            assertThat(result.success()).isFalse();
            assertThat(result.failureReason()).isEqualTo("provider down");
            verify(provider, times(3)).send(any());

            ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
            verify(auditLogService, times(1)).record(captor.capture());
            AuditEntry entry = captor.getValue();
            assertThat(entry.getAction()).isEqualTo(AuditAction.SMS_FAILED);
            assertThat(entry.isSuccess()).isFalse();
            assertThat(entry.getFailureReason()).isEqualTo("provider down");
            assertThat(entry.getDetails()).contains("3 attempt");
        }

        @Test
        @DisplayName("provider throws instead of returning a result → treated as a failure, retried")
        void providerThrows_treatedAsFailure() {
            when(provider.getName()).thenReturn("LOGGING");
            when(provider.send(any())).thenThrow(new RuntimeException("connection refused"));

            SmsSendResult result = newService(2).sendSms("9876543210", "body", "DELIVERY_ASSIGNED");

            assertThat(result.success()).isFalse();
            assertThat(result.failureReason()).isEqualTo("connection refused");
            verify(provider, times(2)).send(any());
        }

        @Test
        @DisplayName("maxAttempts=1 → no retries even on failure")
        void singleAttempt_noRetries() {
            when(provider.getName()).thenReturn("LOGGING");
            when(provider.send(any())).thenReturn(SmsSendResult.failure("down"));

            newService(1).sendSms("9876543210", "body", "OTP");

            verify(provider, times(1)).send(any());
        }

        @Test
        @DisplayName("never throws, even when the provider always fails")
        void neverThrows() {
            when(provider.getName()).thenReturn("LOGGING");
            when(provider.send(any())).thenThrow(new RuntimeException("boom"));

            org.assertj.core.api.Assertions.assertThatNoException()
                    .isThrownBy(() -> newService(2).sendSms("9876543210", "body", "OTP"));
        }
    }
}
