package com.example.platform.common.events;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderCreated(UUID orderId, String customerId, List<OrderItemLine> items, BigDecimal totalAmount)
        implements DomainEvent {}
