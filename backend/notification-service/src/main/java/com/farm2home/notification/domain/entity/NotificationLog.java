package com.farm2home.notification.domain.entity;

import com.farm2home.notification.domain.enums.NotificationChannel;
import com.farm2home.notification.domain.enums.NotificationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_logs", schema = "notification")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    // The producing event's own entity id (orderId/paymentId/assignmentId/subscriptionId - see
    // KafkaEventDto.getDedupeEntityId()) plus the moment the source event was published, used
    // together to dedupe a Kafka redelivery of the same message for the same channel (see
    // NotificationServiceImpl.sendForChannel()) without also suppressing a genuinely new
    // occurrence of a recurring event type for the same entity (e.g. a subscription paused,
    // resumed, then paused again - same subscriptionId, different eventOccurredAt). Both null
    // for event types that carry no entity id at all (OTP, CUSTOMER_CREATED) - those are simply
    // never deduped.
    @Column(name = "source_event_id")
    private UUID sourceEventId;

    @Column(name = "event_occurred_at")
    private LocalDateTime eventOccurredAt;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(length = 255)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @Column(name = "created_at", updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
