CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    customer_id     VARCHAR(100)  NOT NULL,
    status          VARCHAR(30)   NOT NULL,
    total_amount    NUMERIC(19,2) NOT NULL,
    failure_reason  VARCHAR(500),
    idempotency_key VARCHAR(100)  UNIQUE,
    created_at      TIMESTAMPTZ   NOT NULL,
    updated_at      TIMESTAMPTZ   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0
);

CREATE TABLE order_item (
    order_id   UUID          NOT NULL REFERENCES orders (id),
    sku        VARCHAR(64)   NOT NULL,
    quantity   INT           NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19,2) NOT NULL
);
CREATE INDEX idx_order_item_order ON order_item (order_id);

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
