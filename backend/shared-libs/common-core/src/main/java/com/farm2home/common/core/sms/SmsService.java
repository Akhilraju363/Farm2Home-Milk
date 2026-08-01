package com.farm2home.common.core.sms;

import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single entry point every service uses to send an SMS - wraps the active
 * {@link SmsProvider} with bounded retries, graceful failure handling, and an audit trail, so
 * callers (OTP generation, order/delivery/payment notifications) never have to implement any of
 * that themselves and never have to deal with an exception from a flaky SMS gateway.
 *
 * {@link #sendSms} never throws: a failure (even after every retry is exhausted) is reported via
 * the returned {@link SmsSendResult} and an audit entry, not an exception - a transient SMS
 * outage must never fail the business operation that triggered it (an OTP row is still created
 * and usable once entered manually by support; an order is still confirmed even if the SMS
 * receipt didn't go out). Callers that need to react to a failed send (e.g. notification-service
 * marking its own delivery log FAILED) inspect the returned result explicitly.
 */
public class SmsService {

    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    private final SmsProvider provider;
    private final AuditLogService auditLogService;
    private final int maxAttempts;
    private final long retryBackoffMillis;

    public SmsService(SmsProvider provider, AuditLogService auditLogService, int maxAttempts, long retryBackoffMillis) {
        this.provider = provider;
        this.auditLogService = auditLogService;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMillis = Math.max(0, retryBackoffMillis);
    }

    public SmsSendResult sendSms(String to, String body, String eventType) {
        SmsMessage message = new SmsMessage(to, body, eventType);
        SmsSendResult result = SmsSendResult.failure("No attempts made");
        int attempt = 0;

        while (attempt < maxAttempts) {
            attempt++;
            try {
                result = provider.send(message);
            } catch (Exception ex) {
                result = SmsSendResult.failure(ex.getMessage());
            }

            if (result.success()) {
                break;
            }
            log.warn("SMS send attempt {}/{} to {} for event {} failed: {}",
                    attempt, maxAttempts, to, eventType, result.failureReason());
            if (attempt < maxAttempts) {
                backoff(attempt);
            }
        }

        recordAudit(to, eventType, result, attempt);
        if (!result.success()) {
            log.error("SMS to {} for event {} failed after {} attempt(s): {}",
                    to, eventType, attempt, result.failureReason());
        }
        return result;
    }

    private void recordAudit(String to, String eventType, SmsSendResult result, int attempts) {
        auditLogService.record(AuditEntry.builder()
                .action(result.success() ? AuditAction.SMS_SENT : AuditAction.SMS_FAILED)
                .entityType("Sms")
                .entityId(to)
                .username(to)
                .success(result.success())
                .failureReason(result.success() ? null : result.failureReason())
                .details("SMS for event " + eventType + " via " + provider.getName()
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
