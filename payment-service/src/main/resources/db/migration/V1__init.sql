CREATE TABLE payment (
    id             UUID PRIMARY KEY,
    order_id       UUID          NOT NULL UNIQUE,   -- at most one payment per order
    amount         NUMERIC(19,2) NOT NULL,
    status         VARCHAR(20)   NOT NULL,
    transaction_id VARCHAR(100),
    failure_reason VARCHAR(500),
    created_at     TIMESTAMPTZ   NOT NULL
);

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
