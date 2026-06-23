package com.farm2home.delivery.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class DeliveryException extends RuntimeException {
    public DeliveryException(String message) { super(message); }
}
