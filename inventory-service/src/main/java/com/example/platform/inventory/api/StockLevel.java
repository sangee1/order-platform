package com.example.platform.inventory.api;

import com.example.platform.inventory.domain.InventoryItem;

public record StockLevel(String sku, int available, int reserved) {
    public static StockLevel from(InventoryItem i) {
        return new StockLevel(i.getSku(), i.getAvailableQuantity(), i.getReservedQuantity());
    }
}
