package com.example.platform.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** JSON (de)serialisation of events. The event type travels in a Kafka header, not in the payload. */
public final class EventCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EventCodec() {}

    public static String toJson(DomainEvent event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + event.eventType(), e);
        }
    }

    /** Throws IllegalArgumentException (treated as non-retryable, goes straight to the DLT) on bad input. */
    public static DomainEvent fromJson(String eventType, String json) {
        try {
            return MAPPER.readValue(json, DomainEvent.classFor(eventType));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed " + eventType + " payload", e);
        }
    }
}
