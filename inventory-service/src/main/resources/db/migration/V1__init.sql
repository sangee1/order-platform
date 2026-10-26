CREATE TABLE inventory_item (
    sku               VARCHAR(64) PRIMARY KEY,
    -- DB-level invariants: even a buggy code path cannot drive stock negative.
    available_quantity INT    NOT NULL CHECK (available_quantity >= 0),
    reserved_quantity  INT    NOT NULL DEFAULT 0 CHECK (reserved_quantity >= 0),
    version            BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE stock_reservation (
    id         UUID PRIMARY KEY,
    order_id   UUID        NOT NULL,
    sku        VARCHAR(64) NOT NULL REFERENCES inventory_item (sku),
    quantity   INT         NOT NULL CHECK (quantity > 0),
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_reservation_order ON stock_reservation (order_id);

-- Idempotent consumer: one row per Kafka message already handled.
CREATE TABLE processed_message (
    message_id   UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox_event (
    id           UUID PRIMARY KEY,
    aggregate_id VARCHAR(64)  NOT NULL,
    event_type   VARCHAR(100) NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
