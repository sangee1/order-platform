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
    void confirmIsIdempotent() {
        Order o = newOrder();
        o.confirm();
        o.confirm();
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void rejectedOrderCannotBeConfirmed() {
        Order o = newOrder();
        o.reject("out of stock");
        assertThat(o.getFailureReason()).isEqualTo("out of stock");
        assertThatThrownBy(o::confirm).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void emptyOrderIsRejected() {
        assertThatThrownBy(() -> Order.place("c-1", List.of(), null)).isInstanceOf(IllegalArgumentException.class);
    }
}
