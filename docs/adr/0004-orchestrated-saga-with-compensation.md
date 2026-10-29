# ADR 0004: Orchestrated saga with compensation

**Status:** Accepted (supersedes ADR 0003)

## Context
Order fulfilment now spans three services: reserve stock, charge payment, and, if payment fails, give the stock
back. There is no distributed transaction across the three databases, so consistency must come from a saga.
With three participants and a compensating step, a web of services reacting to each other is hard to follow, test and debug.

## Decision
`order-service` hosts an **orchestrator** (`OrderSaga`) and owns the saga state in the `orders.status` column:

```
PENDING -> PAYMENT_PENDING -> CONFIRMED
   |             |
   |             +-> CANCELLING -> CANCELLED      (payment failed, stock released)
   +-> REJECTED                                   (stock unavailable)
```

- Each step is **one local transaction**: change the order state and write the next command to the outbox.
- The orchestrator emits a command **only when the state really changed** (`Order` transition methods return a boolean),
  so redelivered events can never trigger a second payment or a second release.
- Compensation (`ReleaseStockRequested`) is idempotent and **always acknowledged** with `StockReleased`, even if there was
  nothing to release, because the orchestrator waits in `CANCELLING` for that reply.
- Topics are named after their producer: `order-events` (OrderCreated and commands), `inventory-events`, `payment-events`.
- All events remain in one sealed `DomainEvent` hierarchy; listeners use a `default` branch for events meant for others.

## Consequences
- The whole flow is readable in one class and covered by integration tests per path.
- order-service now knows about every step. Adding a step means changing the orchestrator.
- An order stuck in `PAYMENT_PENDING` or `CANCELLING` (a lost reply) needs a timeout/sweeper. **Not built yet.**
- Trade-off taken: one sealed hierarchy loses compile-time exhaustiveness in listeners (they use `default`) in exchange for a single event model.
