package com.example.platform.payment.outbox;

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

    protected OutboxEvent() {}

    public static OutboxEvent of(String topic, String aggregateId, String eventType, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.id = UUID.randomUUID();
        e.topic = topic;
        e.aggregateId = aggregateId;
        e.eventType = eventType;
        e.payload = payload;
        e.createdAt = Instant.now();
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
}
