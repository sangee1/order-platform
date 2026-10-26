package com.example.platform.order.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.order.domain.Order;
import com.example.platform.order.domain.OrderLine;
import com.example.platform.order.domain.OrderRepository;
import com.example.platform.order.domain.OrderStatus;
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
    @Autowired OrderRepository orders;
    @Autowired OutboxRepository outbox;

    private static final List<OrderLine> LINES = List.of(new OrderLine("SKU-1", 2, new BigDecimal("10.00")));

    @BeforeEach
    void clean() {
        outbox.deleteAllInBatch();
        orders.deleteAll();
    }

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

    @Test
    void stockReservedConfirmsOrderAndDuplicateDeliveryIsHarmless() {
        Order order = service.place("c-1", LINES, null);

        service.onStockReserved(order.getId());
        service.onStockReserved(order.getId());

        assertThat(service.get(order.getId()).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void stockReservationFailureRejectsOrderWithReason() {
        Order order = service.place("c-1", LINES, null);

        service.onStockReservationFailed(order.getId(), "Insufficient stock for SKU-1");

        Order reloaded = service.get(order.getId());
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(reloaded.getFailureReason()).contains("SKU-1");
    }
}
