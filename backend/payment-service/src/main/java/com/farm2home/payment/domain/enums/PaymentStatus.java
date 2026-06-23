package com.farm2home.payment.domain.enums;

public enum PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    REFUNDED;

    public boolean isTerminal() {
        return this == FAILED || this == REFUNDED;
    }
}
