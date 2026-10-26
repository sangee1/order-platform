package com.example.platform.inventory.api;

import com.example.platform.inventory.application.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    public record RestockRequest(@Min(1) int quantity) {}

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping
    public List<StockLevel> list() {
        return service.list().stream().map(StockLevel::from).toList();
    }

    @GetMapping("/{sku}")
    public StockLevel get(@PathVariable String sku) {
        return StockLevel.from(service.get(sku));
    }

    @PostMapping("/{sku}/restock")
    public StockLevel restock(@PathVariable String sku, @Valid @RequestBody RestockRequest request) {
        return StockLevel.from(service.restock(sku, request.quantity()));
    }
}
