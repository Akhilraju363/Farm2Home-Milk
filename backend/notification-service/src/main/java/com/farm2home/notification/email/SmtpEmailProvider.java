package com.farm2home.notification.email;

import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.Optional;

/**
 * Real email transport: sends via Spring's {@link JavaMailSender}, itself auto-configured from
 * {@code spring.mail.*} (MAIL_HOST/MAIL_PORT/MAIL_USERNAME/MAIL_PASSWORD - see
 * application.yml/application-docker.yml; defaults point at Mailtrap's sandbox host for safe dev
 * testing). Active only when {@code EMAIL_ENABLED=true} (see {@code EmailConfig}) - this class is
 * never instantiated at all otherwise, so an unconfigured/misconfigured SMTP host never affects a
 * deployment that intentionally runs with email disabled.
 *
 * Every failure path returns a failed {@link EmailSendResult} rather than throwing, exactly like
 * {@code RazorpayPaymentGatewayProvider} does for gateway errors - {@link EmailService} treats a
 * failed result and a thrown exception identically (both trigger a retry), so this is purely a
 * cleanliness choice: the provider layer never needs a try/catch at its call site.
 */
public class SmtpEmailProvider implements EmailProvider {

    private final Optional<JavaMailSender> mailSender;
    private final String fromAddress;
    private final String fromName;

    public SmtpEmailProvider(Optional<JavaMailSender> mailSender, String fromAddress, String fromName) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public String getName() {
        return "SMTP";
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        if (mailSender.isEmpty()) {
            return EmailSendResult.failure("Mail sender not configured");
        }
        try {
            MimeMessage mimeMessage = mailSender.get().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(message.to());
            helper.setSubject(message.subject() != null ? message.subject() : "Farm2Home Notification");
            helper.setText(message.body(), true);
            mailSender.get().send(mimeMessage);
            return EmailSendResult.success(mimeMessage.getMessageID());
        } catch (Exception ex) {
            return EmailSendResult.failure(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
        }
    }
}
