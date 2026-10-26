package com.example.platform.common.events;

public final class EventHeaders {
    public static final String EVENT_TYPE = "eventType";
    /** Outbox row id: stable across redeliveries, so consumers can de-duplicate on it. */
    public static final String MESSAGE_ID = "messageId";

    private EventHeaders() {}
}
