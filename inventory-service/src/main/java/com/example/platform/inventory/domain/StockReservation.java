package com.example.platform.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_reservation")
public class StockReservation {

    @Id
    private UUID id;
    @Column(nullable = false)
    private UUID orderId;
    @Column(nullable = false)
    private String sku;
    @Column(nullable = false)
    private int quantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;
    @Column(nullable = false)
    private Instant createdAt;

    protected StockReservation() {}

    public StockReservation(UUID orderId, String sku, int quantity) {
        this.id = UUID.randomUUID();
        this.orderId = orderId;
        this.sku = sku;
        this.quantity = quantity;
        this.status = ReservationStatus.RESERVED;
        this.createdAt = Instant.now();
    }

    public void release() {
        this.status = ReservationStatus.RELEASED;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
}
