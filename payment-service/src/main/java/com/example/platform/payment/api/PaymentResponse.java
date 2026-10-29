package com.example.platform.payment.api;

import com.example.platform.payment.domain.Payment;
import com.example.platform.payment.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(UUID orderId, BigDecimal amount, PaymentStatus status,
                              String transactionId, String failureReason, Instant createdAt) {
    public static PaymentResponse from(Payment p) {
        return new PaymentResponse(p.getOrderId(), p.getAmount(), p.getStatus(),
                p.getTransactionId(), p.getFailureReason(), p.getCreatedAt());
    }
}
