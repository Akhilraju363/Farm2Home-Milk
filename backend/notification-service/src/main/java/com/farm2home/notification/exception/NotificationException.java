package com.farm2home.notification.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class NotificationException extends RuntimeException {
    public NotificationException(String message) {
        super(message);
    }
}
