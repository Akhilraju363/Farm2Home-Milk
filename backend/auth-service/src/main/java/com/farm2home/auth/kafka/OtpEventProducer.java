package com.farm2home.auth.kafka;

import com.farm2home.auth.domain.entity.User;
import com.farm2home.common.core.constants.EmailTemplateConstants;
import com.farm2home.events.otp.OtpEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OtpEventProducer {

    private static final String TOPIC = "otp.events";

    private final KafkaTemplate<String, OtpEvent> otpKafkaTemplate;

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

        otpKafkaTemplate.send(TOPIC, user.getId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OtpEvent for user {}: {}", user.getId(), ex.getMessage());
                    } else {
                        log.debug("Published OtpEvent for user {}", user.getId());
                    }
                });
    }
}
