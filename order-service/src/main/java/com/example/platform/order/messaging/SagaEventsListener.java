package com.example.platform.order.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.example.platform.common.events.DomainEvent;
import com.example.platform.common.events.EventCodec;
import com.example.platform.common.events.EventHeaders;
import com.example.platform.common.events.PaymentCompleted;
import com.example.platform.common.events.PaymentFailed;
import com.example.platform.common.events.StockReleased;
import com.example.platform.common.events.StockReservationFailed;
import com.example.platform.common.events.StockReserved;
import com.example.platform.common.events.Topics;
import com.example.platform.order.application.OrderSaga;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Receives the replies of the other services and feeds them to the saga. */
@Component
class SagaEventsListener {

    private static final Logger log = LoggerFactory.getLogger(SagaEventsListener.class);

    private final OrderSaga saga;

    SagaEventsListener(OrderSaga saga) {
        this.saga = saga;
    }

    @KafkaListener(topics = {Topics.INVENTORY_EVENTS, Topics.PAYMENT_EVENTS})
    void on(ConsumerRecord<String, String> record) {
        Header type = record.headers().lastHeader(EventHeaders.EVENT_TYPE);
        if (type == null) {
            throw new IllegalArgumentException("Missing " + EventHeaders.EVENT_TYPE + " header");
        }
        DomainEvent event = EventCodec.fromJson(new String(type.value(), UTF_8), record.value());

        switch (event) {
            case StockReserved e -> saga.onStockReserved(e.orderId());
            case StockReservationFailed e -> saga.onStockReservationFailed(e.orderId(), e.reason());
            case PaymentCompleted e -> saga.onPaymentCompleted(e.orderId());
            case PaymentFailed e -> saga.onPaymentFailed(e.orderId(), e.reason());
            case StockReleased e -> saga.onStockReleased(e.orderId());
            default -> log.debug("Ignoring {} on {}", event.eventType(), record.topic());
        }
    }
}
