package com.example.platform.payment.gateway;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Decorates the real gateway. From the outside in, one attempt passes through:
 * <pre>Retry -> CircuitBreaker -> Bulkhead -> timeout -> gateway</pre>
 * Retry is outermost so every attempt is seen by the breaker; an open breaker short-circuits retries.
 * If nothing works the caller gets {@link PaymentGatewayUnavailableException} (outcome unknown).
 */
@Primary
@Component
public class ResilientPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(ResilientPaymentGateway.class);
    static final String NAME = "paymentGateway";

    private final PaymentGateway delegate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;
    private final Duration timeout;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ResilientPaymentGateway(@Qualifier("mockPaymentGateway") PaymentGateway delegate,
                                   CircuitBreakerRegistry circuitBreakers,
                                   RetryRegistry retries,
                                   BulkheadRegistry bulkheads,
                                   @Value("${payment.gateway.timeout:PT2S}") Duration timeout) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreakers.circuitBreaker(NAME);
        this.retry = retries.retry(NAME);
        this.bulkhead = bulkheads.bulkhead(NAME);
        this.timeout = timeout;
        this.circuitBreaker.getEventPublisher().onStateTransition(
                event -> log.warn("Payment gateway circuit breaker: {}", event.getStateTransition()));
    }

    @Override
    public PaymentResult charge(String idempotencyKey, String customerId, BigDecimal amount) {
        Supplier<PaymentResult> call = () -> callWithTimeout(idempotencyKey, customerId, amount);
        call = Bulkhead.decorateSupplier(bulkhead, call);
        call = CircuitBreaker.decorateSupplier(circuitBreaker, call);
        call = Retry.decorateSupplier(retry, call);
        try {
            return call.get();
        } catch (CallNotPermittedException e) {
            throw new PaymentGatewayUnavailableException("Circuit breaker is open: failing fast", e);
        } catch (BulkheadFullException e) {
            throw new PaymentGatewayUnavailableException("Too many concurrent gateway calls", e);
        } catch (PaymentGatewayException e) {
            throw new PaymentGatewayUnavailableException("Gateway failed after retries: " + e.getMessage(), e);
        }
    }

    private PaymentResult callWithTimeout(String idempotencyKey, String customerId, BigDecimal amount) {
        Future<PaymentResult> future = executor.submit(() -> delegate.charge(idempotencyKey, customerId, amount));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new PaymentGatewayException("Gateway call timed out after " + timeout.toMillis() + " ms", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof PaymentGatewayException pge) {
                throw pge;
            }
            throw new PaymentGatewayException("Gateway call failed: " + e.getCause(), e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new PaymentGatewayException("Interrupted while waiting for gateway", e);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
