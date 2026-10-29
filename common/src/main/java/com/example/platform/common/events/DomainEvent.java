package com.example.platform.common.events;

import java.util.UUID;

/**
 * Closed set of events and commands exchanged between services. Sealed, so a typo'd or forgotten
 * type is caught at compile time. Each topic is named after its producer:
 * order-events (saga commands), inventory-events, payment-events.
 */
public sealed interface DomainEvent
        permits OrderCreated, PaymentRequested, ReleaseStockRequested,
                StockReserved, StockReservationFailed, StockReleased,
                PaymentCompleted, PaymentFailed {

    UUID orderId();

    default String eventType() {
        return getClass().getSimpleName();
    }

    static Class<? extends DomainEvent> classFor(String eventType) {
        return switch (eventType) {
            case "OrderCreated" -> OrderCreated.class;
            case "PaymentRequested" -> PaymentRequested.class;
            case "ReleaseStockRequested" -> ReleaseStockRequested.class;
            case "StockReserved" -> StockReserved.class;
            case "StockReservationFailed" -> StockReservationFailed.class;
            case "StockReleased" -> StockReleased.class;
            case "PaymentCompleted" -> PaymentCompleted.class;
            case "PaymentFailed" -> PaymentFailed.class;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
