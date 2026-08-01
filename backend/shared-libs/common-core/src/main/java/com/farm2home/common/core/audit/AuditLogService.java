package com.farm2home.common.core.audit;

import com.farm2home.observability.web.RequestTraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.time.OffsetDateTime;

/**
 * Single entry point every service uses to write an audit row. Enrichment (correlation id from
 * MDC, service name, and HTTP-derived fields) happens here, synchronously, on the caller's
 * thread - RequestContextHolder/MDC are thread-local and would read as empty if deferred onto
 * the async persistence thread. The actual write is handed off to AuditPersister, which runs
 * asynchronously in its own transaction and never lets a persistence failure escape.
 */
public class AuditLogService {

    private final AuditPersister persister;
    private final String serviceName;

    public AuditLogService(AuditLogRepository repository, String serviceName) {
        this.persister = new AuditPersister(repository);
        this.serviceName = serviceName;
    }

    /** Primary entry point: full control over every audit field. */
    public void record(AuditEntry entry) {
        HttpServletRequest request = AuditHttpContext.currentRequest();
        String userId = entry.getUserId() != null ? entry.getUserId() : AuditHttpContext.userId(request);
        String username = entry.getUsername() != null ? entry.getUsername() : AuditHttpContext.username(request);

        AuditLog log = AuditLog.builder()
                .occurredAt(OffsetDateTime.now())
                .action(entry.getAction())
                .entityType(entry.getEntityType())
                .entityId(entry.getEntityId())
                .userId(userId)
                .username(username)
                .performedBy(username != null ? username : "system")
                .serviceName(serviceName)
                .correlationId(MDC.get(RequestTraceIdFilter.MDC_CORRELATION_ID))
                .oldValue(entry.getOldValue())
                .newValue(entry.getNewValue())
                .details(entry.getDetails())
                .ipAddress(AuditHttpContext.ipAddress(request))
                .requestUri(request != null ? request.getRequestURI() : null)
                .httpMethod(request != null ? request.getMethod() : null)
                .success(entry.isSuccess())
                .failureReason(entry.getFailureReason())
                .build();

        persister.persist(log);
    }

    /**
     * Legacy convenience signature kept for simple fire-and-forget events (e.g. LOGIN, where the
     * acting user is known explicitly and isn't yet resolvable from gateway headers). Delegates
     * to {@link #record(AuditEntry)} - there is exactly one write path.
     */
    public void record(String action, String entityType, String entityId, String performedBy, String details) {
        record(AuditEntry.builder()
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .username(performedBy)
                .details(details)
                .success(true)
                .build());
    }
}
