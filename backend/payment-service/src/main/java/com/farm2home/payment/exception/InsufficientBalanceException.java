package com.farm2home.payment.exception;

import com.farm2home.common.web.exception.ConflictException;

public class InsufficientBalanceException extends ConflictException {

    public InsufficientBalanceException(String message) {
        super(message);
    }
}
