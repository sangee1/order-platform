package com.example.platform.order.application;

import com.example.platform.common.events.OrderCreated;
import com.example.platform.common.events.OrderItemLine;
import com.example.platform.common.events.Topics;
import com.example.platform.order.domain.Order;
import com.example.platform.order.domain.OrderLine;
import com.example.platform.order.domain.OrderRepository;
import com.example.platform.order.outbox.OutboxWriter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository orders;
    private final OutboxWriter outbox;

    public OrderService(OrderRepository orders, OutboxWriter outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    /**
     * Saves the order AND its OrderCreated outbox row in one transaction. There is no direct Kafka
     * call here, so a crash can never leave an order without its event (or the reverse).
     */
    @Transactional
    public Order place(String customerId, List<OrderLine> lines, String idempotencyKey) {
        if (idempotencyKey != null) {
            Optional<Order> existing = orders.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        Order order = orders.saveAndFlush(Order.place(customerId, lines, idempotencyKey));
        outbox.append(Topics.ORDER_EVENTS, new OrderCreated(
                order.getId(),
                customerId,
                lines.stream().map(l -> new OrderItemLine(l.sku(), l.quantity())).toList(),
                order.getTotalAmount()));
        return order;
    }

    @Transactional(readOnly = true)
    public Order get(UUID id) {
        return orders.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Optional<Order> findByIdempotencyKey(String key) {
        return orders.findByIdempotencyKey(key);
    }

    @Transactional
    public void onStockReserved(UUID orderId) {
        orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId)).confirm();
    }

    @Transactional
    public void onStockReservationFailed(UUID orderId, String reason) {
        orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId)).reject(reason);
    }
}
