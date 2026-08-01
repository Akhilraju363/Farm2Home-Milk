package com.farm2home.subscription.domain.enums;

public enum SubscriptionStatus {
    ACTIVE,
    PAUSED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminal() {
        return this == CANCELLED || this == EXPIRED;
    }

    public boolean canTransitionTo(SubscriptionStatus next) {
        return switch (this) {
            case ACTIVE             -> next == PAUSED || next == CANCELLED || next == EXPIRED;
            case PAUSED             -> next == ACTIVE || next == CANCELLED || next == EXPIRED;
            case CANCELLED, EXPIRED -> false;
        };
    }
}
