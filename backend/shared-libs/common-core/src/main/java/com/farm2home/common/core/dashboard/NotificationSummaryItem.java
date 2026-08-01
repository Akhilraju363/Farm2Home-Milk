package com.farm2home.common.core.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row of GET /api/v1/notifications/summary - a recent notification, across all recipients. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSummaryItem {

    @Schema(description = "Notification log entry id.", example = "e5f6a7b8-1c2d-4e3f-9a8b-7c6d5e4f3a2b")
    private UUID id;

    @Schema(description = "Delivery channel: SMS, EMAIL, or PUSH.", example = "EMAIL")
    private String channel;

    @Schema(description = "Channel-specific destination: email address (EMAIL), mobile number "
            + "(SMS), or the recipient's customer id (PUSH).", example = "customer@example.com")
    private String recipient;

    @Schema(description = "Message subject. Populated for EMAIL; typically null for SMS/PUSH.",
            example = "Your order is on its way!")
    private String subject;

    @Schema(description = "Delivery outcome: PENDING, SENT, or FAILED.", example = "SENT")
    private String status;

    @Schema(description = "When the log entry was created.", example = "2026-07-31T09:15:00")
    private LocalDateTime createdAt;
}
