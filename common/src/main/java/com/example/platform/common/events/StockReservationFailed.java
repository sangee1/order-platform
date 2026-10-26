package com.example.platform.common.events;

import java.util.UUID;

public record StockReservationFailed(UUID orderId, String reason) implements DomainEvent {}
