package com.example.platform.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.common.events.OrderCreated;
import com.example.platform.common.events.OrderItemLine;
import com.example.platform.inventory.domain.InventoryItem;
import com.example.platform.inventory.domain.InventoryItemRepository;
import com.example.platform.inventory.domain.ProcessedMessageRepository;
import com.example.platform.inventory.domain.StockReservationRepository;
import com.example.platform.inventory.outbox.OutboxRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
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
class ReservationConcurrencyTest {

    private static final String SKU = "TEST-SKU";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired ReservationFacade facade;
    @Autowired InventoryItemRepository items;
    @Autowired StockReservationRepository reservations;
    @Autowired ProcessedMessageRepository processed;
    @Autowired OutboxRepository outbox;

    @BeforeEach
    void reset() {
        outbox.deleteAllInBatch();
        processed.deleteAllInBatch();
        reservations.deleteAllInBatch();
        items.deleteById(SKU);
    }

    private static OrderCreated order(int qty) {
        return new OrderCreated(UUID.randomUUID(), "c-1", List.of(new OrderItemLine(SKU, qty)), BigDecimal.TEN);
    }

    @Test
    void concurrentOrdersNeverOversell() throws Exception {
        items.save(new InventoryItem(SKU, 10));
        int concurrentOrders = 25;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<? extends Future<?>> futures = IntStream.range(0, concurrentOrders)
                    .mapToObj(i -> executor.submit(() -> facade.handle(UUID.randomUUID(), order(1))))
                    .toList();
            for (Future<?> f : futures) {
                f.get();
            }
        }

        InventoryItem item = items.findById(SKU).orElseThrow();
        assertThat(item.getAvailableQuantity()).isZero();
        assertThat(item.getReservedQuantity()).isEqualTo(10);
        assertThat(outbox.countByEventType("StockReserved")).isEqualTo(10);
        assertThat(outbox.countByEventType("StockReservationFailed")).isEqualTo(15);
    }

    @Test
    void redeliveredMessageIsProcessedOnce() {
        items.save(new InventoryItem(SKU, 10));
        UUID messageId = UUID.randomUUID();
        OrderCreated order = order(3);

        facade.handle(messageId, order);
        facade.handle(messageId, order);

        assertThat(items.findById(SKU).orElseThrow().getAvailableQuantity()).isEqualTo(7);
        assertThat(reservations.findByOrderId(order.orderId())).hasSize(1);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void insufficientStockFailsWholeOrderWithoutPartialReservation() {
        items.save(new InventoryItem(SKU, 2));
        OrderCreated order = new OrderCreated(UUID.randomUUID(), "c-1",
                List.of(new OrderItemLine(SKU, 1), new OrderItemLine("MISSING-SKU", 1)), BigDecimal.TEN);

        facade.handle(UUID.randomUUID(), order);

        assertThat(items.findById(SKU).orElseThrow().getAvailableQuantity()).isEqualTo(2);
        assertThat(reservations.findByOrderId(order.orderId())).isEmpty();
        assertThat(outbox.countByEventType("StockReservationFailed")).isEqualTo(1);
    }
}
