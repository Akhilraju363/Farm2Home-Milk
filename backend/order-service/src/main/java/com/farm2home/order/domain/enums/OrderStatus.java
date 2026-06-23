package com.farm2home.order.domain.enums;

public enum OrderStatus {
    PENDING,
    ASSIGNED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING          -> next == ASSIGNED   || next == CANCELLED;
            case ASSIGNED         -> next == OUT_FOR_DELIVERY || next == CANCELLED;
            case OUT_FOR_DELIVERY -> next == DELIVERED  || next == CANCELLED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
