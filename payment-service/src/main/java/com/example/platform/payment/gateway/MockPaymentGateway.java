package com.example.platform.payment.gateway;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * In-memory stand-in for a payment provider. Supports provider-style idempotency keys, deterministic
 * business declines, and injectable technical faults/latency so resilience behaviour can be demonstrated.
 */
@Component("mockPaymentGateway")
public class MockPaymentGateway implements PaymentGateway {

    private final BigDecimal declineAbove;
    private final double failureRate;
    private volatile double transientFailureRate;
    private volatile long latencyMs;
    private final Map<String, PaymentResult> resultsByKey = new ConcurrentHashMap<>();

    public MockPaymentGateway(@Value("${payment.mock.decline-above:1000.00}") BigDecimal declineAbove,
                              @Value("${payment.mock.failure-rate:0.0}") double failureRate,
                              @Value("${payment.mock.transient-failure-rate:0.0}") double transientFailureRate,
                              @Value("${payment.mock.latency-ms:0}") long latencyMs) {
        this.declineAbove = declineAbove;
        this.failureRate = failureRate;
        this.transientFailureRate = transientFailureRate;
        this.latencyMs = latencyMs;
    }

    @Override
    public PaymentResult charge(String idempotencyKey, String customerId, BigDecimal amount) {
        simulateLatency();
        if (ThreadLocalRandom.current().nextDouble() < transientFailureRate) {
            throw new PaymentGatewayException("Gateway returned 503 Service Unavailable");
        }
        return resultsByKey.computeIfAbsent(idempotencyKey, key -> decide(amount));
    }

    public void configure(double transientFailureRate, long latencyMs) {
        this.transientFailureRate = transientFailureRate;
        this.latencyMs = latencyMs;
    }

    public double getTransientFailureRate() { return transientFailureRate; }
    public long getLatencyMs() { return latencyMs; }

    private void simulateLatency() {
        long ms = latencyMs;
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("Interrupted while calling gateway", e);
        }
    }

    private PaymentResult decide(BigDecimal amount) {
        if (amount.compareTo(declineAbove) > 0) {
            return PaymentResult.declined("Card declined: amount exceeds limit");
        }
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            return PaymentResult.declined("Card declined: issuer refused");
        }
        return PaymentResult.approved("txn-" + UUID.randomUUID());
    }
}
