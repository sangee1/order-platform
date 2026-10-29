package com.example.platform.common.events;

import java.util.UUID;

public record PaymentFailed(UUID orderId, String reason) implements DomainEvent {}
