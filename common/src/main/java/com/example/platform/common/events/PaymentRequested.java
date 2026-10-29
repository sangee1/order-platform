package com.example.platform.common.events;

import java.math.BigDecimal;
import java.util.UUID;

/** Command from the order saga to payment-service. */
public record PaymentRequested(UUID orderId, String customerId, BigDecimal amount) implements DomainEvent {}
