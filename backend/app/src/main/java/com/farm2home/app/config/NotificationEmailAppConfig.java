package com.farm2home.app.config;

import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.notification.email.EmailProperties;
import com.farm2home.notification.email.EmailProvider;
import com.farm2home.notification.email.EmailService;
import com.farm2home.notification.email.LoggingEmailProvider;
import com.farm2home.notification.email.SmtpEmailProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;

/**
 * App-side replacement for notification-service's excluded {@code config.EmailConfig}.
 *
 * <p>{@code NotificationServiceImpl} requires an {@link EmailService}; that (and {@link EmailProvider})
 * are wired by the original {@code EmailConfig}, which lives in the never-scanned
 * {@code notification.config} package. This replicates it exactly — same selection semantics:
 *
 * <ul>
 *   <li>{@code email.enabled=false} <b>(the default)</b> always selects
 *       {@link LoggingEmailProvider} regardless of {@code email.provider} — <b>no SMTP
 *       connection, no credentials</b>. Every test and the default deployment use this.</li>
 *   <li>{@code email.enabled=true} + {@code email.provider=smtp} → {@link SmtpEmailProvider},
 *       which needs a {@link JavaMailSender}. Boot only auto-configures that when
 *       {@code spring.mail.host} is set (via the environment, never in source) — the app
 *       {@code application.yml} does not set it, so even a mis-set {@code email.enabled=true}
 *       degrades to logging rather than failing.</li>
 * </ul>
 *
 * <p>{@code SmsService}/{@code PushService} (common-core auto-configuration) already default to
 * their logging providers and have been active since Group 1 — unchanged.
 */
@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class NotificationEmailAppConfig {

    @Value("${app.mail.from}")
    private String mailFrom;

    @Bean
    EmailProvider emailProvider(Optional<JavaMailSender> mailSender, EmailProperties properties) {
        if (properties.isEnabled() && "smtp".equalsIgnoreCase(properties.getProvider())) {
            return new SmtpEmailProvider(mailSender, mailFrom, properties.getFromName());
        }
        return new LoggingEmailProvider();
    }

    @Bean
    EmailService emailService(EmailProvider emailProvider, AuditLogService auditLogService,
                              EmailProperties properties) {
        return new EmailService(emailProvider, auditLogService,
                properties.getMaxAttempts(), properties.getRetryBackoffMillis());
    }
}
