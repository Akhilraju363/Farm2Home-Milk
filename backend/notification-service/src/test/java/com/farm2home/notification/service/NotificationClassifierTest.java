package com.farm2home.notification.service;

import com.farm2home.notification.domain.enums.NotificationPriority;
import com.farm2home.notification.domain.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationClassifierTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "ORDER_CREATED, ORDER",
            "PAYMENT_SUCCESS, PAYMENT",
            "PAYMENT_FAILED, PAYMENT",
            "DELIVERY_ASSIGNED, DELIVERY",
            "DELIVERY_OUT_FOR_DELIVERY, DELIVERY",
            "SUBSCRIPTION_CREATED, SUBSCRIPTION",
            "CUSTOMER_CREATED, ACCOUNT",
            "OTP, ACCOUNT",
            "SOME_UNKNOWN_EVENT, OTHER",
    })
    @DisplayName("typeOf() derives the coarse category from the eventType prefix")
    void typeOf_derivesCategory(String eventType, NotificationType expected) {
        assertThat(NotificationClassifier.typeOf(eventType)).isEqualTo(expected);
    }

    @Test
    @DisplayName("typeOf(null) -> OTHER")
    void typeOf_null() {
        assertThat(NotificationClassifier.typeOf(null)).isEqualTo(NotificationType.OTHER);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "PAYMENT_FAILED, HIGH",
            "DELIVERY_DELAYED, HIGH",
            "OTP, HIGH",
            "DELIVERY_COMPLETED, LOW",
            "SUBSCRIPTION_PAUSED, LOW",
            "SUBSCRIPTION_RESUMED, LOW",
            "CUSTOMER_CREATED, LOW",
            "ORDER_CREATED, MEDIUM",
            "PAYMENT_SUCCESS, MEDIUM",
            "SOME_UNKNOWN_EVENT, MEDIUM",
    })
    @DisplayName("priorityOf() applies the known HIGH/LOW overrides, defaulting to MEDIUM")
    void priorityOf_derivesUrgency(String eventType, NotificationPriority expected) {
        assertThat(NotificationClassifier.priorityOf(eventType)).isEqualTo(expected);
    }

    @Test
    @DisplayName("priorityOf(null) -> MEDIUM")
    void priorityOf_null() {
        assertThat(NotificationClassifier.priorityOf(null)).isEqualTo(NotificationPriority.MEDIUM);
    }
}
