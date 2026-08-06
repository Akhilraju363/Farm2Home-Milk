package com.farm2home.notification.domain.enums;

/** Not persisted - derived from eventType at read time by NotificationClassifier. A coarser
 *  grouping than eventType (e.g. ORDER_CREATED, DELIVERY_ASSIGNED, DELIVERY_COMPLETED all fall
 *  under ORDER/DELIVERY) for display/filtering purposes. */
public enum NotificationType {
    ORDER, PAYMENT, DELIVERY, SUBSCRIPTION, ACCOUNT, OTHER
}
