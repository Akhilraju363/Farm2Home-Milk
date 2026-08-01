package com.farm2home.common.core.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditAspectTest {

    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final AuditAspect aspect = new AuditAspect(auditLogService);

    private Audited audited(String action, String entityType) {
        return new Audited() {
            public String action() { return action; }
            public String entityType() { return entityType; }
            public Class<? extends java.lang.annotation.Annotation> annotationType() { return Audited.class; }
        };
    }

    private record FakeResponse(UUID id) {
        public UUID getId() { return id; }
    }

    @Test
    @DisplayName("success, return value has getId() → entityId resolved from the response")
    void resolvesEntityIdFromReturnValue() throws Throwable {
        UUID id = UUID.randomUUID();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(new FakeResponse(id));
        when(joinPoint.getArgs()).thenReturn(new Object[]{});

        Object result = aspect.around(joinPoint, audited(AuditAction.CREATE, "Widget"));

        assertThat(result).isInstanceOf(FakeResponse.class);
        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditLogService).record(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(captor.getValue().getEntityType()).isEqualTo("Widget");
        assertThat(captor.getValue().getEntityId()).isEqualTo(id.toString());
        assertThat(captor.getValue().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("void return (e.g. delete(UUID)) → entityId falls back to the first UUID argument")
    void resolvesEntityIdFromUuidArg() throws Throwable {
        UUID id = UUID.randomUUID();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn(null);
        when(joinPoint.getArgs()).thenReturn(new Object[]{id});

        aspect.around(joinPoint, audited(AuditAction.DELETE, "Widget"));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditLogService).record(captor.capture());
        assertThat(captor.getValue().getEntityId()).isEqualTo(id.toString());
    }

    @Test
    @DisplayName("target method throws → records a failed audit entry and rethrows unchanged")
    void failure_recordsAndRethrows() throws Throwable {
        UUID id = UUID.randomUUID();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        RuntimeException boom = new RuntimeException("insufficient stock");
        when(joinPoint.proceed()).thenThrow(boom);
        when(joinPoint.getArgs()).thenReturn(new Object[]{id});

        assertThatThrownBy(() -> aspect.around(joinPoint, audited(AuditAction.UPDATE, "Widget")))
                .isSameAs(boom);

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditLogService).record(captor.capture());
        assertThat(captor.getValue().isSuccess()).isFalse();
        assertThat(captor.getValue().getFailureReason()).isEqualTo("insufficient stock");
        assertThat(captor.getValue().getEntityId()).isEqualTo(id.toString());
    }

    @Test
    @DisplayName("neither a resolvable return value nor a UUID argument → entityId is null, no exception")
    void noResolvableId_entityIdNull() throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.proceed()).thenReturn("plain string, no getId()");
        when(joinPoint.getArgs()).thenReturn(new Object[]{"not a uuid"});

        aspect.around(joinPoint, audited(AuditAction.CREATE, "Widget"));

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditLogService).record(captor.capture());
        assertThat(captor.getValue().getEntityId()).isNull();
    }
}
