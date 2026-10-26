# ADR 0002: Optimistic locking (with retry) to prevent overselling

**Status:** Accepted

## Context
Many orders can reserve the same SKU concurrently. Stock must never go negative.

## Decision
`InventoryItem` carries a JPA `@Version`. A conflicting update fails with an optimistic-lock error and
`ReservationFacade` retries in a fresh transaction with jittered backoff (max 50 attempts). A `CHECK
(available_quantity >= 0)` constraint is a last line of defence in the database.

## Consequences
- No row locks held while processing, so reads and unrelated work stay fast.
- Under extreme contention on a single hot SKU, retries waste work. If that becomes real, switch that path to
  `SELECT ... FOR UPDATE` or an atomic `UPDATE ... SET available = available - :q WHERE available >= :q`.
- `ReservationConcurrencyTest` proves the invariant: 25 concurrent orders against 10 units yields exactly 10 reservations.
