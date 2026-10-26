package com.example.platform.common.events;

import java.util.UUID;

public record StockReserved(UUID orderId) implements DomainEvent {}
