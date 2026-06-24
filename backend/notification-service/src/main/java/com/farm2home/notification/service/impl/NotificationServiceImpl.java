package com.farm2home.notification.service.impl;

import com.farm2home.notification.domain.entity.NotificationLog;
import com.farm2home.notification.domain.entity.NotificationTemplate;
import com.farm2home.notification.domain.enums.NotificationChannel;
import com.farm2home.notification.domain.enums.NotificationStatus;
import com.farm2home.notification.domain.repository.NotificationLogRepository;
import com.farm2home.notification.domain.repository.NotificationTemplateRepository;
import com.farm2home.notification.dto.KafkaEventDto;
import com.farm2home.notification.dto.response.NotificationLogResponse;
import com.farm2home.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
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

    @Transactional
    public void process(KafkaEventDto event) {
        if (event.getCustomerId() == null || event.getEventType() == null) {
            log.warn("Skipping notification: missing customerId or eventType");
            return;
        }

        // Try SMS first, then EMAIL
        sendForChannel(event, NotificationChannel.SMS,
                event.getEventType() + "_SMS",
                event.getRecipientMobile());

        if (event.getRecipientEmail() != null) {
            sendForChannel(event, NotificationChannel.EMAIL,
                    event.getEventType() + "_EMAIL",
                    event.getRecipientEmail());
        }
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

        try {
            dispatch(channel, recipient, renderedSubject, renderedBody);
            notifLog.setStatus(NotificationStatus.SENT);
            notifLog.setSentAt(LocalDateTime.now());
        } catch (Exception ex) {
            log.error("Failed to send {} notification to {}: {}", channel, recipient, ex.getMessage());
            notifLog.setStatus(NotificationStatus.FAILED);
            notifLog.setFailureReason(ex.getMessage());
        }

        logRepository.save(notifLog);
    }

    private void dispatch(NotificationChannel channel, String recipient,
                          String subject, String body) {
        switch (channel) {
            case SMS  -> log.info("[SMS] To: {} | Message: {}", recipient, body);
            case EMAIL -> sendEmail(recipient, subject, body);
            case PUSH  -> log.info("[PUSH] To: {} | Message: {}", recipient, body);
        }
    }

    private void sendEmail(String to, String subject, String body) {
        if (mailSender.isEmpty()) {
            log.warn("Mail sender not configured; skipping email to {}", to);
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject(subject != null ? subject : "Farm2Home Notification");
        msg.setText(body);
        mailSender.get().send(msg);
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
}
