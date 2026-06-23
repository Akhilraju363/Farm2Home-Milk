package com.farm2home.subscription.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class SubscriptionException extends RuntimeException {

    public SubscriptionException(String message) {
        super(message);
    }
}
