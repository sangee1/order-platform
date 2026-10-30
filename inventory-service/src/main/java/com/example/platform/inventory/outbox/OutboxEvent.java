package com.example.platform.inventory.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A row in the transactional outbox: written in the same DB transaction as the business change. */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    private UUID id;
    @Column(nullable = false)
    private String aggregateId;
    @Column(nullable = false)
    private String eventType;
    @Column(nullable = false)
    private String topic;
    @Column(nullable = false, columnDefinition = "text")
    private String payload;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant publishedAt;
    /** W3C traceparent of the request or message that produced this event. */
    private String traceParent;

    protected OutboxEvent() {}

    public static OutboxEvent of(String topic, String aggregateId, String eventType, String payload, String traceParent) {
        OutboxEvent e = new OutboxEvent();
        e.id = UUID.randomUUID();
        e.topic = topic;
        e.aggregateId = aggregateId;
        e.eventType = eventType;
        e.payload = payload;
        e.createdAt = Instant.now();
        e.traceParent = traceParent;
        return e;
    }

    public void markPublished(Instant at) { this.publishedAt = at; }

    public UUID getId() { return id; }
    public String getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getTopic() { return topic; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getTraceParent() { return traceParent; }
}
