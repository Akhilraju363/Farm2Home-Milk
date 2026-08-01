package com.farm2home.payment.exception;

/** Thrown for gateway-level failures: unreachable provider, invalid webhook/checkout signature,
 *  or a malformed response. Extends {@link PaymentException} (not a new exception hierarchy) so
 *  it's handled by the existing {@code GlobalExceptionHandler} with zero changes there. */
public class PaymentGatewayException extends PaymentException {

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
