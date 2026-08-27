package com.farm2home.notification.config;

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
 * Wires the active {@link EmailProvider} and {@link EmailService} - the email counterpart of
 * {@code SmsAutoConfiguration}/{@code PushAutoConfiguration}, kept local to notification-service
 * (rather than a common-core auto-configuration) because only notification-service depends on
 * {@code spring-boot-starter-mail} today - see {@link EmailProvider}'s javadoc for the full
 * rationale.
 *
 * Provider selection is entirely configuration-driven: {@code email.enabled=false} (the default)
 * always selects {@link LoggingEmailProvider} regardless of {@code email.provider}, so local dev/
 * CI never attempts a real SMTP connection unless {@code EMAIL_ENABLED=true} is set explicitly.
 */
@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfig {

    @Value("${app.mail.from}")
    private String mailFrom;

    @Bean
    public EmailProvider emailProvider(Optional<JavaMailSender> mailSender, EmailProperties properties) {
        if (properties.isEnabled() && "smtp".equalsIgnoreCase(properties.getProvider())) {
            return new SmtpEmailProvider(mailSender, mailFrom, properties.getFromName());
        }
        return new LoggingEmailProvider();
    }

    @Bean
    public EmailService emailService(EmailProvider emailProvider, AuditLogService auditLogService, EmailProperties properties) {
        return new EmailService(emailProvider, auditLogService, properties.getMaxAttempts(), properties.getRetryBackoffMillis());
    }
}
