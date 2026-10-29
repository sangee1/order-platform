package com.example.platform.payment.api;

import com.example.platform.payment.domain.PaymentRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentRepository payments;

    public PaymentController(PaymentRepository payments) {
        this.payments = payments;
    }

    @GetMapping("/order/{orderId}")
    public PaymentResponse byOrder(@PathVariable UUID orderId) {
        return payments.findByOrderId(orderId).map(PaymentResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No payment for order " + orderId));
    }
}
