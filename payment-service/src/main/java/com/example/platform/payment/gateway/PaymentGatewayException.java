package com.example.platform.payment.gateway;

/** A technical failure talking to the provider (timeout, HTTP 5xx...). Retryable; says nothing about the card. */
public class PaymentGatewayException extends RuntimeException {
    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
