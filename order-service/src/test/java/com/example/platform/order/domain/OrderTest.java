package com.example.platform.order.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTest {

    private Order newOrder() {
        return Order.place("c-1", List.of(
                new OrderLine("A", 2, new BigDecimal("10.00")),
                new OrderLine("B", 1, new BigDecimal("5.50"))), null);
    }

    @Test
    void totalIsSumOfLines() {
        assertThat(newOrder().getTotalAmount()).isEqualByComparingTo("25.50");
    }

    @Test
    void happyPathTransitionsAreIdempotent() {
        Order o = newOrder();

        assertThat(o.awaitPayment()).isTrue();
        assertThat(o.awaitPayment()).isFalse();   // duplicate StockReserved
        assertThat(o.confirm()).isTrue();
        assertThat(o.confirm()).isFalse();        // duplicate PaymentCompleted
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(o.awaitPayment()).isFalse();   // late duplicate after completion is ignored
    }

    @Test
    void paymentFailureLeadsToCancelledViaCancelling() {
        Order o = newOrder();
        o.awaitPayment();

        assertThat(o.startCancellation("declined")).isTrue();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLING);
        assertThat(o.startCancellation("declined")).isFalse();
        assertThat(o.cancel()).isTrue();
        assertThat(o.cancel()).isFalse();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(o.getFailureReason()).isEqualTo("declined");
    }

    @Test
    void stockFailureRejectsAndCannotProceedToPayment() {
        Order o = newOrder();
        assertThat(o.reject("out of stock")).isTrue();

        assertThat(o.getFailureReason()).isEqualTo("out of stock");
        assertThatThrownBy(o::awaitPayment).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotConfirmWithoutPaymentStep() {
        assertThatThrownBy(() -> newOrder().confirm()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emptyOrderIsRejected() {
        assertThatThrownBy(() -> Order.place("c-1", List.of(), null)).isInstanceOf(IllegalArgumentException.class);
    }
}
