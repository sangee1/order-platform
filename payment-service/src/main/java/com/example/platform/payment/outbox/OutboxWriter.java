package com.example.platform.payment.outbox;

import com.example.platform.common.events.DomainEvent;
import com.example.platform.common.events.EventCodec;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxWriter {

    private final OutboxRepository repository;

    public OutboxWriter(OutboxRepository repository) {
        this.repository = repository;
    }

    /** MANDATORY: fails fast if called outside a business transaction, which would defeat the pattern. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, DomainEvent event) {
        repository.save(OutboxEvent.of(topic, event.orderId().toString(), event.eventType(), EventCodec.toJson(event)));
    }
}
