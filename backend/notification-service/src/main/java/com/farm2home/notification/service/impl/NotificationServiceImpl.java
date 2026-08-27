package com.farm2home.notification.service.impl;

import com.farm2home.common.core.constants.EmailTemplateConstants;
import com.farm2home.common.core.dashboard.NotificationSummaryItem;
import com.farm2home.common.core.push.PushSendResult;
import com.farm2home.common.core.push.PushService;
import com.farm2home.common.core.sms.SmsSendResult;
import com.farm2home.common.core.sms.SmsService;
import com.farm2home.notification.client.CustomerContactDto;
import com.farm2home.notification.client.CustomerServiceClient;
import com.farm2home.notification.domain.entity.NotificationLog;
import com.farm2home.notification.domain.entity.NotificationTemplate;
import com.farm2home.notification.domain.enums.NotificationChannel;
import com.farm2home.notification.domain.enums.NotificationStatus;
import com.farm2home.notification.domain.repository.NotificationLogRepository;
import com.farm2home.notification.domain.repository.NotificationTemplateRepository;
import com.farm2home.notification.dto.KafkaEventDto;
import com.farm2home.notification.dto.response.NotificationLogResponse;
import com.farm2home.notification.email.EmailSendResult;
import com.farm2home.notification.email.EmailService;
import com.farm2home.notification.mapper.NotificationMapper;
import com.farm2home.notification.service.NotificationClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final SmsService smsService;
    private final PushService pushService;
    private final EmailService emailService;
    private final CustomerServiceClient customerServiceClient;

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

        // Order/Payment/Subscription/Delivery events only carry a customerId, not the
        // recipient's actual mobile/email/name (only CustomerEvent/OtpEvent do, since
        // auth-service has that on hand directly) - without this, SMS/EMAIL would silently
        // no-op below (sendForChannel returns early when recipient is null) even though a
        // template exists. Best-effort: a lookup failure just means this event falls back to
        // PUSH-only, exactly like it would have before this enrichment existed.
        if (event.getRecipientMobile() == null || event.getRecipientEmail() == null || event.getCustomerName() == null) {
            enrichFromCustomerService(event);
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

    private void enrichFromCustomerService(KafkaEventDto event) {
        try {
            CustomerContactDto contact = customerServiceClient.getContact(event.getCustomerId())
                    .block(Duration.ofSeconds(3));
            if (contact == null) return;

            if (event.getRecipientMobile() == null) event.setRecipientMobile(contact.getMobile());
            if (event.getRecipientEmail() == null) event.setRecipientEmail(contact.getEmail());
            if (event.getCustomerName() == null) {
                String name = ((contact.getFirstName() != null ? contact.getFirstName() : "")
                        + " " + (contact.getLastName() != null ? contact.getLastName() : "")).trim();
                if (!name.isEmpty()) event.setCustomerName(name);
            }
        } catch (Exception ex) {
            log.warn("Customer contact lookup failed for {}: {}", event.getCustomerId(), ex.getMessage());
        }
    }

    private void sendForChannel(KafkaEventDto event, NotificationChannel channel,
                                 String templateCode, String recipient) {
        if (recipient == null) return;

        // Idempotency: a Kafka redelivery (consumer restart/rebalance before offset commit)
        // would otherwise re-run this whole method and double-send. Keyed on (entity id,
        // occurredAt) together, not entity id alone - a recurring event type for the same entity
        // (a subscription paused, resumed, then paused again; a second DELIVERY_DELAYED ping for
        // the same assignment) is a genuinely new occurrence with a new occurredAt, not a
        // redelivery of the same message, and must still be sent. Only events with an entity id
        // (see KafkaEventDto.getDedupeEntityId()) can be deduped this way; OTP/CUSTOMER_CREATED
        // have none (OTP resends are intentional and must never be blocked; CUSTOMER_CREATED
        // fires exactly once per customer in practice) and are simply not deduped - documented
        // gap, not an oversight. Checked against SENT only, so a previously-FAILED attempt can
        // still be retried by a genuine redelivery.
        UUID dedupeEntityId = event.getDedupeEntityId();
        if (dedupeEntityId != null && event.getOccurredAt() != null
                && logRepository.existsByChannelAndEventTypeAndSourceEventIdAndEventOccurredAtAndStatus(
                        channel, event.getEventType(), dedupeEntityId, event.getOccurredAt(), NotificationStatus.SENT)) {
            log.debug("Skipping duplicate {} notification for event {} ({} @ {}): already sent",
                    channel, event.getEventType(), dedupeEntityId, event.getOccurredAt());
            return;
        }

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
                .sourceEventId(dedupeEntityId)
                .eventOccurredAt(event.getOccurredAt())
                .recipient(recipient)
                .subject(renderedSubject)
                .message(renderedBody)
                .status(NotificationStatus.PENDING)
                .build();

        try {
            dispatch(channel, event, recipient, renderedSubject, renderedBody);
            notifLog.setStatus(NotificationStatus.SENT);
            notifLog.setSentAt(LocalDateTime.now());
        } catch (Exception ex) {
            log.error("Failed to send {} notification to {}: {}", channel, recipient, ex.getMessage());
            notifLog.setStatus(NotificationStatus.FAILED);
            notifLog.setFailureReason(ex.getMessage());
        }

        // SMS/PUSH/EMAIL sends are already audited by SmsService/PushService/EmailService
        // themselves (entityType "Sms"/"Push"/"Email", action *_SENT/*_FAILED) - recording it
        // again here under the Notification entity would just duplicate the same information
        // under a different name.
        try {
            logRepository.save(notifLog);
        } catch (DataIntegrityViolationException ex) {
            // Belt-and-suspenders for the same idempotency case checked above: only reachable if
            // two threads raced past the existsBy... check for the same (channel, eventType,
            // sourceEventId) - the partial unique index on notification_logs is the real
            // guarantee, this check is just the fast path. The send itself already happened by
            // this point (can't be un-sent), so this only prevents a duplicate history row.
            log.warn("Duplicate {} notification log for event {} ({}) - already recorded by a concurrent send",
                    channel, event.getEventType(), event.getSourceEventId());
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
            case EMAIL -> {
                EmailSendResult result = emailService.sendEmail(recipient, subject, body, event.getEventType());
                if (!result.success()) {
                    throw new IllegalStateException(
                            result.failureReason() != null ? result.failureReason() : "Email delivery failed");
                }
            }
            case PUSH -> {
                PushSendResult result = pushService.sendPush(recipient, subject, body, event.getEventType());
                if (!result.success()) {
                    throw new IllegalStateException(
                            result.failureReason() != null ? result.failureReason() : "Push delivery failed");
                }
            }
        }
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
                .map(this::toSummaryItem)
                .toList();
    }

    /** Self-service equivalent of getRecent() above, scoped to one recipient - safe to expose to
     *  any authenticated user (unlike getRecent, which spans every recipient) since it can only
     *  ever return the caller's own notifications; see NotificationLogController.getMyRecent().
     *  Fetches extra rows and collapses same-event fan-out (see collapseSameEvent()) so a caller
     *  asking for `limit` gets `limit` real-world events, not `limit` raw channel rows. OTP is
     *  excluded - it's a transient security code, not a "something happened" notification like
     *  every other event type, and the mockup this screen is built from doesn't show it. */
    @Transactional(readOnly = true)
    public List<NotificationSummaryItem> getMyRecent(UUID recipientId, int limit) {
        List<NotificationLog> raw = logRepository
                .findAllByRecipientIdOrderByCreatedAtDesc(recipientId, PageRequest.of(0, limit * 4))
                .getContent()
                .stream()
                .filter(entry -> !EmailTemplateConstants.EVENT_OTP.equals(entry.getEventType()))
                .toList();
        return collapseSameEvent(raw).stream().map(this::toSummaryItem).limit(limit).toList();
    }

    /** SMS/EMAIL/PUSH sends for the same real-world event all happen within the same process()
     *  call, so their createdAt timestamps land within a few hundred ms of each other - collapse
     *  adjacent same-eventType rows within a short window into one, preferring whichever has a
     *  subject (EMAIL/PUSH) over a bare SMS body, so the notification center shows one card per
     *  event instead of one per channel. */
    private List<NotificationLog> collapseSameEvent(List<NotificationLog> sortedDesc) {
        List<NotificationLog> result = new ArrayList<>();
        for (NotificationLog entry : sortedDesc) {
            NotificationLog last = result.isEmpty() ? null : result.get(result.size() - 1);
            boolean sameEvent = last != null
                    && last.getEventType().equals(entry.getEventType())
                    && Duration.between(entry.getCreatedAt(), last.getCreatedAt()).abs().getSeconds() <= 10;
            if (sameEvent) {
                if (last.getSubject() == null && entry.getSubject() != null) {
                    result.set(result.size() - 1, entry);
                }
                continue;
            }
            result.add(entry);
        }
        return result;
    }

    private NotificationSummaryItem toSummaryItem(NotificationLog entry) {
        return NotificationSummaryItem.builder()
                .id(entry.getId())
                .channel(entry.getChannel().name())
                .recipient(entry.getRecipient())
                .eventType(entry.getEventType())
                .type(NotificationClassifier.typeOf(entry.getEventType()).name())
                .priority(NotificationClassifier.priorityOf(entry.getEventType()).name())
                .subject(entry.getSubject())
                .message(entry.getMessage())
                .status(entry.getStatus().name())
                .read(entry.isRead())
                .createdAt(entry.getCreatedAt())
                .sentAt(entry.getSentAt())
                .build();
    }

    /** Marks one of the caller's own notifications read - ownership is enforced here
     *  (recipientId must match), not left to the controller, so this can never be used to mark
     *  another recipient's notification as read by guessing its id. */
    @Transactional
    public void markAsRead(UUID recipientId, UUID notificationId) {
        NotificationLog entry = logRepository.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new com.farm2home.notification.exception.ResourceNotFoundException(
                        "Notification not found: " + notificationId));
        if (!entry.isRead()) {
            entry.setRead(true);
            logRepository.save(entry);
        }
    }

    @Transactional
    public void markAllAsRead(UUID recipientId) {
        logRepository.markAllAsReadForRecipient(recipientId);
    }
}
