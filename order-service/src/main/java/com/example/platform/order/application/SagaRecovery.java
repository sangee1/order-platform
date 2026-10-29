package com.example.platform.order.application;

import com.example.platform.common.events.PaymentRequested;
import com.example.platform.common.events.ReleaseStockRequested;
import com.example.platform.common.events.Topics;
import com.example.platform.order.domain.Order;
import com.example.platform.order.domain.OrderRepository;
import com.example.platform.order.domain.OrderStatus;
import com.example.platform.order.outbox.OutboxWriter;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rescues orders whose saga stopped making progress (a reply was lost, a service was down...).
 * Time in the current state is measured from {@code updated_at}, which every transition refreshes.
 *
 * <ul>
 *   <li>CANCELLING too long: re-send ReleaseStockRequested. Compensation must eventually succeed,
 *       so it is retried indefinitely (it is idempotent).</li>
 *   <li>PAYMENT_PENDING too long: re-send PaymentRequested (payment-service is idempotent per order,
 *       so this can never charge twice) up to N attempts, then give up and compensate.</li>
 *   <li>PENDING too long: stock outcome unknown, so compensate. The release command is queued behind
 *       OrderCreated on the same partition, so inventory can never reserve after releasing.</li>
 * </ul>
 */
@Service
public class SagaRecovery {

    public record Report(int expired, int paymentRetried, int releaseResent) {
        public boolean isEmpty() {
            return expired == 0 && paymentRetried == 0 && releaseResent == 0;
        }
    }

    private final OrderRepository orders;
    private final OutboxWriter outbox;
    private final Duration pendingTimeout;
    private final Duration paymentTimeout;
    private final Duration cancellingTimeout;
    private final int maxPaymentAttempts;
    private final int batchSize;

    public SagaRecovery(OrderRepository orders, OutboxWriter outbox,
                        @Value("${saga.timeout.pending:PT2M}") Duration pendingTimeout,
                        @Value("${saga.timeout.payment:PT1M}") Duration paymentTimeout,
                        @Value("${saga.timeout.cancelling:PT1M}") Duration cancellingTimeout,
                        @Value("${saga.timeout.max-payment-attempts:3}") int maxPaymentAttempts,
                        @Value("${saga.sweeper.batch-size:50}") int batchSize) {
        this.orders = orders;
        this.outbox = outbox;
        this.pendingTimeout = pendingTimeout;
        this.paymentTimeout = paymentTimeout;
        this.cancellingTimeout = cancellingTimeout;
        this.maxPaymentAttempts = maxPaymentAttempts;
        this.batchSize = batchSize;
    }

    /** {@code now} is a parameter so tests can fast-forward time. One transaction: state changes + outbox rows. */
    @Transactional
    public Report recoverStuckOrders(Instant now) {
        int resent = 0;
        int retried = 0;
        int expired = 0;

        // Order matters: handle CANCELLING first so orders expired later in this same sweep
        // are not immediately picked up again.
        for (Order order : orders.lockStuck(OrderStatus.CANCELLING.name(), now.minus(cancellingTimeout), batchSize)) {
            order.markCompensationRetried();
            outbox.append(Topics.ORDER_EVENTS, new ReleaseStockRequested(order.getId(), order.getFailureReason()));
            resent++;
        }

        for (Order order : orders.lockStuck(OrderStatus.PAYMENT_PENDING.name(), now.minus(paymentTimeout), batchSize)) {
            if (order.retryPayment(maxPaymentAttempts)) {
                outbox.append(Topics.ORDER_EVENTS,
                        new PaymentRequested(order.getId(), order.getCustomerId(), order.getTotalAmount()));
                retried++;
            } else if (order.expire("Payment timed out after " + order.getPaymentAttempts() + " attempts")) {
                outbox.append(Topics.ORDER_EVENTS, new ReleaseStockRequested(order.getId(), order.getFailureReason()));
                expired++;
            }
        }

        for (Order order : orders.lockStuck(OrderStatus.PENDING.name(), now.minus(pendingTimeout), batchSize)) {
            if (order.expire("Timed out waiting for stock reservation")) {
                outbox.append(Topics.ORDER_EVENTS, new ReleaseStockRequested(order.getId(), order.getFailureReason()));
                expired++;
            }
        }

        return new Report(expired, retried, resent);
    }
}
