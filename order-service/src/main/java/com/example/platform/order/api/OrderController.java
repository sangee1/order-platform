package com.example.platform.order.api;

import com.example.platform.order.application.OrderService;
import com.example.platform.order.domain.Order;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    /**
     * Asynchronous: returns 202 with status PENDING; poll GET /api/orders/{id} for the outcome.
     * Send an Idempotency-Key header to make client retries safe.
     */
    @PostMapping
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request,
                                                @RequestHeader(name = "Idempotency-Key", required = false) String key) {
        try {
            return accepted(service.place(request.customerId(), request.toLines(), key));
        } catch (DataIntegrityViolationException e) {
            // Two concurrent requests with the same key: the unique index let one win; return its order.
            if (key != null) {
                return service.findByIdempotencyKey(key).map(this::accepted).orElseThrow(() -> e);
            }
            throw e;
        }
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return OrderResponse.from(service.get(id));
    }

    private ResponseEntity<OrderResponse> accepted(Order order) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(OrderResponse.from(order));
    }
}
