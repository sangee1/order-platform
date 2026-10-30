package com.example.platform.payment.outbox;

import com.example.platform.common.events.DomainEvent;
import com.example.platform.common.events.EventCodec;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxWriter {

    private final OutboxRepository repository;
    private final ObjectProvider<Tracer> tracer;
    private final ObjectProvider<Propagator> propagator;

    public OutboxWriter(OutboxRepository repository, ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        this.repository = repository;
        this.tracer = tracer;
        this.propagator = propagator;
    }

    /** MANDATORY: fails fast if called outside a business transaction, which would defeat the pattern. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(String topic, DomainEvent event) {
        repository.save(OutboxEvent.of(topic, event.orderId().toString(), event.eventType(),
                EventCodec.toJson(event), currentTraceParent()));
    }

    /**
     * The event is published later by a background poller, outside this request's trace. Saving the current
     * trace context with the row lets the poller continue the same trace, so one order is one trace end to end.
     */
    private String currentTraceParent() {
        Tracer t = tracer.getIfAvailable();
        Propagator p = propagator.getIfAvailable();
        if (t == null || p == null) {
            return null;
        }
        Span span = t.currentSpan();
        if (span == null) {
            return null;
        }
        Map<String, String> carrier = new HashMap<>();
        p.inject(span.context(), carrier, (c, key, value) -> c.put(key, value));
        return carrier.get("traceparent");
    }
}
