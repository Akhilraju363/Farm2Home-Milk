package com.farm2home.notification.domain.enums;

/** Not persisted - derived from eventType at read time by NotificationClassifier, since it's a
 *  pure function of "what kind of event was this" rather than per-instance state. */
public enum NotificationPriority {
    HIGH, MEDIUM, LOW
}
