package com.farm2home.notification.service;

import com.farm2home.common.core.constants.EmailTemplateConstants;
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
import com.farm2home.notification.email.EmailSendResult;
import com.farm2home.notification.email.EmailService;
import com.farm2home.notification.mapper.NotificationMapper;
import com.farm2home.notification.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationLogRepository logRepository;
    @Mock private NotificationTemplateRepository templateRepository;
    @Mock private NotificationMapper mapper;
    @Mock private SmsService smsService;
    @Mock private PushService pushService;
    @Mock private EmailService emailService;
    @Mock private CustomerServiceClient customerServiceClient;

    @InjectMocks private NotificationServiceImpl service;

    private final UUID customerId = UUID.randomUUID();

    // buildEvent() never sets recipientEmail/customerName, so process() enriches from
    // customerServiceClient on nearly every test here - default it to "nothing found" so
    // pre-existing tests that don't care about enrichment keep behaving exactly as before.
    @BeforeEach
    void stubEnrichmentAsEmpty() {
        lenient().when(customerServiceClient.getContact(any())).thenReturn(Mono.empty());
    }

    private KafkaEventDto buildEvent(String eventType) {
        KafkaEventDto event = new KafkaEventDto();
        event.setEventType(eventType);
        event.setCustomerId(customerId);
        event.setRecipientMobile("9876543210");
        event.setOrderNumber("ORD-001");
        event.setAmount("150.00");
        return event;
    }

    private NotificationTemplate buildTemplate(String code, NotificationChannel channel) {
        return NotificationTemplate.builder()
                .id(UUID.randomUUID())
                .templateCode(code)
                .channel(channel)
                .body("Your order #{{order_number}} is confirmed. Amount: Rs {{amount}}.")
                .active(true)
                .build();
    }

    @Nested @DisplayName("process()")
    class Process {

        @Test
        @DisplayName("matching SMS template → saves SENT log")
        void smsTemplateSent() {
            KafkaEventDto event = buildEvent("ORDER_CREATED");
            NotificationTemplate template = buildTemplate("ORDER_CREATED_SMS", NotificationChannel.SMS);
            when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_SMS"))
                    .thenReturn(Optional.of(template));
            // Only stubbed for tests where buildEvent(...) sets a recipientEmail (the EMAIL
            // branch is skipped entirely otherwise), so this stub goes unused here.
            lenient().when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_EMAIL"))
                    .thenReturn(Optional.empty());
            when(smsService.sendSms(any(), any(), any())).thenReturn(SmsSendResult.success("msg-1"));
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.process(event);

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository, atLeastOnce()).save(captor.capture());
            NotificationLog saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(saved.getChannel()).isEqualTo(NotificationChannel.SMS);
            assertThat(saved.getMessage()).contains("ORD-001");
            verify(smsService).sendSms(eq("9876543210"), any(), eq("ORDER_CREATED"));
            // SmsService already publishes its own audit entry for this send - NotificationServiceImpl
            // must not duplicate it under the Notification entity.
        }

        @Test
        @DisplayName("SMS delivery fails → saves FAILED log with the failure reason, no duplicate audit")
        void smsDeliveryFails_logsFailed() {
            KafkaEventDto event = buildEvent("ORDER_CREATED");
            NotificationTemplate template = buildTemplate("ORDER_CREATED_SMS", NotificationChannel.SMS);
            when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_SMS"))
                    .thenReturn(Optional.of(template));
            lenient().when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_EMAIL"))
                    .thenReturn(Optional.empty());
            when(smsService.sendSms(any(), any(), any())).thenReturn(SmsSendResult.failure("gateway unreachable"));
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.process(event);

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository).save(captor.capture());
            NotificationLog saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(saved.getFailureReason()).isEqualTo("gateway unreachable");
        }

        @Test
        @DisplayName("matching PUSH template → saves SENT log addressed by customerId, no duplicate audit")
        void pushTemplateSent() {
            KafkaEventDto event = buildEvent("ORDER_CREATED");
            NotificationTemplate smsTemplate = buildTemplate("ORDER_CREATED_SMS", NotificationChannel.SMS);
            NotificationTemplate pushTemplate = NotificationTemplate.builder()
                    .id(UUID.randomUUID()).templateCode("ORDER_CREATED_PUSH").channel(NotificationChannel.PUSH)
                    .subject("Order Confirmed")
                    .body("Your order #{{order_number}} has been placed. Amount: Rs {{amount}}.")
                    .active(true).build();
            when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_SMS"))
                    .thenReturn(Optional.of(smsTemplate));
            when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_PUSH"))
                    .thenReturn(Optional.of(pushTemplate));
            when(smsService.sendSms(any(), any(), any())).thenReturn(SmsSendResult.success("msg-1"));
            when(pushService.sendPush(any(), any(), any(), any())).thenReturn(PushSendResult.success("push-1"));
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.process(event);

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository, times(2)).save(captor.capture());
            assertThat(captor.getAllValues())
                    .anySatisfy(l -> {
                        assertThat(l.getChannel()).isEqualTo(NotificationChannel.PUSH);
                        assertThat(l.getStatus()).isEqualTo(NotificationStatus.SENT);
                        assertThat(l.getRecipient()).isEqualTo(customerId.toString());
                    });
            verify(pushService).sendPush(eq(customerId.toString()), eq("Order Confirmed"), any(), eq("ORDER_CREATED"));
            // PushService already publishes its own audit entry for this send - NotificationServiceImpl
            // must not duplicate it under the Notification entity.
        }

        @Test
        @DisplayName("PUSH delivery fails → saves FAILED log with the failure reason, no duplicate audit")
        void pushDeliveryFails_logsFailed() {
            KafkaEventDto event = buildEvent("ORDER_CREATED");
            NotificationTemplate pushTemplate = NotificationTemplate.builder()
                    .id(UUID.randomUUID()).templateCode("ORDER_CREATED_PUSH").channel(NotificationChannel.PUSH)
                    .subject("Order Confirmed").body("body").active(true).build();
            lenient().when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_SMS"))
                    .thenReturn(Optional.empty());
            when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_PUSH"))
                    .thenReturn(Optional.of(pushTemplate));
            when(pushService.sendPush(any(), any(), any(), any())).thenReturn(PushSendResult.failure("device unreachable"));
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.process(event);

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository).save(captor.capture());
            NotificationLog saved = captor.getValue();
            assertThat(saved.getChannel()).isEqualTo(NotificationChannel.PUSH);
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(saved.getFailureReason()).isEqualTo("device unreachable");
        }

        @Test
        @DisplayName("recipientEmail set, matching EMAIL template → saves SENT log, sent via EmailService")
        void emailTemplateSent() {
            KafkaEventDto event = buildEvent("ORDER_CREATED");
            event.setRecipientEmail("customer@example.com");
            NotificationTemplate smsTemplate = buildTemplate("ORDER_CREATED_SMS", NotificationChannel.SMS);
            NotificationTemplate emailTemplate = buildTemplate("ORDER_CREATED_EMAIL", NotificationChannel.EMAIL);
            when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_SMS"))
                    .thenReturn(Optional.of(smsTemplate));
            when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_EMAIL"))
                    .thenReturn(Optional.of(emailTemplate));
            when(smsService.sendSms(any(), any(), any())).thenReturn(SmsSendResult.success("msg-1"));
            when(emailService.sendEmail(any(), any(), any(), any())).thenReturn(EmailSendResult.success("email-1"));
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.process(event);

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository, times(2)).save(captor.capture());
            assertThat(captor.getAllValues())
                    .anySatisfy(l -> {
                        assertThat(l.getChannel()).isEqualTo(NotificationChannel.EMAIL);
                        assertThat(l.getStatus()).isEqualTo(NotificationStatus.SENT);
                        assertThat(l.getRecipient()).isEqualTo("customer@example.com");
                    });
            verify(emailService).sendEmail(eq("customer@example.com"), any(), any(), eq("ORDER_CREATED"));
        }

        @Test
        @DisplayName("EMAIL delivery fails → saves FAILED log with the failure reason, no duplicate audit")
        void emailDeliveryFails_logsFailed() {
            KafkaEventDto event = buildEvent("ORDER_CREATED");
            event.setRecipientEmail("customer@example.com");
            NotificationTemplate emailTemplate = buildTemplate("ORDER_CREATED_EMAIL", NotificationChannel.EMAIL);
            lenient().when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_SMS"))
                    .thenReturn(Optional.empty());
            when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_EMAIL"))
                    .thenReturn(Optional.of(emailTemplate));
            when(emailService.sendEmail(any(), any(), any(), any()))
                    .thenReturn(EmailSendResult.failure("SMTP connection refused"));
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.process(event);

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository).save(captor.capture());
            NotificationLog saved = captor.getValue();
            assertThat(saved.getChannel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.FAILED);
            assertThat(saved.getFailureReason()).isEqualTo("SMTP connection refused");
        }

        @Test
        @DisplayName("event carries no recipientEmail → EMAIL branch skipped entirely, EmailService never called")
        void noRecipientEmail_emailSkipped() {
            KafkaEventDto event = buildEvent("ORDER_CREATED");
            NotificationTemplate smsTemplate = buildTemplate("ORDER_CREATED_SMS", NotificationChannel.SMS);
            when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_SMS"))
                    .thenReturn(Optional.of(smsTemplate));
            when(smsService.sendSms(any(), any(), any())).thenReturn(SmsSendResult.success("msg-1"));
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.process(event);

            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("Kafka redelivery of an already-SENT event (same entity id + occurredAt) → skipped, no duplicate send")
        void duplicateEvent_sameEntityAndOccurredAt_skipped() {
            KafkaEventDto event = buildEvent("ORDER_CREATED");
            java.time.LocalDateTime occurredAt = java.time.LocalDateTime.of(2026, 1, 1, 10, 0);
            event.setOrderId(UUID.randomUUID());
            event.setOccurredAt(occurredAt);
            when(logRepository.existsByChannelAndEventTypeAndSourceEventIdAndEventOccurredAtAndStatus(
                    eq(NotificationChannel.SMS), eq("ORDER_CREATED"), eq(event.getOrderId()), eq(occurredAt), eq(NotificationStatus.SENT)))
                    .thenReturn(true);
            lenient().when(templateRepository.findByTemplateCodeAndActiveTrue(anyString()))
                    .thenReturn(Optional.empty());

            service.process(event);

            verify(smsService, never()).sendSms(any(), any(), any());
            verify(templateRepository, never()).findByTemplateCodeAndActiveTrue("ORDER_CREATED_SMS");
        }

        @Test
        @DisplayName("same entity id but a different occurredAt → treated as a new occurrence, sent normally")
        void sameEntityDifferentOccurredAt_notSkipped() {
            KafkaEventDto event = buildEvent("SUBSCRIPTION_PAUSED");
            UUID subscriptionId = UUID.randomUUID();
            event.setSourceEventId(subscriptionId);
            event.setOccurredAt(java.time.LocalDateTime.of(2026, 2, 1, 9, 0));
            NotificationTemplate template = buildTemplate("SUBSCRIPTION_PAUSED_SMS", NotificationChannel.SMS);
            // A prior (different) occurrence of the same event type for the same subscription
            // was already sent - only an exact (entityId, occurredAt) match should be skipped.
            when(logRepository.existsByChannelAndEventTypeAndSourceEventIdAndEventOccurredAtAndStatus(
                    eq(NotificationChannel.SMS), eq("SUBSCRIPTION_PAUSED"), eq(subscriptionId), eq(event.getOccurredAt()), eq(NotificationStatus.SENT)))
                    .thenReturn(false);
            when(templateRepository.findByTemplateCodeAndActiveTrue("SUBSCRIPTION_PAUSED_SMS"))
                    .thenReturn(Optional.of(template));
            when(smsService.sendSms(any(), any(), any())).thenReturn(SmsSendResult.success("msg-1"));
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.process(event);

            verify(smsService).sendSms(any(), any(), eq("SUBSCRIPTION_PAUSED"));
        }

        @Test
        @DisplayName("event has no entity id (e.g. OTP) → idempotency check skipped entirely, always sent")
        void noEntityId_idempotencyNotChecked() {
            KafkaEventDto event = buildEvent(EmailTemplateConstants.EVENT_OTP);
            event.setOtp("123456");
            NotificationTemplate template = buildTemplate("OTP_SMS", NotificationChannel.SMS);
            when(templateRepository.findByTemplateCodeAndActiveTrue("OTP_SMS")).thenReturn(Optional.of(template));
            when(smsService.sendSms(any(), any(), any())).thenReturn(SmsSendResult.success("msg-1"));
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.process(event);

            verify(logRepository, never()).existsByChannelAndEventTypeAndSourceEventIdAndEventOccurredAtAndStatus(
                    any(), any(), any(), any(), any());
            verify(smsService).sendSms(any(), any(), eq(EmailTemplateConstants.EVENT_OTP));
        }

        @Test
        @DisplayName("all optional payload fields set → each is rendered into the payload map")
        void allPayloadFieldsSet_renderedCorrectly() {
            KafkaEventDto event = buildEvent("DELIVERY_ASSIGNED");
            event.setPartnerName("Raj Kumar");
            event.setExpectedTime("10:00 AM");
            event.setPaymentReference("PAY-123");
            event.setCustomerName("Jane Doe");
            event.setQuantity("2.5L");
            event.setMilkType("FULL_CREAM");
            event.setExtra(java.util.Map.of("extraKey", "extraValue"));

            NotificationTemplate template = NotificationTemplate.builder()
                    .id(UUID.randomUUID()).templateCode(EmailTemplateConstants.EVENT_DELIVERY_ASSIGNED + EmailTemplateConstants.SMS_SUFFIX).channel(NotificationChannel.SMS)
                    .body("{{partner_name}} arriving {{expected_time}} for {{customer_name}}, "
                            + "{{quantity}} {{milk_type}}, ref {{reference}}, {{extraKey}}")
                    .active(true).build();
            when(templateRepository.findByTemplateCodeAndActiveTrue(EmailTemplateConstants.EVENT_DELIVERY_ASSIGNED + EmailTemplateConstants.SMS_SUFFIX))
                    .thenReturn(Optional.of(template));
            lenient().when(templateRepository.findByTemplateCodeAndActiveTrue("DELIVERY_ASSIGNED_EMAIL"))
                    .thenReturn(Optional.empty());
            when(smsService.sendSms(any(), any(), any())).thenReturn(SmsSendResult.success("msg-1"));
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.process(event);

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository).save(captor.capture());
            String message = captor.getValue().getMessage();
            assertThat(message).contains("Raj Kumar", "10:00 AM", "Jane Doe", "2.5L", "FULL_CREAM",
                    "PAY-123", "extraValue");
        }

        @Test
        @DisplayName("no matching template → no log saved")
        void noTemplate_noLog() {
            KafkaEventDto event = buildEvent("UNKNOWN_EVENT");
            when(templateRepository.findByTemplateCodeAndActiveTrue(anyString()))
                    .thenReturn(Optional.empty());

            service.process(event);

            verify(logRepository, never()).save(any());
        }

        @Test
        @DisplayName("null customerId → skips processing")
        void nullCustomerId_skips() {
            KafkaEventDto event = new KafkaEventDto();
            event.setEventType("ORDER_CREATED");

            service.process(event);

            verify(templateRepository, never()).findByTemplateCodeAndActiveTrue(anyString());
            verify(logRepository, never()).save(any());
        }

        @Test
        @DisplayName("recipientEmail/customerName missing → enriches from customer-service before sending")
        void missingContactInfo_enrichedFromCustomerService() {
            KafkaEventDto event = buildEvent("PAYMENT_SUCCESS");
            event.setRecipientMobile(null); // force both SMS and EMAIL to depend on enrichment
            CustomerContactDto contact = new CustomerContactDto();
            contact.setFirstName("Asha");
            contact.setLastName("Rao");
            contact.setMobile("9876500000");
            contact.setEmail("asha.rao@example.com");
            when(customerServiceClient.getContact(customerId)).thenReturn(Mono.just(contact));
            NotificationTemplate smsTemplate = buildTemplate("PAYMENT_SUCCESS_SMS", NotificationChannel.SMS);
            NotificationTemplate emailTemplate = buildTemplate("PAYMENT_SUCCESS_EMAIL", NotificationChannel.EMAIL);
            when(templateRepository.findByTemplateCodeAndActiveTrue("PAYMENT_SUCCESS_SMS"))
                    .thenReturn(Optional.of(smsTemplate));
            when(templateRepository.findByTemplateCodeAndActiveTrue("PAYMENT_SUCCESS_EMAIL"))
                    .thenReturn(Optional.of(emailTemplate));
            when(smsService.sendSms(any(), any(), any())).thenReturn(SmsSendResult.success("msg-1"));
            when(emailService.sendEmail(any(), any(), any(), any())).thenReturn(EmailSendResult.success("email-1"));
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.process(event);

            verify(smsService).sendSms(eq("9876500000"), any(), eq("PAYMENT_SUCCESS"));
            assertThat(event.getCustomerName()).isEqualTo("Asha Rao");
            assertThat(event.getRecipientEmail()).isEqualTo("asha.rao@example.com");
        }

        @Test
        @DisplayName("customer-service lookup fails → falls back to PUSH only, no exception")
        void enrichmentLookupFails_fallsBackToPushOnly() {
            KafkaEventDto event = buildEvent("PAYMENT_SUCCESS");
            event.setRecipientMobile(null);
            when(customerServiceClient.getContact(customerId)).thenReturn(Mono.error(new RuntimeException("unreachable")));
            lenient().when(templateRepository.findByTemplateCodeAndActiveTrue("PAYMENT_SUCCESS_SMS"))
                    .thenReturn(Optional.empty());
            NotificationTemplate pushTemplate = NotificationTemplate.builder()
                    .id(UUID.randomUUID()).templateCode("PAYMENT_SUCCESS_PUSH").channel(NotificationChannel.PUSH)
                    .subject("Payment Received").body("body").active(true).build();
            when(templateRepository.findByTemplateCodeAndActiveTrue("PAYMENT_SUCCESS_PUSH"))
                    .thenReturn(Optional.of(pushTemplate));
            when(pushService.sendPush(any(), any(), any(), any())).thenReturn(PushSendResult.success("push-1"));
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.process(event);

            verify(smsService, never()).sendSms(any(), any(), any());
            verify(pushService).sendPush(eq(customerId.toString()), any(), any(), eq("PAYMENT_SUCCESS"));
        }

        @Test
        @DisplayName("template renders placeholders correctly")
        void templateRendering() {
            KafkaEventDto event = buildEvent("PAYMENT_SUCCESS");
            NotificationTemplate template = buildTemplate("PAYMENT_SUCCESS_SMS", NotificationChannel.SMS);
            when(templateRepository.findByTemplateCodeAndActiveTrue("PAYMENT_SUCCESS_SMS"))
                    .thenReturn(Optional.of(template));
            lenient().when(templateRepository.findByTemplateCodeAndActiveTrue("PAYMENT_SUCCESS_EMAIL"))
                    .thenReturn(Optional.empty());
            when(smsService.sendSms(any(), any(), any())).thenReturn(SmsSendResult.success("msg-1"));
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.process(event);

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository).save(captor.capture());
            assertThat(captor.getValue().getMessage()).doesNotContain("{{order_number}}");
            assertThat(captor.getValue().getMessage()).contains("ORD-001");
        }
    }

    @Nested @DisplayName("findByRecipient()")
    class FindByRecipient {

        @Test
        @DisplayName("returns mapped page for the recipient")
        void returnsMappedPage() {
            NotificationLog log = NotificationLog.builder().id(UUID.randomUUID()).build();
            when(logRepository.findAllByRecipientIdOrderByCreatedAtDesc(any(), any())).thenReturn(
                    new org.springframework.data.domain.PageImpl<>(java.util.List.of(log)));
            when(mapper.toResponse(log)).thenReturn(
                    com.farm2home.notification.dto.response.NotificationLogResponse.builder().build());

            var result = service.findByRecipient(customerId, org.springframework.data.domain.Pageable.unpaged());

            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested @DisplayName("getRecent()")
    class GetRecent {

        @Test
        @DisplayName("returns mapped list ordered by most recent createdAt")
        void returnsMappedList() {
            NotificationLog logEntry = NotificationLog.builder()
                    .id(UUID.randomUUID())
                    .channel(NotificationChannel.SMS)
                    .recipient("9876543210")
                    .subject("Order Confirmed")
                    .status(NotificationStatus.SENT)
                    .createdAt(java.time.LocalDateTime.now())
                    .build();
            when(logRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(logEntry)));

            var result = service.getRecent(10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(logEntry.getId());
            assertThat(result.get(0).getChannel()).isEqualTo("SMS");
            assertThat(result.get(0).getRecipient()).isEqualTo("9876543210");
            assertThat(result.get(0).getSubject()).isEqualTo("Order Confirmed");
            assertThat(result.get(0).getStatus()).isEqualTo("SENT");
        }

        @Test
        @DisplayName("no logs → returns empty list")
        void noLogs_returnsEmpty() {
            when(logRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));

            assertThat(service.getRecent(10)).isEmpty();
        }
    }

    @Nested @DisplayName("getMyRecent()")
    class GetMyRecent {

        private NotificationLog logEntry(String eventType, NotificationChannel channel, String subject,
                                          java.time.LocalDateTime createdAt) {
            return NotificationLog.builder()
                    .id(UUID.randomUUID())
                    .eventType(eventType)
                    .channel(channel)
                    .recipient("9876543210")
                    .subject(subject)
                    .message("body")
                    .status(NotificationStatus.SENT)
                    .createdAt(createdAt)
                    .build();
        }

        @Test
        @DisplayName("same event fanned out to SMS+EMAIL+PUSH within seconds → collapses to one item, preferring the subject-bearing entry")
        void collapsesSameEventAcrossChannels() {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            NotificationLog sms = logEntry("ORDER_CREATED", NotificationChannel.SMS, null, now);
            NotificationLog email = logEntry("ORDER_CREATED", NotificationChannel.EMAIL, "Order Confirmed", now.minusSeconds(1));
            when(logRepository.findAllByRecipientIdOrderByCreatedAtDesc(eq(customerId), any()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(sms, email)));

            var result = service.getMyRecent(customerId, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSubject()).isEqualTo("Order Confirmed");
            assertThat(result.get(0).getEventType()).isEqualTo("ORDER_CREATED");
        }

        @Test
        @DisplayName("different event types are never collapsed, even if adjacent in time")
        void distinctEventTypesKeptSeparate() {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            NotificationLog order = logEntry("ORDER_CREATED", NotificationChannel.SMS, null, now);
            NotificationLog payment = logEntry("PAYMENT_SUCCESS", NotificationChannel.SMS, null, now.minusSeconds(1));
            when(logRepository.findAllByRecipientIdOrderByCreatedAtDesc(eq(customerId), any()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(order, payment)));

            var result = service.getMyRecent(customerId, 10);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("OTP events are excluded from the notification center")
        void excludesOtpEvents() {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            NotificationLog otp = logEntry(EmailTemplateConstants.EVENT_OTP, NotificationChannel.SMS, null, now);
            when(logRepository.findAllByRecipientIdOrderByCreatedAtDesc(eq(customerId), any()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(otp)));

            var result = service.getMyRecent(customerId, 10);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("no logs → returns empty list")
        void noLogs_returnsEmpty() {
            when(logRepository.findAllByRecipientIdOrderByCreatedAtDesc(eq(customerId), any()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of()));

            assertThat(service.getMyRecent(customerId, 10)).isEmpty();
        }
    }

    @Nested @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("existing log → returns mapped response")
        void found() {
            UUID logId = UUID.randomUUID();
            NotificationLog log = NotificationLog.builder().id(logId).build();
            when(logRepository.findById(logId)).thenReturn(Optional.of(log));
            when(mapper.toResponse(log)).thenReturn(
                    com.farm2home.notification.dto.response.NotificationLogResponse.builder().id(logId).build());

            var result = service.findById(logId);

            assertThat(result.getId()).isEqualTo(logId);
        }

        @Test
        @DisplayName("missing log → throws ResourceNotFoundException")
        void notFound() {
            UUID logId = UUID.randomUUID();
            when(logRepository.findById(logId)).thenReturn(Optional.empty());

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.findById(logId))
                    .isInstanceOf(com.farm2home.notification.exception.ResourceNotFoundException.class);
        }
    }

    @Nested @DisplayName("markAsRead()")
    class MarkAsRead {

        @Test
        @DisplayName("unread notification owned by the caller → marks it read and saves")
        void marksUnreadAsRead() {
            UUID notificationId = UUID.randomUUID();
            NotificationLog log = NotificationLog.builder().id(notificationId).recipientId(customerId).build();
            when(logRepository.findByIdAndRecipientId(notificationId, customerId)).thenReturn(Optional.of(log));

            service.markAsRead(customerId, notificationId);

            assertThat(log.isRead()).isTrue();
            verify(logRepository).save(log);
        }

        @Test
        @DisplayName("already-read notification → no-op, does not re-save")
        void alreadyRead_noOp() {
            UUID notificationId = UUID.randomUUID();
            NotificationLog log = NotificationLog.builder().id(notificationId).recipientId(customerId).isRead(true).build();
            when(logRepository.findByIdAndRecipientId(notificationId, customerId)).thenReturn(Optional.of(log));

            service.markAsRead(customerId, notificationId);

            verify(logRepository, never()).save(any());
        }

        @Test
        @DisplayName("notification belongs to someone else (or doesn't exist) → throws, never leaks existence via a different error")
        void notOwnedByCaller_throws() {
            UUID notificationId = UUID.randomUUID();
            when(logRepository.findByIdAndRecipientId(notificationId, customerId)).thenReturn(Optional.empty());

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.markAsRead(customerId, notificationId))
                    .isInstanceOf(com.farm2home.notification.exception.ResourceNotFoundException.class);
            verify(logRepository, never()).save(any());
        }
    }

    @Nested @DisplayName("markAllAsRead()")
    class MarkAllAsRead {

        @Test
        @DisplayName("delegates to the bulk repository update, scoped to the caller")
        void delegatesToRepository() {
            service.markAllAsRead(customerId);

            verify(logRepository).markAllAsReadForRecipient(customerId);
        }
    }
}
