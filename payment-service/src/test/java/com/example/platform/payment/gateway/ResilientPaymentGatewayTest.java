package com.example.platform.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilientPaymentGatewayTest {

    private final CircuitBreakerRegistry circuitBreakers = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowSize(4)
            .minimumNumberOfCalls(4)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .recordExceptions(PaymentGatewayException.class)
            .build());
    private final RetryRegistry retries = RetryRegistry.of(RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(1))
            .retryExceptions(PaymentGatewayException.class)
            .build());
    private final BulkheadRegistry bulkheads = BulkheadRegistry.of(BulkheadConfig.custom()
            .maxConcurrentCalls(5)
            .maxWaitDuration(Duration.ZERO)
            .build());

    private ResilientPaymentGateway gateway(PaymentGateway delegate, Duration timeout) {
        return new ResilientPaymentGateway(delegate, circuitBreakers, retries, bulkheads, timeout);
    }

    @Test
    void transientFailuresAreRetriedUntilTheCallSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        PaymentGateway flaky = (key, customer, amount) -> {
            if (calls.incrementAndGet() <= 2) {
                throw new PaymentGatewayException("503");
            }
            return PaymentResult.approved("txn-1");
        };

        PaymentResult result = gateway(flaky, Duration.ofSeconds(1)).charge("o-1", "c-1", BigDecimal.TEN);

        assertThat(result.approved()).isTrue();
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void aCardDeclineIsAnAnswerNotAFailure() {
        AtomicInteger calls = new AtomicInteger();
        PaymentGateway declining = (key, customer, amount) -> {
            calls.incrementAndGet();
            return PaymentResult.declined("Card declined");
        };

        PaymentResult result = gateway(declining, Duration.ofSeconds(1)).charge("o-2", "c-1", BigDecimal.TEN);

        assertThat(result.approved()).isFalse();
        assertThat(calls.get()).isEqualTo(1);   // not retried
        assertThat(circuitBreakers.circuitBreaker("paymentGateway").getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void persistentFailureOpensTheCircuitAndSubsequentCallsFailFast() {
        AtomicInteger calls = new AtomicInteger();
        PaymentGateway down = (key, customer, amount) -> {
            calls.incrementAndGet();
            throw new PaymentGatewayException("503");
        };
        ResilientPaymentGateway gateway = gateway(down, Duration.ofSeconds(1));

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> gateway.charge("o-3", "c-1", BigDecimal.TEN))
                    .isInstanceOf(PaymentGatewayUnavailableException.class);
        }
        assertThat(circuitBreakers.circuitBreaker("paymentGateway").getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int callsBeforeFailFast = calls.get();
        assertThatThrownBy(() -> gateway.charge("o-3", "c-1", BigDecimal.TEN))
                .isInstanceOf(PaymentGatewayUnavailableException.class)
                .hasMessageContaining("Circuit breaker is open");
        assertThat(calls.get()).isEqualTo(callsBeforeFailFast);   // the gateway was not even called
    }

    @Test
    void slowCallsAreCutOffByTheTimeout() {
        PaymentGateway slow = (key, customer, amount) -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PaymentGatewayException("interrupted", e);
            }
            return PaymentResult.approved("txn-late");
        };

        assertThatThrownBy(() -> gateway(slow, Duration.ofMillis(50)).charge("o-4", "c-1", BigDecimal.TEN))
                .isInstanceOf(PaymentGatewayUnavailableException.class)
                .hasMessageContaining("timed out");
    }
}
