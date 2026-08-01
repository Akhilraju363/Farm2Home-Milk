package com.farm2home.notification.service.impl;

import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.common.core.constants.EmailTemplateConstants;
import com.farm2home.common.core.dashboard.NotificationSummaryItem;
import com.farm2home.common.core.push.PushSendResult;
import com.farm2home.common.core.push.PushService;
import com.farm2home.common.core.sms.SmsSendResult;
import com.farm2home.common.core.sms.SmsService;
import com.farm2home.notification.domain.entity.NotificationLog;
import com.farm2home.notification.domain.entity.NotificationTemplate;
import com.farm2home.notification.domain.enums.NotificationChannel;
import com.farm2home.notification.domain.enums.NotificationStatus;
import com.farm2home.notification.domain.repository.NotificationLogRepository;
import com.farm2home.notification.domain.repository.NotificationTemplateRepository;
import com.farm2home.notification.dto.KafkaEventDto;
import com.farm2home.notification.dto.response.NotificationLogResponse;
import com.farm2home.notification.mapper.NotificationMapper;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl {

    private final NotificationLogRepository logRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationMapper mapper;
    private final Optional<JavaMailSender> mailSender;
    private final AuditLogService auditLogService;
    private final SmsService smsService;
    private final PushService pushService;

    @Value("${app.mail.from}")
    private String mailFrom;

    @Transactional
    public void process(KafkaEventDto event) {
        if (event.getEventType() == null) {
            log.warn("Skipping notification: missing eventType");
            return;
        }

        // Ops alert, not a customer notification - no customerId involved, so it must be
        // handled before the customerId check below rather than through the SMS/EMAIL
        // template pipeline.
        if (EmailTemplateConstants.EVENT_INVENTORY_UPDATED.equals(event.getEventType())) {
            if (Boolean.TRUE.equals(event.getLowStock())) {
                log.warn("[LOW STOCK ALERT] {} — quantity: {}", event.getItemName(), event.getQuantity());
            }
            return;
        }

        if (event.getCustomerId() == null) {
            log.warn("Skipping notification: missing customerId");
            return;
        }

        // Try SMS first, then EMAIL, then PUSH
        sendForChannel(event, NotificationChannel.SMS,
                event.getEventType() + EmailTemplateConstants.SMS_SUFFIX,
                event.getRecipientMobile());

        if (event.getRecipientEmail() != null) {
            sendForChannel(event, NotificationChannel.EMAIL,
                    event.getEventType() + EmailTemplateConstants.EMAIL_SUFFIX,
                    event.getRecipientEmail());
        }

        // Push has no device-token registry in this codebase (see PushService/dispatch() below) -
        // addressed by customerId instead, which is always present here (checked above), unlike
        // recipientMobile/recipientEmail which depend on what the producing service happened to
        // include on the event.
        sendForChannel(event, NotificationChannel.PUSH,
                event.getEventType() + EmailTemplateConstants.PUSH_SUFFIX,
                event.getCustomerId().toString());
    }

    private void sendForChannel(KafkaEventDto event, NotificationChannel channel,
                                 String templateCode, String recipient) {
        if (recipient == null) return;

        Optional<NotificationTemplate> templateOpt =
                templateRepository.findByTemplateCodeAndActiveTrue(templateCode);

        if (templateOpt.isEmpty()) {
            log.debug("No active template found for code: {}", templateCode);
            return;
        }

        NotificationTemplate template = templateOpt.get();
        String renderedBody = render(template.getBody(), buildPayload(event));
        String renderedSubject = template.getSubject() != null
                ? render(template.getSubject(), buildPayload(event)) : null;

        NotificationLog notifLog = NotificationLog.builder()
                .recipientId(event.getCustomerId())
                .channel(channel)
                .eventType(event.getEventType())
                .recipient(recipient)
                .subject(renderedSubject)
                .message(renderedBody)
                .status(NotificationStatus.PENDING)
                .build();

        String failureReason = null;
        try {
            dispatch(channel, event, recipient, renderedSubject, renderedBody);
            notifLog.setStatus(NotificationStatus.SENT);
            notifLog.setSentAt(LocalDateTime.now());
        } catch (Exception ex) {
            log.error("Failed to send {} notification to {}: {}", channel, recipient, ex.getMessage());
            notifLog.setStatus(NotificationStatus.FAILED);
            notifLog.setFailureReason(ex.getMessage());
            failureReason = ex.getMessage();
        }

        NotificationLog saved = logRepository.save(notifLog);

        // SMS and PUSH sends are already audited by SmsService/PushService themselves
        // (entityType "Sms"/"Push", action *_SENT/*_FAILED) - recording it again here under the
        // Notification entity would just duplicate the same information under a different name.
        // EMAIL has no such provider-level abstraction yet, so it's still audited here.
        if (channel == NotificationChannel.EMAIL) {
            auditLogService.record(AuditEntry.builder()
                    .action(AuditAction.EMAIL_SENT)
                    .entityType("Notification")
                    .entityId(saved.getId() != null ? saved.getId().toString() : null)
                    .username(recipient)
                    .success(saved.getStatus() == NotificationStatus.SENT)
                    .failureReason(failureReason)
                    .details(channel + " notification for event " + event.getEventType())
                    .build());
        }
    }

    private void dispatch(NotificationChannel channel, KafkaEventDto event, String recipient,
                          String subject, String body) throws Exception {
        switch (channel) {
            case SMS -> {
                SmsSendResult result = smsService.sendSms(recipient, body, event.getEventType());
                if (!result.success()) {
                    throw new IllegalStateException(
                            result.failureReason() != null ? result.failureReason() : "SMS delivery failed");
                }
            }
            case EMAIL -> sendEmail(recipient, subject, body);
            case PUSH -> {
                PushSendResult result = pushService.sendPush(recipient, subject, body, event.getEventType());
                if (!result.success()) {
                    throw new IllegalStateException(
                            result.failureReason() != null ? result.failureReason() : "Push delivery failed");
                }
            }
        }
    }

    private void sendEmail(String to, String subject, String body) throws Exception {
        if (mailSender.isEmpty()) {
            log.warn("Mail sender not configured; skipping email to {}", to);
            return;
        }
        MimeMessage message = mailSender.get().createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(mailFrom);
        helper.setTo(to);
        helper.setSubject(subject != null ? subject : "Farm2Home Notification");
        helper.setText(body, true);
        mailSender.get().send(message);
    }

    private String render(String template, Map<String, String> payload) {
        String result = template;
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (entry.getValue() != null) {
                result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }
        return result;
    }

    private Map<String, String> buildPayload(KafkaEventDto event) {
        Map<String, String> payload = new HashMap<>();
        if (event.getOrderNumber()      != null) payload.put("order_number",   event.getOrderNumber());
        if (event.getAmount()           != null) payload.put("amount",         event.getAmount());
        if (event.getPartnerName()      != null) payload.put("partner_name",   event.getPartnerName());
        if (event.getExpectedTime()     != null) payload.put("expected_time",  event.getExpectedTime());
        if (event.getPaymentReference() != null) payload.put("reference",      event.getPaymentReference());
        if (event.getCustomerName()     != null) payload.put("customer_name",  event.getCustomerName());
        if (event.getQuantity()         != null) payload.put("quantity",       event.getQuantity());
        if (event.getMilkType()         != null) payload.put("milk_type",      event.getMilkType());
        if (event.getOtp()              != null) payload.put("otp",            event.getOtp());
        if (event.getFailureReason()    != null) payload.put("reason",         event.getFailureReason());
        if (event.getExtra()            != null) payload.putAll(event.getExtra());
        return payload;
    }

    @Transactional(readOnly = true)
    public Page<NotificationLogResponse> findByRecipient(UUID recipientId, Pageable pageable) {
        return logRepository.findAllByRecipientIdOrderByCreatedAtDesc(recipientId, pageable)
                .map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public NotificationLogResponse findById(UUID id) {
        return mapper.toResponse(logRepository.findById(id)
                .orElseThrow(() -> new com.farm2home.notification.exception.ResourceNotFoundException(
                        "Notification log not found: " + id)));
    }

    @Transactional(readOnly = true)
    public List<NotificationSummaryItem> getRecent(int limit) {
        return logRepository.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream()
                .map(entry -> NotificationSummaryItem.builder()
                        .id(entry.getId())
                        .channel(entry.getChannel().name())
                        .recipient(entry.getRecipient())
                        .subject(entry.getSubject())
                        .status(entry.getStatus().name())
                        .createdAt(entry.getCreatedAt())
                        .build())
                .toList();
    }
}
