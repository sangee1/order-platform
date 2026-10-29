# ADR 0006: Timeout-driven saga recovery

**Status:** Accepted

## Context
The outbox and consumer retries guarantee that messages are eventually delivered, but they cannot help when a
participant is down for a long time, a reply ends up in a DLT, or a command was handled and its reply never arrived.
Without a safety net an order can sit in `PENDING`, `PAYMENT_PENDING` or `CANCELLING` forever.

## Decision
`SagaTimeoutSweeper` runs `SagaRecovery` every few seconds. It measures time in the current state from `orders.updated_at`
(refreshed by every transition) and acts per state:

| State stuck | Action | Why this is safe |
|---|---|---|
| `CANCELLING` | re-send `ReleaseStockRequested`, indefinitely | release is idempotent and always acknowledged; compensation must eventually succeed |
| `PAYMENT_PENDING` | re-send `PaymentRequested` up to 3 times, then expire and compensate | payment-service is idempotent per order and gateway call uses an idempotency key, so no double charge |
| `PENDING` | expire and compensate | the release command queues behind `OrderCreated` on the same partition key, so inventory cannot reserve after releasing |

Each recovery step is one transaction (state change + outbox row). Orders are claimed with
`SELECT ... FOR UPDATE SKIP LOCKED`, so several order-service instances can sweep without double-handling.
Actions are exposed as the Micrometer counter `saga.recovery{action=...}`.

## Consequences
- No order can be stuck permanently; every path ends in `CONFIRMED`, `REJECTED` or `CANCELLED`.
- **Known race (not yet handled):** if a payment completes at the very moment the order is expired, the customer is charged for a
  cancelled order. The late `PaymentCompleted` then fails the state machine and lands in the DLT. The proper fix is a
  *refund* compensation (`RefundRequested`). Listed on the roadmap; generous timeouts and retries make it unlikely, not impossible.
- Timeouts are configuration (`saga.timeout.*`), so tests can fast-forward time by passing `now` into `recoverStuckOrders`.
