package com.example.platform.order.outbox;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.example.platform.common.events.EventHeaders;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the outbox and relays rows to Kafka. Delivery is at-least-once: if the process dies
 * between send and commit the row is sent again, which is why consumers are idempotent.
 * Each send continues the trace that was active when the event was written.
 */
@Component
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectProvider<Tracer> tracer;
    private final ObjectProvider<Propagator> propagator;
    private final int batchSize;

    public OutboxPublisher(OutboxRepository repository, KafkaTemplate<String, String> kafka,
                           ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator,
                           @Value("${outbox.publisher.batch-size:100}") int batchSize) {
        this.repository = repository;
        this.kafka = kafka;
        this.tracer = tracer;
        this.propagator = propagator;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.poll-interval-ms:500}")
    @Transactional
    public void publishPending() {
        for (OutboxEvent event : repository.lockNextBatch(batchSize)) {
            try {
                var record = new ProducerRecord<>(event.getTopic(), event.getAggregateId(), event.getPayload());
                record.headers().add(EventHeaders.EVENT_TYPE, event.getEventType().getBytes(UTF_8));
                record.headers().add(EventHeaders.MESSAGE_ID, event.getId().toString().getBytes(UTF_8));
                send(event, record);
                event.markPublished(Instant.now());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                // Stop the batch but still commit what was sent; the rest is retried on the next poll.
                log.warn("Outbox publish failed for event {}: {}", event.getId(), e.toString());
                return;
            }
        }
    }

    private void send(OutboxEvent event, ProducerRecord<String, String> record) throws Exception {
        Tracer t = tracer.getIfAvailable();
        Propagator p = propagator.getIfAvailable();
        if (t == null || p == null || event.getTraceParent() == null) {
            kafka.send(record).get(10, TimeUnit.SECONDS);
            return;
        }
        // Rebuild the original trace context, open a span under it, and send inside that span: the Kafka
        // producer span (and, via record headers, the consumer's span) then become part of the same trace.
        Span span = p.extract(Map.of("traceparent", event.getTraceParent()), (carrier, key) -> carrier.get(key))
                .name("outbox relay " + event.getEventType())
                .start();
        try (Tracer.SpanInScope ignored = t.withSpan(span)) {
            kafka.send(record).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
