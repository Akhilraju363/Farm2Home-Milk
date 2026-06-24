package com.farm2home.notification.dto.response;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationLogResponse {
    private UUID id;
    private UUID recipientId;
    private String channel;
    private String eventType;
    private String recipient;
    private String subject;
    private String message;
    private String status;
    private String failureReason;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
}
