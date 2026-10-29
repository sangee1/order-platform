package com.example.platform.common.events;

public final class Topics {
    /** Produced by order-service (the saga orchestrator): OrderCreated, PaymentRequested, ReleaseStockRequested. */
    public static final String ORDER_EVENTS = "order-events";
    /** Produced by inventory-service: StockReserved, StockReservationFailed, StockReleased. */
    public static final String INVENTORY_EVENTS = "inventory-events";
    /** Produced by payment-service: PaymentCompleted, PaymentFailed. */
    public static final String PAYMENT_EVENTS = "payment-events";

    private Topics() {}
}
