# ADR 0003: Start with choreography, move to orchestration when payment is added

**Status:** Superseded by [ADR 0004](0004-orchestrated-saga-with-compensation.md)

## Context
Today the flow has two participants (order, inventory), so services simply react to each other's events.

## Decision
Use event choreography now: no central coordinator, minimal moving parts. When payment-service is added the
flow gains ordering and compensation (release stock if payment fails). At that point an explicit saga state
machine owned by order-service becomes easier to reason about, observe and test than a web of reactions.

## Consequences
- Simple and decoupled today; the flow is implicit across listeners.
- Planned: introduce `OrderSaga` (orchestrator) and compensating `ReleaseStock` command with the payment step.
