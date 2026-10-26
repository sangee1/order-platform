package com.example.platform.inventory.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.example.platform.common.events.DomainEvent;
import com.example.platform.common.events.EventCodec;
import com.example.platform.common.events.EventHeaders;
import com.example.platform.common.events.OrderCreated;
import com.example.platform.common.events.StockReservationFailed;
import com.example.platform.common.events.StockReserved;
import com.example.platform.common.events.Topics;
import com.example.platform.inventory.application.ReservationFacade;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class OrderEventsListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventsListener.class);

    private final ReservationFacade reservations;

    OrderEventsListener(ReservationFacade reservations) {
        this.reservations = reservations;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS)
    void on(ConsumerRecord<String, String> record) {
        String type = header(record, EventHeaders.EVENT_TYPE);
        UUID messageId = UUID.fromString(header(record, EventHeaders.MESSAGE_ID));
        DomainEvent event = EventCodec.fromJson(type, record.value());

        switch (event) {
            case OrderCreated e -> reservations.handle(messageId, e);
            case StockReserved e -> log.debug("Ignoring {} on {}", e.eventType(), Topics.ORDER_EVENTS);
            case StockReservationFailed e -> log.debug("Ignoring {} on {}", e.eventType(), Topics.ORDER_EVENTS);
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header h = record.headers().lastHeader(name);
        if (h == null) {
            throw new IllegalArgumentException("Missing " + name + " header"); // non-retryable -> DLT
        }
        return new String(h.value(), UTF_8);
    }
}
