package com.example.platform.order.domain;

/**
 * Saga states.
 * <pre>
 * PENDING ──stock reserved──► PAYMENT_PENDING ──payment ok──► CONFIRMED
 *    │                              │
 *    └─stock failed─► REJECTED      └─payment failed─► CANCELLING ──stock released──► CANCELLED
 * </pre>
 */
public enum OrderStatus {
    PENDING, PAYMENT_PENDING, CONFIRMED, REJECTED, CANCELLING, CANCELLED
}
