package com.farm2home.common.core.push;

import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single entry point every service uses to send a push notification - wraps the active
 * {@link PushProvider} with bounded retries, graceful failure handling, and an audit trail,
 * mirroring {@code SmsService} exactly.
 *
 * {@link #sendPush} never throws: a failure (even after every retry is exhausted) is reported
 * via the returned {@link PushSendResult} and an audit entry, not an exception - a transient push
 * gateway outage must never fail the business operation that triggered it. Callers that need to
 * react to a failed send (e.g. notification-service marking its own delivery log FAILED) inspect
 * the returned result explicitly.
 */
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final PushProvider provider;
    private final AuditLogService auditLogService;
    private final int maxAttempts;
    private final long retryBackoffMillis;

    public PushService(PushProvider provider, AuditLogService auditLogService, int maxAttempts, long retryBackoffMillis) {
        this.provider = provider;
        this.auditLogService = auditLogService;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMillis = Math.max(0, retryBackoffMillis);
    }

    public PushSendResult sendPush(String recipient, String title, String body, String eventType) {
        PushMessage message = new PushMessage(recipient, title, body, eventType);
        PushSendResult result = PushSendResult.failure("No attempts made");
        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                result = provider.send(message);
            } catch (Exception ex) {
                result = PushSendResult.failure(ex.getMessage());
            }

            if (result.success()) {
                break;
            }
            log.warn("Push send attempt {}/{} to {} for event {} failed: {}",
                    attempt, maxAttempts, recipient, eventType, result.failureReason());
            if (attempt < maxAttempts) {
                backoff(attempt);
            }
        }

        recordAudit(recipient, eventType, result, attempt);
        if (!result.success()) {
            log.error("Push to {} for event {} failed after {} attempt(s): {}",
                    recipient, eventType, attempt, result.failureReason());
        }
        return result;
    }

    private void recordAudit(String recipient, String eventType, PushSendResult result, int attempts) {
        auditLogService.record(AuditEntry.builder()
                .action(result.success() ? AuditAction.PUSH_SENT : AuditAction.PUSH_FAILED)
                .entityType("Push")
                .entityId(recipient)
                .username(recipient)
                .success(result.success())
                .failureReason(result.success() ? null : result.failureReason())
                .details("Push for event " + eventType + " via " + provider.getName()
                        + " (" + attempts + " attempt(s))")
                .build());
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(retryBackoffMillis * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
