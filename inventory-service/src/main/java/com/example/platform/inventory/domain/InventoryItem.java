package com.example.platform.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Stock for one SKU. The @Version column is the oversell guard: two transactions that read the same
 * row and both try to decrement it cannot both commit; the loser gets an optimistic-lock failure and retries.
 */
@Entity
@Table(name = "inventory_item")
public class InventoryItem {

    @Id
    private String sku;
    @Column(nullable = false)
    private int availableQuantity;
    @Column(nullable = false)
    private int reservedQuantity;
    @Version
    private Long version;

    protected InventoryItem() {}

    public InventoryItem(String sku, int availableQuantity) {
        this.sku = sku;
        this.availableQuantity = availableQuantity;
    }

    public boolean canReserve(int quantity) {
        return availableQuantity >= quantity;
    }

    public void reserve(int quantity) {
        if (!canReserve(quantity)) {
            throw new IllegalStateException("Insufficient stock for " + sku);
        }
        availableQuantity -= quantity;
        reservedQuantity += quantity;
    }

    public void restock(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        availableQuantity += quantity;
    }

    public String getSku() { return sku; }
    public int getAvailableQuantity() { return availableQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
}
