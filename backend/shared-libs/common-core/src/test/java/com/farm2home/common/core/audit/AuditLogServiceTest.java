package com.farm2home.common.core.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * No Spring context here, so @Async/@Transactional on AuditPersister are inert - persist() runs
 * synchronously on the calling thread, which is exactly what makes it possible to assert against
 * repository.save(...) without any test infrastructure for async completion.
 */
class AuditLogServiceTest {

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final AuditLogService service = new AuditLogService(repository, "test-service");

    @AfterEach
    void cleanup() {
        MDC.clear();
        RequestContextHolder.resetRequestAttributes();
    }

    @Nested
    @DisplayName("record(AuditEntry)")
    class RecordEntry {

        @Test
        @DisplayName("enriches occurredAt, serviceName, correlationId, and defaults success=true")
        void enrichesCommonFields() {
            MDC.put("correlationId", "corr-123");

            service.record(AuditEntry.builder()
                    .action(AuditAction.CREATE)
                    .entityType("Widget")
                    .entityId("widget-1")
                    .build());

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(repository).save(captor.capture());
            AuditLog saved = captor.getValue();

            assertThat(saved.getAction()).isEqualTo(AuditAction.CREATE);
            assertThat(saved.getEntityType()).isEqualTo("Widget");
            assertThat(saved.getEntityId()).isEqualTo("widget-1");
            assertThat(saved.getServiceName()).isEqualTo("test-service");
            assertThat(saved.getCorrelationId()).isEqualTo("corr-123");
            assertThat(saved.getOccurredAt()).isNotNull();
            assertThat(saved.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("no HTTP request bound to thread → ip/uri/method left null, no exception")
        void noRequestContext_fieldsNull() {
            service.record(AuditEntry.builder()
                    .action(AuditAction.UPDATE)
                    .entityType("Widget")
                    .entityId("widget-2")
                    .build());

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getIpAddress()).isNull();
            assertThat(captor.getValue().getRequestUri()).isNull();
            assertThat(captor.getValue().getHttpMethod()).isNull();
        }

        @Test
        @DisplayName("HTTP request bound to thread → ip/uri/method and gateway identity headers populated")
        void withRequestContext_populatesHttpFields() {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/widgets");
            request.setRemoteAddr("10.0.0.5");
            request.addHeader("X-User-Id", "user-42");
            request.addHeader("X-User-Mobile", "9876543210");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            service.record(AuditEntry.builder()
                    .action(AuditAction.CREATE)
                    .entityType("Widget")
                    .entityId("widget-3")
                    .build());

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(repository).save(captor.capture());
            AuditLog saved = captor.getValue();
            assertThat(saved.getIpAddress()).isEqualTo("10.0.0.5");
            assertThat(saved.getRequestUri()).isEqualTo("/api/v1/widgets");
            assertThat(saved.getHttpMethod()).isEqualTo("POST");
            assertThat(saved.getUserId()).isEqualTo("user-42");
            assertThat(saved.getUsername()).isEqualTo("9876543210");
            assertThat(saved.getPerformedBy()).isEqualTo("9876543210");
        }

        @Test
        @DisplayName("explicit userId/username on the entry win over gateway headers")
        void explicitIdentityOverridesHeaders() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("X-User-Id", "header-user");
            request.addHeader("X-User-Mobile", "header-mobile");
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

            service.record(AuditEntry.builder()
                    .action(AuditAction.CREATE)
                    .entityType("Widget")
                    .entityId("widget-4")
                    .userId("explicit-user")
                    .username("explicit-name")
                    .build());

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().getUserId()).isEqualTo("explicit-user");
            assertThat(captor.getValue().getUsername()).isEqualTo("explicit-name");
        }

        @Test
        @DisplayName("failure entry carries success=false and failureReason through")
        void failureEntry_persisted() {
            service.record(AuditEntry.builder()
                    .action(AuditAction.UPDATE)
                    .entityType("Widget")
                    .entityId("widget-5")
                    .success(false)
                    .failureReason("boom")
                    .build());

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(repository).save(captor.capture());
            assertThat(captor.getValue().isSuccess()).isFalse();
            assertThat(captor.getValue().getFailureReason()).isEqualTo("boom");
        }

        @Test
        @DisplayName("repository.save throws → exception is swallowed, business flow is never affected")
        void persistenceFailure_isSwallowed() {
            when(repository.save(any())).thenThrow(new RuntimeException("db down"));

            service.record(AuditEntry.builder()
                    .action(AuditAction.CREATE)
                    .entityType("Widget")
                    .entityId("widget-6")
                    .build());

            verify(repository).save(any());
        }
    }

    @Nested
    @DisplayName("record(action, entityType, entityId, performedBy, details) legacy overload")
    class LegacyRecord {

        @Test
        @DisplayName("delegates to record(AuditEntry), mapping performedBy to username/performedBy")
        void delegatesToRichOverload() {
            service.record(AuditAction.LOGIN, "User", "user-1", "9999999999", "User logged in");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(repository).save(captor.capture());
            AuditLog saved = captor.getValue();
            assertThat(saved.getAction()).isEqualTo(AuditAction.LOGIN);
            assertThat(saved.getEntityType()).isEqualTo("User");
            assertThat(saved.getEntityId()).isEqualTo("user-1");
            assertThat(saved.getUsername()).isEqualTo("9999999999");
            assertThat(saved.getPerformedBy()).isEqualTo("9999999999");
            assertThat(saved.getDetails()).isEqualTo("User logged in");
            assertThat(saved.isSuccess()).isTrue();
        }
    }
}
