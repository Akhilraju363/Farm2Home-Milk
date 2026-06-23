package com.farm2home.delivery.domain.enums;

public enum AssignmentStatus {
    ASSIGNED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    FAILED;

    public boolean isTerminal() {
        return this == DELIVERED || this == FAILED;
    }

    public boolean canTransitionTo(AssignmentStatus next) {
        return switch (this) {
            case ASSIGNED         -> next == OUT_FOR_DELIVERY || next == FAILED;
            case OUT_FOR_DELIVERY -> next == DELIVERED        || next == FAILED;
            case DELIVERED, FAILED -> false;
        };
    }
}
