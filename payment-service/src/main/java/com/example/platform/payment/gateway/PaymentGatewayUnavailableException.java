package com.example.platform.payment.gateway;

/**
 * The gateway could not give us an answer even after timeout, retries, bulkhead and circuit breaker.
 * The outcome of the charge is UNKNOWN: it must never be recorded as a failure.
 */
public class PaymentGatewayUnavailableException extends RuntimeException {
    public PaymentGatewayUnavailableException(String message) {
        super(message);
    }

    public PaymentGatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
