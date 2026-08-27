package com.farm2home.notification.email;

/**
 * Provider abstraction over a real email transport (SMTP today; a transactional-email API such
 * as SendGrid/SES could implement this the same way later) so {@link EmailService} - and
 * everything upstream of it - never depends on a specific vendor's SDK or API shape.
 *
 * Unlike {@code SmsProvider}/{@code PushProvider} (which live in common-core because auth-service
 * and notification-service both send SMS), email is only ever sent by notification-service today,
 * so this abstraction stays local here - the same placement {@code payment-service} uses for its
 * own {@code PaymentGatewayProvider} (Razorpay/mock), which only payment-service needs.
 *
 * Exactly one implementation is active per environment, selected by {@code email.enabled} and
 * {@code email.provider} (see {@code EmailConfig}): {@link LoggingEmailProvider} (default - safe
 * for local dev/CI, no SMTP credentials required, matches {@code sms.provider}/{@code
 * push.provider}'s "logging by default" convention) or {@link SmtpEmailProvider} (real send via
 * Spring's {@code JavaMailSender}, active only when {@code EMAIL_ENABLED=true}).
 */
public interface EmailProvider {

    /** Identifies the active provider for diagnostics/audit trails (e.g. "LOGGING", "SMTP"). */
    String getName();

    /** Sends a single email. Implementations should return a failed {@link EmailSendResult} for
     *  provider-reported failures rather than throwing where possible; {@link EmailService}
     *  treats a thrown exception and a failed result identically (both trigger a retry), so
     *  either is safe to use. */
    EmailSendResult send(EmailMessage message);
}
