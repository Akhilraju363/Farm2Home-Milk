package com.farm2home.customer.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class CustomerException extends RuntimeException {
    public CustomerException(String message) {
        super(message);
    }
}
