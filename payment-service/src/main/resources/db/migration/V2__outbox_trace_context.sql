-- W3C traceparent captured when the event was written, so the trace continues when the outbox poller publishes it.
ALTER TABLE outbox_event ADD COLUMN trace_parent VARCHAR(100);
