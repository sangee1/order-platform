package com.example.platform.common.events;

import java.util.UUID;

public record StockReleased(UUID orderId) implements DomainEvent {}
