package com.farm2home.common.core.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Split out from AuditLogService purely so @Async and @Transactional actually apply: both are
 * proxy-based, and a bean invoking its own @Async method (self-invocation) bypasses the proxy
 * entirely. AuditLogService builds the fully-enriched AuditLog on the caller's thread (where MDC
 * and the request context are still available), then hands it to this bean across a real proxy
 * boundary to persist off-thread.
 *
 * Failures here are caught and logged to a dedicated logger rather than propagated - losing an
 * audit row must never fail, retry, or roll back the business operation it's describing.
 */
class AuditPersister {

    private static final Logger auditFailureLog = LoggerFactory.getLogger("AUDIT_FAILURES");

    private final AuditLogRepository repository;

    AuditPersister(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Async("auditTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void persist(AuditLog entry) {
        try {
            repository.save(entry);
        } catch (Exception ex) {
            auditFailureLog.error("Failed to persist audit log entry: action={} entityType={} entityId={}",
                    entry.getAction(), entry.getEntityType(), entry.getEntityId(), ex);
        }
    }
}
