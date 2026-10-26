# ADR 0001: Transactional outbox for publishing events

**Status:** Accepted

## Context
Placing an order must update PostgreSQL *and* publish `OrderCreated` to Kafka. Doing both directly is a
dual write: a crash between them either loses the event (order stuck in PENDING forever) or publishes an
event for an order that was rolled back.

## Decision
Services append events to an `outbox_event` table in the **same transaction** as the business change.
A poller (`OutboxPublisher`) relays unpublished rows to Kafka using `SELECT ... FOR UPDATE SKIP LOCKED`
so multiple instances can poll safely. `OutboxWriter` uses `Propagation.MANDATORY` so it cannot be called
outside a transaction.

## Consequences
- Delivery is **at-least-once**; every consumer must be idempotent (see inventory `processed_message`).
- Adds publish latency (poll interval, 500 ms by default) and a polling query.
- Ordering is per aggregate key (`orderId`) only on a single publisher; with several publishers, rows for the
  same order could interleave. Acceptable here because each order produces a single event per step.
- Alternative considered: CDC with Debezium. Lower latency and no polling, but more infrastructure. Revisit at scale.
