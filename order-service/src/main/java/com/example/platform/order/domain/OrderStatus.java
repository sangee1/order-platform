package com.example.platform.order.domain;

/** PENDING -> CONFIRMED | REJECTED. Payment states (PAYMENT_PENDING, PAID...) arrive with payment-service. */
public enum OrderStatus {
    PENDING, CONFIRMED, REJECTED
}
