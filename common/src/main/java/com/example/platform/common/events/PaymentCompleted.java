package com.example.platform.common.events;

import java.util.UUID;

public record PaymentCompleted(UUID orderId, String transactionId) implements DomainEvent {}
