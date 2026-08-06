package com.farm2home.notification.service;

import com.farm2home.notification.domain.enums.NotificationPriority;
import com.farm2home.notification.domain.enums.NotificationType;

import java.util.Set;

/** Derives display-only Type/Priority from an eventType string - both are pure functions of
 *  "what kind of event was this," so they're computed here rather than stored as columns (see
 *  NotificationLog, which only persists genuinely per-instance state like isRead). */
public final class NotificationClassifier {

    private NotificationClassifier() {
    }

    private static final Set<String> HIGH_PRIORITY = Set.of("PAYMENT_FAILED", "DELIVERY_DELAYED", "OTP");
    private static final Set<String> LOW_PRIORITY = Set.of(
            "DELIVERY_COMPLETED", "SUBSCRIPTION_PAUSED", "SUBSCRIPTION_RESUMED", "CUSTOMER_CREATED");

    public static NotificationType typeOf(String eventType) {
        if (eventType == null) return NotificationType.OTHER;
        if (eventType.startsWith("ORDER_")) return NotificationType.ORDER;
        if (eventType.startsWith("PAYMENT_")) return NotificationType.PAYMENT;
        if (eventType.startsWith("DELIVERY_")) return NotificationType.DELIVERY;
        if (eventType.startsWith("SUBSCRIPTION_")) return NotificationType.SUBSCRIPTION;
        if (eventType.startsWith("CUSTOMER_") || "OTP".equals(eventType)) return NotificationType.ACCOUNT;
        return NotificationType.OTHER;
    }

    /** Everything not explicitly listed as HIGH/LOW defaults to MEDIUM (e.g. ORDER_CREATED,
     *  PAYMENT_SUCCESS, DELIVERY_ASSIGNED/OUT_FOR_DELIVERY, SUBSCRIPTION_CREATED/CANCELLED) -
     *  routine "something happened" updates rather than something urgent or purely informational. */
    public static NotificationPriority priorityOf(String eventType) {
        if (eventType == null) return NotificationPriority.MEDIUM;
        if (HIGH_PRIORITY.contains(eventType)) return NotificationPriority.HIGH;
        if (LOW_PRIORITY.contains(eventType)) return NotificationPriority.LOW;
        return NotificationPriority.MEDIUM;
    }
}
