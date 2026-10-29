package com.example.platform.order.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Order aggregate and saga state machine. Every transition method returns true when it changed state and
 * false when the event was a harmless duplicate, so the orchestrator knows whether to emit follow-up commands.
 * A transition that makes no sense from the current state throws (the message then ends up in the DLT).
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;
    @Column(nullable = false)
    private String customerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
    @Column(nullable = false)
    private BigDecimal totalAmount;
    private String failureReason;
    private String idempotencyKey;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_item", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderLine> items = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    protected Order() {}

    public static Order place(String customerId, List<OrderLine> lines, String idempotencyKey) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("An order needs at least one line");
        }
        Order o = new Order();
        o.id = UUID.randomUUID();
        o.customerId = customerId;
        o.status = OrderStatus.PENDING;
        o.items = new ArrayList<>(lines);
        o.totalAmount = lines.stream().map(OrderLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        o.idempotencyKey = idempotencyKey;
        o.createdAt = Instant.now();
        o.updatedAt = o.createdAt;
        return o;
    }

    /** Stock is reserved: PENDING -> PAYMENT_PENDING. */
    public boolean awaitPayment() {
        return switch (status) {
            case PENDING -> { moveTo(OrderStatus.PAYMENT_PENDING); yield true; }
            case PAYMENT_PENDING, CONFIRMED, CANCELLING, CANCELLED -> false;
            case REJECTED -> throw illegal("await payment for");
        };
    }

    /** Stock could not be reserved: PENDING -> REJECTED. */
    public boolean reject(String reason) {
        return switch (status) {
            case PENDING -> { failureReason = reason; moveTo(OrderStatus.REJECTED); yield true; }
            case REJECTED -> false;
            default -> throw illegal("reject");
        };
    }

    /** Payment succeeded: PAYMENT_PENDING -> CONFIRMED. */
    public boolean confirm() {
        return switch (status) {
            case PAYMENT_PENDING -> { moveTo(OrderStatus.CONFIRMED); yield true; }
            case CONFIRMED -> false;
            default -> throw illegal("confirm");
        };
    }

    /** Payment failed, stock must be given back: PAYMENT_PENDING -> CANCELLING. */
    public boolean startCancellation(String reason) {
        return switch (status) {
            case PAYMENT_PENDING -> { failureReason = reason; moveTo(OrderStatus.CANCELLING); yield true; }
            case CANCELLING, CANCELLED -> false;
            default -> throw illegal("start cancelling");
        };
    }

    /** Compensation finished: CANCELLING -> CANCELLED. */
    public boolean cancel() {
        return switch (status) {
            case CANCELLING -> { moveTo(OrderStatus.CANCELLED); yield true; }
            case CANCELLED -> false;
            default -> throw illegal("cancel");
        };
    }

    private void moveTo(OrderStatus next) {
        status = next;
        updatedAt = Instant.now();
    }

    private IllegalStateException illegal(String action) {
        return new IllegalStateException("Cannot " + action + " order " + id + " in status " + status);
    }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getFailureReason() { return failureReason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public List<OrderLine> getItems() { return List.copyOf(items); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
