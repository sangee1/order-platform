package com.example.platform.order.application;

import com.example.platform.order.domain.OrderRepository;
import com.example.platform.order.domain.OrderStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Exposes how many orders are in each state as the gauge orders.by.status{status=...}. Refreshed on a
 * timer with one GROUP BY query (not per scrape, not per order), and every status is always present, even at 0,
 * so dashboards never show gaps. The label is the status only; order ids would explode metric cardinality.
 */
@Component
public class OrderMetrics {

    private final OrderRepository orders;
    private final MultiGauge gauge;

    public OrderMetrics(OrderRepository orders, MeterRegistry registry) {
        this.orders = orders;
        this.gauge = MultiGauge.builder("orders.by.status")
                .description("Number of orders currently in each saga state")
                .register(registry);
    }

    @Scheduled(fixedDelayString = "${metrics.orders.refresh-ms:5000}")
    public void refresh() {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status, 0L);
        }
        for (OrderRepository.StatusCount row : orders.countGroupedByStatus()) {
            counts.put(OrderStatus.valueOf(row.getStatus()), row.getTotal());
        }

        List<MultiGauge.Row<?>> rows = new ArrayList<>();
        counts.forEach((status, total) -> rows.add(MultiGauge.Row.of(Tags.of("status", status.name()), total)));
        gauge.register(rows, true);
    }
}
