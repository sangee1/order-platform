package com.example.platform.order.application;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically runs {@link SagaRecovery} and exposes what it did as the metric {@code saga.recovery}. */
@Component
@ConditionalOnProperty(name = "saga.sweeper.enabled", havingValue = "true", matchIfMissing = true)
public class SagaTimeoutSweeper {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutSweeper.class);

    private final SagaRecovery recovery;
    private final MeterRegistry meters;

    public SagaTimeoutSweeper(SagaRecovery recovery, MeterRegistry meters) {
        this.recovery = recovery;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${saga.sweeper.interval-ms:15000}")
    public void sweep() {
        SagaRecovery.Report report = recovery.recoverStuckOrders(Instant.now());
        meters.counter("saga.recovery", "action", "expired").increment(report.expired());
        meters.counter("saga.recovery", "action", "payment_retried").increment(report.paymentRetried());
        meters.counter("saga.recovery", "action", "release_resent").increment(report.releaseResent());
        if (!report.isEmpty()) {
            log.warn("Saga recovery acted on stuck orders: {}", report);
        }
    }
}
