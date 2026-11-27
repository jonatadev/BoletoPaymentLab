# UC-BPL-002 — Manage the educational boleto lifecycle

The authorized release retains UC-BPL-001, including idempotency, PDF on demand
and existing status codes. Existing JSON response fields retain their meaning.
Responses add `status` and `issuedAt`.

| Request | Scope | Result |
| --- | --- | --- |
| POST /api/v1/boletos | boletos:issue | Existing 201 or 200 replay |
| GET /api/v1/boletos/{id} and /{id}/pdf | boletos:read | Existing 200 and 404 |
| GET /api/v1/boletos | boletos:list | Paged result with content, page, size, totalElements and totalPages |
| POST /api/v1/boletos/{id}/payments | boletos:pay | 200 with updated boleto |
| POST /api/v1/boletos/{id}/cancellations | boletos:cancel | 200 with updated boleto |
| GET /api/v1/boletos/{id}/history | boletos:audit | Ordered audit history |
| GET /actuator/metrics and /actuator/metrics/{name} | ops:metrics | Operational metrics |

Health probes expose status only. Other endpoints default to denied. A bearer
token requires valid signature, issuer, audience and timestamps. Missing or
invalid credentials return 401. Insufficient scope returns 403. Scopes are
laboratory-wide for synthetic data. There is no tenant ownership model.

A boleto starts EMITIDO. Payment and cancellation are simulated terminal
transitions from EMITIDO. A date strictly after the original due date in
America/Sao_Paulo makes an EMITIDO boleto VENCIDO. A paid or canceled boleto
keeps its terminal state. Detail, history and command access materialize a
pending expiry. Listing immediately evaluates effective status without writes,
and a bounded sweep reconciles untouched records. Conflicting transitions
return 409 `INVALID_STATE_TRANSITION`. PostgreSQL row locks serialize commands.

List query parameters are `customerId`, `status`, `from`, `to`, `page`,
`size` and `sort=issuedAt,desc`. Dates are inclusive São Paulo calendar dates
over issuedAt. Page starts at zero, size defaults to 20 and is limited to 100.
Offsets are limited to 100,000. Sort fields are issuedAt, dueDate, amount and
id. A UUID tie-breaker makes pages deterministic.

Audit entries contain event ID, time, source (API or SYSTEM), old/new state
and correlation ID. State, audit and outbox commit together. A pending outbox
row is claimed with PostgreSQL SKIP LOCKED. Publish using aggregate ID as
Kafka key and preserve per-aggregate publication order. Events contain
eventId, eventType, aggregateId, timestamp, version, aggregateVersion and
correlationId. Kafka acknowledgement precedes marking a row published.
Failures reschedule with bounded exponential backoff and jitter. A crash
after acknowledgement can cause redelivery. Consumers must deduplicate.
The educational consumer uses a permanent event ID receipt. Delivery is
at-least-once across PostgreSQL and Kafka.

| Scenario | Expected outcome |
| --- | --- |
| S14 | Competing payment and cancellation produce one terminal state, one transition audit and one transition event. |
| S15 | After the São Paulo due date, filters show VENCIDO immediately. Access materializes expiry once and commands return 409. |
| S16 | Customer, state and inclusive issuance dates filter correctly. Ordering is stable and invalid page or sort parameters return 400. |
| S17 | Issuance and changes produce ordered audit history. Rollback removes corresponding audit and outbox writes. |
| S18 | Kafka failure leaves a committed event pending. Retrying after availability publishes it without replaying the boleto command. |
| S19 | Repeat Kafka delivery of one event creates one processed receipt. |
| S20 | Missing or invalid JWT returns 401, insufficient scopes return 403, and an authorized request succeeds. |
| S21 | In the observability profile, Prometheus scraping requires ops:metrics. Count committed issuances, payments and cancellations once per transition. Replays, rejected transitions and rolled-back writes do not increase completion counters. Expose operation timings and the unpublished outbox backlog without identifiers. A traced request correlates logs and spans. API success is independent of an unavailable OTLP exporter. |

See [S22–S25](../boleto-revisions.md) for technical entity revisions.

S21 distributed tracing also covers a synthetic HTTP issuance. Follow its sampled
W3C trace through persistence, the committed outbox record, Kafka publication and
the listener's processed receipt. With the optional Tempo overlay the API exports
once to Alloy. Independent exporters forward traces to local Jaeger and the
stack's authenticated OTLP gateway. Metrics retain their Prometheus path.
