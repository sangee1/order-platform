package com.example.platform.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MockPaymentGatewayTest {

    private final MockPaymentGateway gateway = new MockPaymentGateway(new BigDecimal("1000.00"), 0.0, 0.0, 0);

    @Test
    void approvesAmountsWithinLimit() {
        PaymentResult r = gateway.charge("order-1", "c-1", new BigDecimal("999.99"));
        assertThat(r.approved()).isTrue();
        assertThat(r.transactionId()).startsWith("txn-");
    }

    @Test
    void declinesAmountsAboveLimit() {
        PaymentResult r = gateway.charge("order-2", "c-1", new BigDecimal("1000.01"));
        assertThat(r.approved()).isFalse();
        assertThat(r.declineReason()).contains("limit");
    }

    @Test
    void sameIdempotencyKeyReturnsOriginalOutcomeEvenIfRandomnessWouldDiffer() {
        var flaky = new MockPaymentGateway(new BigDecimal("1000.00"), 0.5, 0.0, 0);
        PaymentResult first = flaky.charge("order-3", "c-1", BigDecimal.TEN);
        for (int i = 0; i < 20; i++) {
            assertThat(flaky.charge("order-3", "c-1", BigDecimal.TEN)).isEqualTo(first);
        }
    }

    @Test
    void technicalFaultsCanBeInjectedAndHealedAtRuntime() {
        gateway.configure(1.0, 0);
        assertThatThrownBy(() -> gateway.charge("order-4", "c-1", BigDecimal.TEN))
                .isInstanceOf(PaymentGatewayException.class);

        gateway.configure(0.0, 0);
        assertThat(gateway.charge("order-4", "c-1", BigDecimal.TEN).approved()).isTrue();
    }
}
