package com.example.platform.payment.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Publishes the outbox backlog as the gauge outbox.pending. A growing value means Kafka publishing is stuck. */
@Component
class OutboxMetrics {

    OutboxMetrics(MeterRegistry registry, OutboxRepository repository) {
        Gauge.builder("outbox.pending", repository, OutboxRepository::countByPublishedAtIsNull)
                .description("Outbox rows not yet published to Kafka")
                .register(registry);
    }
}
