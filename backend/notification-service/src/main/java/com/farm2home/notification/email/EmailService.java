package com.farm2home.notification.email;

import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single entry point notification-service uses to send an email - wraps the active {@link
 * EmailProvider} with bounded retries, graceful failure handling, and an audit trail. Mirrors
 * {@code com.farm2home.common.core.sms.SmsService} exactly, so EMAIL behaves identically to SMS/
 * PUSH from {@code NotificationServiceImpl}'s point of view.
 *
 * {@link #sendEmail} never throws: a failure (even after every retry is exhausted) is reported via
 * the returned {@link EmailSendResult} and an audit entry, not an exception - a transient SMTP
 * outage must never fail the business operation that triggered it (the order/payment/delivery
 * event that produced this email has already been committed by the time notification-service
 * consumes it from Kafka). Callers that need to react to a failed send (notification-service
 * marking its own delivery log FAILED) inspect the returned result explicitly.
 */
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailProvider provider;
    private final AuditLogService auditLogService;
    private final int maxAttempts;
    private final long retryBackoffMillis;

    public EmailService(EmailProvider provider, AuditLogService auditLogService, int maxAttempts, long retryBackoffMillis) {
        this.provider = provider;
        this.auditLogService = auditLogService;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMillis = Math.max(0, retryBackoffMillis);
    }

    public EmailSendResult sendEmail(String to, String subject, String body, String eventType) {
        EmailMessage message = new EmailMessage(to, subject, body, eventType);
        EmailSendResult result = EmailSendResult.failure("No attempts made");
        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                result = provider.send(message);
            } catch (Exception ex) {
                result = EmailSendResult.failure(ex.getMessage());
            }

            if (result.success()) {
                break;
            }
            log.warn("Email send attempt {}/{} to {} for event {} failed: {}",
                    attempt, maxAttempts, to, eventType, result.failureReason());
            if (attempt < maxAttempts) {
                backoff(attempt);
            }
        }

        recordAudit(to, eventType, result, attempt);
        if (!result.success()) {
            log.error("Email to {} for event {} failed after {} attempt(s): {}",
                    to, eventType, attempt, result.failureReason());
        }
        return result;
    }

    private void recordAudit(String to, String eventType, EmailSendResult result, int attempts) {
        auditLogService.record(AuditEntry.builder()
                .action(result.success() ? AuditAction.EMAIL_SENT : AuditAction.EMAIL_FAILED)
                .entityType("Email")
                .entityId(to)
                .username(to)
                .success(result.success())
                .failureReason(result.success() ? null : result.failureReason())
                .details("Email for event " + eventType + " via " + provider.getName()
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
