package com.farm2home.auth.kafka;

import com.farm2home.auth.domain.entity.User;
import com.farm2home.common.core.constants.EmailTemplateConstants;
import com.farm2home.events.otp.OtpEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OtpEventProducer {

    private static final String TOPIC = "otp.events";

    private final KafkaTemplate<String, OtpEvent> otpKafkaTemplate;

    /** Best-effort, fire-and-forget publish - runs on kafkaEventExecutor (see KafkaConfig), never
     *  the calling request thread (registration, login, or OTP resend), so it never makes the
     *  HTTP caller wait on Kafka's health. KafkaTemplate.send() can throw synchronously (not just
     *  via the returned future) when it can't resolve topic/broker metadata in time - see
     *  CustomerEventProducer for the same pattern and a fuller explanation. The OTP is already
     *  delivered by SMS before this is called (see OtpService.generateAndSend) and that path
     *  never throws on its own, so a Kafka outage here only drops the email-based secondary
     *  delivery channel a downstream consumer would otherwise trigger, not the OTP itself. */
    @Async("kafkaEventExecutor")
    public void publishOtpGenerated(User user, String otp) {
        OtpEvent event = OtpEvent.builder()
                .eventType(EmailTemplateConstants.EVENT_OTP)
                .customerId(user.getId())
                .customerName(user.getUsername())
                .mobile(user.getMobile())
                .email(user.getEmail())
                .otp(otp)
                .occurredAt(LocalDateTime.now())
                .build();

        try {
            otpKafkaTemplate.send(TOPIC, user.getId().toString(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish OtpEvent for user {}: {}", user.getId(), ex.getMessage());
                        } else {
                            log.debug("Published OtpEvent for user {}", user.getId());
                        }
                    });
        } catch (Exception ex) {
            log.error("Failed to publish OtpEvent for user {} (Kafka unreachable?): {}", user.getId(), ex.getMessage());
        }
    }
}
