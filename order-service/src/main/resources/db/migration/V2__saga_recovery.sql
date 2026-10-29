-- How many times payment has been requested for this order (used by the saga timeout sweeper).
ALTER TABLE orders ADD COLUMN payment_attempts INT NOT NULL DEFAULT 0;

-- The sweeper only ever looks at orders that are still "in flight".
CREATE INDEX idx_orders_in_flight ON orders (status, updated_at)
    WHERE status IN ('PENDING', 'PAYMENT_PENDING', 'CANCELLING');
