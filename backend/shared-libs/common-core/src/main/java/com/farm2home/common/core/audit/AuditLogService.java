package com.farm2home.common.core.audit;

import com.farm2home.observability.web.RequestTraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Thin wrapper over AuditLogRepository so callers write one line instead of hand-building an
 * AuditLog every time, and so the correlation id (already flowing through every request via
 * MDC) ends up on the audit row without every call site having to know where it comes from.
 *
 * REQUIRES_NEW so an audit entry is still written even if the surrounding business transaction
 * later rolls back for an unrelated reason; failures writing the audit row itself are caught
 * and logged rather than propagated, since losing an audit entry should never take down the
 * business operation it's describing.
 */
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(String action, String entityType, String entityId, String performedBy, String details) {
        try {
            saveInNewTransaction(action, entityType, entityId, performedBy, details);
        } catch (Exception ex) {
            log.error("Failed to write audit log entry: action={} entityType={} entityId={}",
                    action, entityType, entityId, ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void saveInNewTransaction(String action, String entityType, String entityId,
            String performedBy, String details) {
        AuditLog entry = AuditLog.builder()
                .occurredAt(OffsetDateTime.now())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .performedBy(performedBy != null ? performedBy : "system")
                .correlationId(MDC.get(RequestTraceIdFilter.MDC_CORRELATION_ID))
                .details(details)
                .build();
        repository.save(entry);
    }
}
