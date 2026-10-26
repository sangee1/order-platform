package com.example.platform.common.events;

import java.util.UUID;

/**
 * Closed set of events exchanged between services. Being sealed, every consumer's
 * switch over it is checked for exhaustiveness at compile time.
 */
public sealed interface DomainEvent permits OrderCreated, StockReserved, StockReservationFailed {

    UUID orderId();

    default String eventType() {
        return getClass().getSimpleName();
    }

    static Class<? extends DomainEvent> classFor(String eventType) {
        return switch (eventType) {
            case "OrderCreated" -> OrderCreated.class;
            case "StockReserved" -> StockReserved.class;
            case "StockReservationFailed" -> StockReservationFailed.class;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
