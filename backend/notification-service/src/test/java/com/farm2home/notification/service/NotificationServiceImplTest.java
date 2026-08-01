package com.farm2home.notification.service;

import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.common.core.constants.EmailTemplateConstants;
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
import com.farm2home.notification.mapper.NotificationMapper;
import com.farm2home.notification.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

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
    @Mock private Optional<JavaMailSender> mailSender;
    @Mock private AuditLogService auditLogService;
    @Mock private SmsService smsService;
    @Mock private PushService pushService;

    @InjectMocks private NotificationServiceImpl service;

    private final UUID customerId = UUID.randomUUID();

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
            verifyNoInteractions(auditLogService);
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
            verifyNoInteractions(auditLogService);
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
            verifyNoInteractions(auditLogService);
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
            verifyNoInteractions(auditLogService);
        }

        @Test
        @DisplayName("recipientEmail set, matching EMAIL template, mail sender unconfigured → logs FAILED... " +
                "actually SENT (dispatch swallows the missing sender) with a warning")
        void emailBranch_mailSenderNotConfigured() {
            KafkaEventDto event = buildEvent("ORDER_CREATED");
            event.setRecipientEmail("customer@example.com");
            NotificationTemplate smsTemplate = buildTemplate("ORDER_CREATED_SMS", NotificationChannel.SMS);
            NotificationTemplate emailTemplate = buildTemplate("ORDER_CREATED_EMAIL", NotificationChannel.EMAIL);
            when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_SMS"))
                    .thenReturn(Optional.of(smsTemplate));
            when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_EMAIL"))
                    .thenReturn(Optional.of(emailTemplate));
            when(smsService.sendSms(any(), any(), any())).thenReturn(SmsSendResult.success("msg-1"));
            when(mailSender.isEmpty()).thenReturn(true);
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.process(event);

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository, times(2)).save(captor.capture());
            assertThat(captor.getAllValues())
                    .anySatisfy(l -> assertThat(l.getChannel()).isEqualTo(NotificationChannel.EMAIL));
        }

        @Test
        @DisplayName("recipientEmail set, mail sender configured → actually sends via JavaMailSender")
        void emailBranch_mailSenderConfigured() throws Exception {
            KafkaEventDto event = buildEvent("ORDER_CREATED");
            event.setRecipientEmail("customer@example.com");
            NotificationTemplate smsTemplate = buildTemplate("ORDER_CREATED_SMS", NotificationChannel.SMS);
            NotificationTemplate emailTemplate = buildTemplate("ORDER_CREATED_EMAIL", NotificationChannel.EMAIL);
            when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_SMS"))
                    .thenReturn(Optional.of(smsTemplate));
            when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_EMAIL"))
                    .thenReturn(Optional.of(emailTemplate));
            org.springframework.mail.javamail.JavaMailSender realSender =
                    mock(org.springframework.mail.javamail.JavaMailSender.class);
            jakarta.mail.Session mailSession =
                    jakarta.mail.Session.getDefaultInstance(new java.util.Properties());
            when(realSender.createMimeMessage()).thenReturn(new jakarta.mail.internet.MimeMessage(mailSession));
            when(mailSender.isEmpty()).thenReturn(false);
            when(mailSender.get()).thenReturn(realSender);
            when(smsService.sendSms(any(), any(), any())).thenReturn(SmsSendResult.success("msg-1"));
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            ReflectionTestUtils.setField(service, "mailFrom", "no-reply@farm2homemilk.example");

            service.process(event);

            verify(realSender).send(any(jakarta.mail.internet.MimeMessage.class));
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
}
