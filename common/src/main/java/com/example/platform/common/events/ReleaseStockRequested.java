package com.example.platform.common.events;

import java.util.UUID;

/** Compensating command: give back any stock reserved for this order. Safe to run more than once. */
public record ReleaseStockRequested(UUID orderId, String reason) implements DomainEvent {}
