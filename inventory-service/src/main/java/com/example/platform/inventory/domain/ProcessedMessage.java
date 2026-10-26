package com.example.platform.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_message")
public class ProcessedMessage {

    @Id
    private UUID messageId;
    @Column(nullable = false)
    private Instant processedAt;

    protected ProcessedMessage() {}

    public ProcessedMessage(UUID messageId) {
        this.messageId = messageId;
        this.processedAt = Instant.now();
    }

    public UUID getMessageId() { return messageId; }
}
