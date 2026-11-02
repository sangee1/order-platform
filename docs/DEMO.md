# Demo script (about 6 minutes)

A walkthrough for interviews and reviews. Everything here uses what is in the repo.

## Before you start (do this 10 minutes ahead)

1. `docker compose pull && docker compose up --no-build -d`, then wait about a minute.
2. Run `scripts/demo.ps1` (or `scripts/demo.sh`) once. This warms the JVMs and Kafka clients so the live run is fast.
3. Open three tabs: Grafana http://localhost:3000, Jaeger http://localhost:16686, and the README diagram.
4. Open one trace in Jaeger and check it really spans order-, inventory- and payment-service. If it does not, do not claim end-to-end tracing.
5. Have `ReservationConcurrencyTest` open in the IDE.

## The walkthrough

| Time | Show | Say |
|---|---|---|
| 0:30 | README architecture diagram | Three services, each with its own database. Order-service orchestrates a saga. Every message goes through a transactional outbox. |
| 1:30 | `demo.ps1 -Scenario happy`, then the trace in Jaeger | One order is one trace across three services and four Kafka hops. The trace survives the outbox because each row stores the W3C traceparent. |
| 1:00 | `demo.ps1 -Scenario declined` | Payment was declined, so the saga released the reserved stock. Compensation is idempotent and always acknowledged. |
| 1:30 | `demo.ps1 -Scenario outage` and Grafana | The gateway is down: retries, then the circuit breaker opens. An unknown outcome is never recorded as a failure. When the gateway returns, the saga's timeout sweeper re-asks and the idempotency key prevents a double charge. |
| 0:30 | `ReservationConcurrencyTest` | 25 concurrent orders for 10 units gives exactly 10 reservations. Optimistic locking plus a CHECK constraint. |
| 0:30 | README "Known limitations" | Name the gaps yourself: refund race, no auth, single broker. |

## Likely questions, and honest answers

**Why an outbox instead of publishing to Kafka directly?**
A direct publish is a dual write: a crash between the DB commit and the send loses the event or sends one for a rolled-back change. The outbox row commits with the business change; a poller relays it afterwards.

**Is this exactly-once?**
No. Delivery is at-least-once, and consumers are idempotent (a processed-message table in inventory and payment, state-machine guards in the order). The effect is exactly-once.

**Why orchestration rather than choreography?**
With three participants and a compensating step, a single orchestrator makes the flow readable, testable and observable. The cost is that order-service knows every step (ADR 0004).

**What if order-service crashes mid-saga?**
State lives in the database and commands live in the outbox, so it resumes on restart. If a reply is lost, the timeout sweeper re-sends commands (ADR 0006).

**How do you avoid charging twice?**
Three layers: payment is processed once per message id, there is a unique constraint on order id, and the gateway call uses the order id as an idempotency key.

**What happens if the gateway times out after charging?**
The outcome is unknown, so we record nothing. The saga re-requests, and the same idempotency key returns the original result (ADR 0007).

**What is not handled?**
A payment that completes at the instant its order expires leaves a charged, cancelled order. The fix is a refund compensation (ADR 0006). Also: no authentication, a single Kafka broker, and the mock gateway is not a real provider.

**How would you scale it?**
Partition by order id (already the message key), run more consumers, and run several order-service instances: the sweeper and outbox poller already use `SKIP LOCKED`. The first limits would be the single Postgres per service and hot SKUs under optimistic locking (ADR 0002 notes the switch to an atomic UPDATE).
