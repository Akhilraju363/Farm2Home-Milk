package com.farm2home.order.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class OrderException extends RuntimeException {
    public OrderException(String message) { super(message); }
}
