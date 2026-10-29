# ADR 0007: Resilience around the payment gateway, with "unknown outcome" semantics

**Status:** Accepted

## Context
The gateway is a remote dependency. When it is slow or failing, naive code ties up threads, hammers a struggling provider,
and, worst of all, may record a payment as failed when the charge actually went through.

## Decision
`ResilientPaymentGateway` decorates the gateway: **Retry -> CircuitBreaker -> Bulkhead -> timeout -> call**.
- Only technical failures (`PaymentGatewayException`: timeouts, 5xx) are retried and counted by the breaker.
  A card decline is a normal business answer and is neither retried nor counted.
- Retry is outermost so every attempt feeds the breaker, and an open breaker stops retries immediately.
- The bulkhead caps concurrent gateway calls; the timeout cuts off slow calls.
- When all of that fails the caller sees `PaymentGatewayUnavailableException`, meaning **the outcome is unknown**.
  `PaymentHandler` then records nothing and does not mark the message processed.

Recovery of the unknown outcome is delegated to the saga (ADR 0006): the sweeper re-sends `PaymentRequested`, and the gateway
idempotency key (order id) makes the retry return the original result or perform the first charge.

## Consequences
- A gateway outage can never produce a false "payment failed" and a wrongful cancellation.
- Latency of recovery is the saga timeout, not the Kafka retry interval; acceptable for payments.
- Needs a provider that honours idempotency keys (the mock simulates one).
- `GatewayChaosController` (`/api/admin/gateway`) lets you inject failures and latency at runtime for demos. It is unauthenticated and
  must be removed or secured outside demos.
