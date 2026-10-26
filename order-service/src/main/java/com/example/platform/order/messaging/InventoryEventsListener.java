package com.example.platform.order.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.example.platform.common.events.DomainEvent;
import com.example.platform.common.events.EventCodec;
import com.example.platform.common.events.EventHeaders;
import com.example.platform.common.events.OrderCreated;
import com.example.platform.common.events.StockReservationFailed;
import com.example.platform.common.events.StockReserved;
import com.example.platform.common.events.Topics;
import com.example.platform.order.application.OrderService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class InventoryEventsListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventsListener.class);

    private final OrderService orders;

    InventoryEventsListener(OrderService orders) {
        this.orders = orders;
    }

    @KafkaListener(topics = Topics.INVENTORY_EVENTS)
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader(EventHeaders.EVENT_TYPE);
        if (type == null) {
            throw new IllegalArgumentException("Missing " + EventHeaders.EVENT_TYPE + " header");
        }
        DomainEvent event = EventCodec.fromJson(new String(type.value(), UTF_8), record.value());

        // Sealed type + pattern matching: adding an event type without handling it here fails to compile.
        switch (event) {
            case StockReserved e -> orders.onStockReserved(e.orderId());
            case StockReservationFailed e -> orders.onStockReservationFailed(e.orderId(), e.reason());
            case OrderCreated e -> log.debug("Ignoring {} on {}", e.eventType(), Topics.INVENTORY_EVENTS);
        }
    }
}
