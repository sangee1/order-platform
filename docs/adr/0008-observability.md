# ADR 0008: Observability with OpenTelemetry traces, Prometheus metrics and trace-aware outbox

**Status:** Accepted

## Context
One order touches three services and four Kafka hops, and part of the flow is asynchronous. When something is slow or stuck,
logs alone cannot say where. We need to answer: where did this order stop, is the system healthy, and is anything piling up?

## Decision
- **Traces:** Micrometer Tracing with the OpenTelemetry bridge, exported over OTLP to Jaeger. Spring Kafka observation is enabled on
  both producer and consumer, so trace context travels in Kafka record headers (W3C `traceparent`).
- **Trace context through the outbox.** The event is published by a background poller, long after the HTTP request ended, so the
  request's trace would normally be lost at that point. `OutboxWriter` stores the current `traceparent` in the outbox row
  (`trace_parent` column) and `OutboxPublisher` restores it: it opens an "outbox relay" span under the stored context and sends inside
  it. Result: one order is one trace, from `POST /api/orders` through every service and back.
- **Metrics:** Micrometer to Prometheus, with a provisioned Grafana dashboard. Every metric carries an `application` tag.
- **Custom metrics** that describe the saga rather than the JVM:
  `orders.by.status` (gauge per state), `outbox.pending` (backlog per service), `kafka.dead.letters` (counter per topic),
  `saga.recovery` (counter per action). Resilience4j and Kafka client metrics are picked up automatically.
- **Logs** include `[traceId,spanId]` so a log line can be matched to its trace.

## Consequences
- A stuck order is findable in Jaeger by its trace; a stuck system shows up on the dashboard (rising outbox backlog, non-zero DLT,
  orders piling in PAYMENT_PENDING or CANCELLING, open circuit breaker).
- Metric labels are deliberately low-cardinality (status, topic, action). Order ids are never labels; they belong in traces and logs.
- Sampling is 100% for the demo (`management.tracing.sampling.probability`). Production should sample a fraction and keep errors.
- `orders.by.status` is refreshed on a timer with one GROUP BY query, not computed per scrape.
- The Jaeger all-in-one image keeps traces in memory only: fine for a demo, not for production (use Tempo or a persistent Jaeger backend).
- Not yet covered: alert rules, and tracing of the Kafka consumer lag per partition.
