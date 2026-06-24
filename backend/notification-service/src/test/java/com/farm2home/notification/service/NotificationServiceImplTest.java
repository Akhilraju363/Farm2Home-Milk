package com.farm2home.notification.service;

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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationLogRepository logRepository;
    @Mock private NotificationTemplateRepository templateRepository;
    @Mock private NotificationMapper mapper;
    @Mock private Optional<JavaMailSender> mailSender;

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
            when(templateRepository.findByTemplateCodeAndActiveTrue("ORDER_CREATED_EMAIL"))
                    .thenReturn(Optional.empty());
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.process(event);

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository, atLeastOnce()).save(captor.capture());
            NotificationLog saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(NotificationStatus.SENT);
            assertThat(saved.getChannel()).isEqualTo(NotificationChannel.SMS);
            assertThat(saved.getMessage()).contains("ORD-001");
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
            when(templateRepository.findByTemplateCodeAndActiveTrue("PAYMENT_SUCCESS_EMAIL"))
                    .thenReturn(Optional.empty());
            when(logRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.process(event);

            ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
            verify(logRepository).save(captor.capture());
            assertThat(captor.getValue().getMessage()).doesNotContain("{{order_number}}");
            assertThat(captor.getValue().getMessage()).contains("ORD-001");
        }
    }
}
