package com.example.platform.order.application;

import com.example.platform.common.events.PaymentRequested;
import com.example.platform.common.events.ReleaseStockRequested;
import com.example.platform.common.events.Topics;
import com.example.platform.order.domain.Order;
import com.example.platform.order.domain.OrderRepository;
import com.example.platform.order.outbox.OutboxWriter;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga orchestrator. Each handler is one local transaction that (1) advances the order's state and
 * (2) writes the next command to the outbox. A command is emitted only when the state actually changed,
 * so redelivered events never trigger a second payment or a second release.
 *
 * <pre>
 * happy path:   OrderCreated -> [inventory] StockReserved -> PaymentRequested -> [payment] PaymentCompleted -> CONFIRMED
 * stock fails:  StockReservationFailed -> REJECTED
 * pay fails:    PaymentFailed -> ReleaseStockRequested (compensation) -> [inventory] StockReleased -> CANCELLED
 * </pre>
 */
@Service
public class OrderSaga {

    private final OrderRepository orders;
    private final OutboxWriter outbox;

    public OrderSaga(OrderRepository orders, OutboxWriter outbox) {
        this.orders = orders;
        this.outbox = outbox;
    }

    @Transactional
    public void onStockReserved(UUID orderId) {
        Order order = load(orderId);
        if (order.awaitPayment()) {
            outbox.append(Topics.ORDER_EVENTS,
                    new PaymentRequested(orderId, order.getCustomerId(), order.getTotalAmount()));
        }
    }

    @Transactional
    public void onStockReservationFailed(UUID orderId, String reason) {
        load(orderId).reject(reason);
    }

    @Transactional
    public void onPaymentCompleted(UUID orderId) {
        load(orderId).confirm();
    }

    @Transactional
    public void onPaymentFailed(UUID orderId, String reason) {
        Order order = load(orderId);
        if (order.startCancellation("Payment failed: " + reason)) {
            outbox.append(Topics.ORDER_EVENTS, new ReleaseStockRequested(orderId, reason));
        }
    }

    @Transactional
    public void onStockReleased(UUID orderId) {
        load(orderId).cancel();
    }

    private Order load(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }
}
