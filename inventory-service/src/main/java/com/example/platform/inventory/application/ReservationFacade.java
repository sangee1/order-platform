package com.example.platform.inventory.application;

import com.example.platform.common.events.OrderCreated;
import com.example.platform.common.events.ReleaseStockRequested;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Retries when an attempt loses an optimistic-lock race (another order touched the same stock) or a
 * duplicate-message race. Deliberately NOT transactional: each attempt gets a fresh transaction and a
 * fresh read of the stock rows.
 */
@Component
public class ReservationFacade {

    private static final Logger log = LoggerFactory.getLogger(ReservationFacade.class);

    private final ReservationService service;
    private final int maxAttempts;

    public ReservationFacade(ReservationService service, @Value("${reservation.max-attempts:50}") int maxAttempts) {
        this.service = service;
        this.maxAttempts = maxAttempts;
    }

    public void handle(UUID messageId, OrderCreated order) {
        withRetry(order.orderId(), () -> service.reserveForOrder(messageId, order));
    }

    public void handleRelease(UUID messageId, ReleaseStockRequested command) {
        withRetry(command.orderId(), () -> service.releaseForOrder(messageId, command));
    }

    private void withRetry(UUID orderId, Runnable action) {
        for (int attempt = 1; ; attempt++) {
            try {
                action.run();
                return;
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
                if (attempt >= maxAttempts) {
                    throw e;
                }
                log.debug("Conflict handling order {} (attempt {}): {}", orderId, attempt, e.getClass().getSimpleName());
                backoff(attempt);
            }
        }
    }

    private static void backoff(int attempt) {
        try {
            // Jittered, growing backoff so contending threads spread out.
            Thread.sleep(ThreadLocalRandom.current().nextLong(1, 5L * Math.min(attempt, 10) + 1));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying", ie);
        }
    }
}
