package com.example.platform.inventory.application;

import com.example.platform.common.events.OrderCreated;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Retries a reservation when it loses an optimistic-lock race (another order took the same stock) or a
 * duplicate-message race. Deliberately NOT transactional: each attempt gets a fresh transaction and a
 * fresh read of the stock row.
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
        for (int attempt = 1; ; attempt++) {
            try {
                service.reserveForOrder(messageId, order);
                return;
            } catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
                if (attempt >= maxAttempts) {
                    throw e;
                }
                log.debug("Conflict reserving stock for order {} (attempt {}): {}", order.orderId(), attempt, e.getClass().getSimpleName());
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
            throw new IllegalStateException("Interrupted while retrying reservation", ie);
        }
    }
}
