package com.farm2home.notification.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class NotificationLogResponse {

    @Schema(description = "Notification log entry id.", example = "e5f6a7b8-1c2d-4e3f-9a8b-7c6d5e4f3a2b")
    private UUID id;

    @Schema(description = "Customer id the notification was sent to.", example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID recipientId;

    @Schema(description = "Delivery channel: SMS, EMAIL, or PUSH. PUSH entries use the recipient's "
            + "own customer id as `recipient` too, since push has no separate device-token "
            + "identifier in this codebase.", example = "EMAIL")
    private String channel;

    @Schema(description = "Event type that triggered this notification (matches the Kafka event "
            + "name from order/delivery/payment/subscription-service).", example = "DELIVERY_ASSIGNED")
    private String eventType;

    @Schema(description = "Channel-specific destination: email address (EMAIL), mobile number "
            + "(SMS), or the recipient's customer id (PUSH).", example = "customer@example.com")
    private String recipient;

    @Schema(description = "Message subject. Populated for EMAIL; typically null for SMS/PUSH.",
            example = "Your order is on its way!")
    private String subject;

    @Schema(description = "Rendered message body actually sent.",
            example = "Hi Ramesh, your order #1234 has been assigned to a delivery partner.")
    private String message;

    @Schema(description = "Delivery outcome: PENDING, SENT, or FAILED.", example = "SENT")
    private String status;

    @Schema(description = "Reason the send failed. Populated only when status is FAILED.",
            example = "SMTP connection timed out")
    private String failureReason;

    @Schema(description = "When the send was attempted. Null if still PENDING.", example = "2026-07-31T09:15:05")
    private LocalDateTime sentAt;

    @Schema(description = "When the log entry was created.", example = "2026-07-31T09:15:00")
    private LocalDateTime createdAt;
}
