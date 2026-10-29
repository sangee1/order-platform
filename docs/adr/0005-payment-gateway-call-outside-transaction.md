# ADR 0005: Call the payment gateway outside the database transaction

**Status:** Accepted

## Context
Charging a card is a remote call with unpredictable latency. Making it inside a DB transaction holds a connection
(and row locks) open for the duration, which can exhaust the pool when the provider is slow.

## Decision
`PaymentHandler` calls the gateway first, with the **order id as the provider idempotency key**, and only then opens a short
transaction in `PaymentService.record` to store the payment, write the outbox event and mark the message processed.

## Consequences
- If the process crashes after charging but before committing, Kafka redelivers the message. The retry calls the gateway
  with the same key and receives the original result, so the customer is **not charged twice**.
- Relies on the provider honouring idempotency keys (the mock gateway simulates this).
- A `UNIQUE(order_id)` constraint on `payment` is the last line of defence against a second payment for one order.
- Next: wrap the gateway call in Resilience4j (timeout, retry, circuit breaker); see roadmap.
