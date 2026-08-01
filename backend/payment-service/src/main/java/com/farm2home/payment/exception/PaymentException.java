package com.farm2home.payment.exception;

import com.farm2home.common.web.exception.BadRequestException;

public class PaymentException extends BadRequestException {

    public PaymentException(String message) {
        super(message);
    }

    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
