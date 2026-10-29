package com.example.platform.payment.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.example.platform.common.events.DomainEvent;
import com.example.platform.common.events.EventCodec;
import com.example.platform.common.events.EventHeaders;
import com.example.platform.common.events.PaymentRequested;
import com.example.platform.common.events.Topics;
import com.example.platform.payment.application.PaymentHandler;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
class PaymentRequestListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentRequestListener.class);

    private final PaymentHandler handler;

    PaymentRequestListener(PaymentHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS)
    void on(ConsumerRecord<String, String> record) {
        String type = header(record, EventHeaders.EVENT_TYPE);
        UUID messageId = UUID.fromString(header(record, EventHeaders.MESSAGE_ID));
        DomainEvent event = EventCodec.fromJson(type, record.value());

        switch (event) {
            case PaymentRequested e -> handler.handle(messageId, e);
            default -> log.debug("Ignoring {} on {}", event.eventType(), Topics.ORDER_EVENTS);
        }
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header h = record.headers().lastHeader(name);
        if (h == null) {
            throw new IllegalArgumentException("Missing " + name + " header");
        }
        return new String(h.value(), UTF_8);
    }
}
