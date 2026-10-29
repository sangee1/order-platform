package com.example.platform.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment")
public class Payment {

    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID orderId;
    @Column(nullable = false)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
    private String transactionId;
    private String failureReason;
    @Column(nullable = false)
    private Instant createdAt;

    protected Payment() {}

    private Payment(UUID orderId, BigDecimal amount, PaymentStatus status, String transactionId, String failureReason) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.amount = amount;
        this.status = status;
        this.transactionId = transactionId;
        this.failureReason = failureReason;
        this.createdAt = Instant.now();
    }

    public static Payment completed(UUID orderId, BigDecimal amount, String transactionId) {
        return new Payment(orderId, amount, PaymentStatus.COMPLETED, transactionId, null);
    }

    public static Payment failed(UUID orderId, BigDecimal amount, String reason) {
        return new Payment(orderId, amount, PaymentStatus.FAILED, null, reason);
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getTransactionId() { return transactionId; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
}
