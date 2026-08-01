package com.farm2home.common.core.push;

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
class PushServiceTest {

    @Mock private PushProvider provider;
    @Mock private AuditLogService auditLogService;

    /** 1ms backoff keeps failure-path tests fast without changing the retry behavior itself. */
    private PushService newService(int maxAttempts) {
        return new PushService(provider, auditLogService, maxAttempts, 1);
    }

    @Nested
    @DisplayName("sendPush()")
    class SendPush {

        @Test
        @DisplayName("first attempt succeeds → returns success, provider called once, one PUSH_SENT audit")
        void firstAttemptSucceeds() {
            when(provider.getName()).thenReturn("LOGGING");
            when(provider.send(any())).thenReturn(PushSendResult.success("msg-1"));

            PushSendResult result = newService(3).sendPush("customer-1", "Order Confirmed", "body", "ORDER_CREATED");

            assertThat(result.success()).isTrue();
            assertThat(result.providerMessageId()).isEqualTo("msg-1");
            verify(provider, times(1)).send(any());

            ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
            verify(auditLogService, times(1)).record(captor.capture());
            AuditEntry entry = captor.getValue();
            assertThat(entry.getAction()).isEqualTo(AuditAction.PUSH_SENT);
            assertThat(entry.getEntityType()).isEqualTo("Push");
            assertThat(entry.getEntityId()).isEqualTo("customer-1");
            assertThat(entry.isSuccess()).isTrue();
            assertThat(entry.getDetails()).contains("ORDER_CREATED", "LOGGING", "1 attempt");
        }

        @Test
        @DisplayName("fails once then succeeds → retries and returns success, only the final outcome is audited")
        void failsThenSucceeds() {
            when(provider.getName()).thenReturn("LOGGING");
            when(provider.send(any()))
                    .thenReturn(PushSendResult.failure("temporary gateway error"))
                    .thenReturn(PushSendResult.success("msg-2"));

            PushSendResult result = newService(3).sendPush("customer-1", "title", "body", "DELIVERY_ASSIGNED");

            assertThat(result.success()).isTrue();
            verify(provider, times(2)).send(any());
            verify(auditLogService, times(1)).record(any());
        }

        @Test
        @DisplayName("every attempt fails → returns failure after maxAttempts, one PUSH_FAILED audit")
        void everyAttemptFails() {
            when(provider.getName()).thenReturn("LOGGING");
            when(provider.send(any())).thenReturn(PushSendResult.failure("provider down"));

            PushSendResult result = newService(3).sendPush("customer-1", "title", "body", "PAYMENT_SUCCESS");

            assertThat(result.success()).isFalse();
            assertThat(result.failureReason()).isEqualTo("provider down");
            verify(provider, times(3)).send(any());

            ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
            verify(auditLogService, times(1)).record(captor.capture());
            AuditEntry entry = captor.getValue();
            assertThat(entry.getAction()).isEqualTo(AuditAction.PUSH_FAILED);
            assertThat(entry.isSuccess()).isFalse();
            assertThat(entry.getFailureReason()).isEqualTo("provider down");
            assertThat(entry.getDetails()).contains("3 attempt");
        }

        @Test
        @DisplayName("provider throws instead of returning a result → treated as a failure, retried")
        void providerThrows_treatedAsFailure() {
            when(provider.getName()).thenReturn("LOGGING");
            when(provider.send(any())).thenThrow(new RuntimeException("connection refused"));

            PushSendResult result = newService(2).sendPush("customer-1", "title", "body", "SUBSCRIPTION_PAUSED");

            assertThat(result.success()).isFalse();
            assertThat(result.failureReason()).isEqualTo("connection refused");
            verify(provider, times(2)).send(any());
        }

        @Test
        @DisplayName("never throws, even when the provider always fails")
        void neverThrows() {
            when(provider.getName()).thenReturn("LOGGING");
            when(provider.send(any())).thenThrow(new RuntimeException("boom"));

            org.assertj.core.api.Assertions.assertThatNoException()
                    .isThrownBy(() -> newService(2).sendPush("customer-1", "title", "body", "ORDER_CREATED"));
        }
    }
}
