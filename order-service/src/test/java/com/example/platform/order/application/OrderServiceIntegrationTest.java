package com.example.platform.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.order.domain.Order;
import com.example.platform.order.domain.OrderLine;
import com.example.platform.order.domain.OrderRepository;
import com.example.platform.order.domain.OrderStatus;
import com.example.platform.order.outbox.OutboxEvent;
import com.example.platform.order.outbox.OutboxRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.auto-create=false",
        "outbox.publisher.enabled=false"})
@Testcontainers
class OrderServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired OrderService service;
    @Autowired OrderSaga saga;
    @Autowired OrderRepository orders;
    @Autowired OutboxRepository outbox;

    private static final List<OrderLine> LINES = List.of(new OrderLine("SKU-1", 2, new BigDecimal("10.00")));

    @BeforeEach
    void clean() {
        outbox.deleteAllInBatch();
        orders.deleteAll();
    }

    private OrderStatus statusOf(Order o) {
        return service.get(o.getId()).getStatus();
    }

    private List<String> emittedEvents() {
        return outbox.findAll().stream().map(OutboxEvent::getEventType).toList();
    }

    // ---------- order creation ----------

    @Test
    void placingAnOrderWritesOutboxRowInSameTransaction() {
        Order order = service.place("c-1", LINES, null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(outbox.findAll()).singleElement().satisfies(e -> {
            assertThat(e.getEventType()).isEqualTo("OrderCreated");
            assertThat(e.getAggregateId()).isEqualTo(order.getId().toString());
            assertThat(e.getPublishedAt()).isNull();
        });
    }

    @Test
    void sameIdempotencyKeyReturnsSameOrderAndEmitsOneEvent() {
        Order first = service.place("c-1", LINES, "key-1");
        Order second = service.place("c-1", LINES, "key-1");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(orders.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
    }

    // ---------- saga ----------

    @Test
    void happyPathConfirmsOrderAfterPayment() {
        Order order = service.place("c-1", LINES, null);

        saga.onStockReserved(order.getId());
        assertThat(statusOf(order)).isEqualTo(OrderStatus.PAYMENT_PENDING);
        assertThat(emittedEvents()).containsExactlyInAnyOrder("OrderCreated", "PaymentRequested");

        saga.onPaymentCompleted(order.getId());
        assertThat(statusOf(order)).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void duplicateStockReservedRequestsPaymentOnlyOnce() {
        Order order = service.place("c-1", LINES, null);

        saga.onStockReserved(order.getId());
        saga.onStockReserved(order.getId());

        assertThat(outbox.countByEventType("PaymentRequested")).isEqualTo(1);
    }

    @Test
    void paymentFailureCompensatesByReleasingStock() {
        Order order = service.place("c-1", LINES, null);
        saga.onStockReserved(order.getId());

        saga.onPaymentFailed(order.getId(), "Card declined");
        saga.onPaymentFailed(order.getId(), "Card declined"); // duplicate

        assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCELLING);
        assertThat(service.get(order.getId()).getFailureReason()).contains("Card declined");
        assertThat(outbox.countByEventType("ReleaseStockRequested")).isEqualTo(1);

        saga.onStockReleased(order.getId());
        assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void stockFailureRejectsOrderWithoutRequestingPayment() {
        Order order = service.place("c-1", LINES, null);

        saga.onStockReservationFailed(order.getId(), "Insufficient stock for SKU-1");

        assertThat(statusOf(order)).isEqualTo(OrderStatus.REJECTED);
        assertThat(service.get(order.getId()).getFailureReason()).contains("SKU-1");
        assertThat(emittedEvents()).containsExactly("OrderCreated");
    }
}
