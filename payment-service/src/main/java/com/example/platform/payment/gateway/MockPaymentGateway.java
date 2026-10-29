package com.example.platform.payment.gateway;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** In-memory stand-in for a payment provider with provider-style idempotency keys. */
@Component
public class MockPaymentGateway implements PaymentGateway {

    private final BigDecimal declineAbove;
    private final double failureRate;
    private final Map<String, PaymentResult> resultsByKey = new ConcurrentHashMap<>();

    public MockPaymentGateway(@Value("${payment.mock.decline-above:1000.00}") BigDecimal declineAbove,
                              @Value("${payment.mock.failure-rate:0.0}") double failureRate) {
        this.declineAbove = declineAbove;
        this.failureRate = failureRate;
    }

    @Override
    public PaymentResult charge(String idempotencyKey, String customerId, BigDecimal amount) {
        return resultsByKey.computeIfAbsent(idempotencyKey, key -> decide(amount));
    }

    private PaymentResult decide(BigDecimal amount) {
        if (amount.compareTo(declineAbove) > 0) {
            return PaymentResult.declined("Card declined: amount exceeds limit");
        }
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            return PaymentResult.declined("Card declined: issuer unavailable");
        }
        return PaymentResult.approved("txn-" + UUID.randomUUID());
    }
}
