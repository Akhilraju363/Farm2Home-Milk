package com.farm2home.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Development-only provider: logs the email instead of actually sending it - active whenever
 * {@code email.enabled=false} (the default; see {@code EmailConfig}), and used by every automated
 * test, so nothing requires real SMTP credentials to run locally or in CI. Always succeeds, so
 * local dev/tests never have to deal with simulated delivery failures - to exercise {@link
 * EmailService}'s retry/failure path in a test, provide a different {@link EmailProvider}
 * stub/mock instead of relying on this one. Mirrors {@code
 * com.farm2home.common.core.sms.LoggingSmsProvider}.
 */
public class LoggingEmailProvider implements EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailProvider.class);

    @Override
    public String getName() {
        return "LOGGING";
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        log.info("[EMAIL] To: {} | Event: {} | Subject: {}", message.to(), message.eventType(), message.subject());
        return EmailSendResult.success("logged-" + UUID.randomUUID());
    }
}
