package com.example.platform.inventory.application;

import com.example.platform.inventory.domain.InventoryItem;
import com.example.platform.inventory.domain.InventoryItemRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {

    private final InventoryItemRepository items;

    public InventoryService(InventoryItemRepository items) {
        this.items = items;
    }

    @Transactional(readOnly = true)
    public List<InventoryItem> list() {
        return items.findAll();
    }

    @Transactional(readOnly = true)
    public InventoryItem get(String sku) {
        return items.findById(sku).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown SKU " + sku));
    }

    @Transactional
    public InventoryItem restock(String sku, int quantity) {
        InventoryItem item = items.findById(sku).orElseGet(() -> new InventoryItem(sku, 0));
        item.restock(quantity);
        return items.save(item);
    }
}
