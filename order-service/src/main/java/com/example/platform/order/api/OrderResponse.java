package com.example.platform.order.api;

import com.example.platform.order.domain.Order;
import com.example.platform.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        String failureReason,
        Instant createdAt,
        List<Line> items) {

    public record Line(String sku, int quantity, BigDecimal unitPrice) {}

    public static OrderResponse from(Order o) {
        return new OrderResponse(o.getId(), o.getCustomerId(), o.getStatus(), o.getTotalAmount(),
                o.getFailureReason(), o.getCreatedAt(),
                o.getItems().stream().map(i -> new Line(i.sku(), i.quantity(), i.unitPrice())).toList());
    }
}
