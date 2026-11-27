# UC-BPL-001 implementation plan

Status: Draft. Implementation requires human approval of the specification.

1. Review the [use case](usecases/UC-BPL-001-emitir-boleto.md), its proposed
   HTTP contract, canonicalization and locking strategy. Resolve PENDENTE
   items with the [entity model](entity_model.md) and record the revision.
2. After approval, design the issuance and document boundaries in the modular
   monolith. Put issuance and its key association in one transaction.
   Implement document retrieval and on-demand rendering separately.
3. In the implementation stage, design the PostgreSQL unique constraint and
   nonblocking guard. Verify commit, rollback and replay visibility on actual
   PostgreSQL. Review and request approval for any new dependency.
4. Link tests and implementation changes to the following scenario IDs.

| Scenario | Planned verification |
| --- | --- |
| UC-BPL-001#S01 | HTTP and PostgreSQL integration test for 201 and Location. Assert no renderer invocation on POST. Verify PDF MIME type, content from persisted data and visible educational notice on GET. |
| UC-BPL-001#S02 | HTTP replay test comparing 10 and 10.00, status 200, UUID and single persisted issuance. |
| UC-BPL-001#S03 | HTTP test for permanent 409, stable code and original record unchanged. |
| UC-BPL-001#S04 | Concurrent PostgreSQL test with synchronized workers, one issuance and a later matching 200. |
| UC-BPL-001#S05 | Concurrent PostgreSQL test with different payloads. Assert temporary conflict during the transaction, permanent 409 after commit and one issuance. |
| UC-BPL-001#S06 | Inject a controlled renderer failure on GET. Verify error and repeat GET. Assert original UUID, unchanged data and one issuance. |
| UC-BPL-001#S07 | Parameterized request tests for invalid inputs and amount boundaries. Use a fixed clock around midnight in America/Sao_Paulo and assert rejected inputs persist nothing. |
| UC-BPL-001#S08 | PostgreSQL test for two customers with a shared key and distinct persisted UUIDs. |
| UC-BPL-001#S09 | Issue using a controllable clock, advance beyond due date, replay and assert 200 with the original UUID. |
| UC-BPL-001#S10 | Gate the original transaction with test synchronization. Assert 409, code and Retry-After before releasing it. |
| UC-BPL-001#S11 | HTTP lookup tests for persisted JSON and the proposed 404 ProblemDetail. |
| UC-BPL-001#S12 | Assert two calls to the renderer for repeated GETs, valid PDF responses and proposed 404 for unknown UUID. Verify no durable PDF storage. |
| UC-BPL-001#S13 | Gate the original transaction, observe temporary conflict, force rollback and assert the next valid attempt commits exactly one result. |

The [lifecycle and platform release](usecases/UC-BPL-002-boleto-lifecycle.md)
adds observability, authorization, lifecycle, queries, audit and delivery.
Validate in that order, then exercise the incremental migrations and full
suite. Preserve the S01–S13 regression cases.

| Lifecycle verification | Automated coverage |
| --- | --- |
| S14–S17 | `BoletoStatusTests` and `BoletoPostgresTests` cover terminal states, effective expiry, filters and atomic audit. |
| S18–S19 | `BoletoEventsPostgresTests` injects a failed publication, proves durable recovery and deduplicates repeat delivery. The broker run also tests competing PostgreSQL locks. |
| S20 | `BoletoPostgresTests` exercises signed JWTs against a local test JWKS endpoint, invalid tokens and denied scopes. |
| S21 | `BoletoPostgresTests` confirms log correlation and success during exporter errors. The observability profile also verifies authorized Prometheus scrapes, committed counters, rollback, replay, histogram names and backlog. `OpenApiContractTests` checks default exposure and `BoletoEventsPostgresTests` checks producer, consumer and relay metrics. With Jaeger configured, the suite finds the trace through the Jaeger query API. |
| Incremental migration | `LegacyDatabaseUpgradeTests` upgrades a PostgreSQL schema containing a V1 row and validates V1–V5. |
| S17–S19 database invariant | `OutboxAuditMigrationTests` upgrades populated V4, verifies the V5 composite foreign key and confirms transactional rollback when legacy rows diverge. |

## Initial implementation choices

- `ISSUE_BOLETO` is the fixed operation identifier. Keys are case-sensitive
  printable ASCII, excluding comma at the HTTP boundary, with a 160-character
  limit. Synthetic customer IDs use letters, digits, dots, underscores or
  hyphens, with a 120-character limit. Payload fields are checked explicitly,
  duplicate JSON fields are rejected, and exact decimal exponents or trailing
  zeros are accepted when conversion to scale 2 requires no rounding.
- The temporary conflict uses a one-second retry delay. The lock ID is the
  first 64 bits of SHA-256 over the delimited scope. A hash collision can
  temporarily serialize unrelated scopes, while the database unique constraint
  and full-scope lookup prevent a collision from sharing a boleto or changing
  idempotency identity. Committed records can be replayed without acquiring the
  guard. An absent record is looked up again after acquiring the guard.
- The first release bound HTTP to loopback. UC-BPL-002 adds authentication.
  PDFBox renders in memory, and the PDF request has no write transaction.
- `BoletoPostgresTests` exercises S01–S13 over a real local HTTP server
  and PostgreSQL. `IssuanceInputTests` covers precision and normalization
  for S02 and S07. The integration suite runs only when configured with a
  PostgreSQL URL.

## Optional metrics collector (RNF-BPL-003/004, UC-BPL-002 S20/S21)

The optional Alloy Compose overlay requests short-lived tokens as the metrics
client and scrapes only the protected Prometheus endpoint. It forwards samples
over HTTPS using an independent write-only Cloud credential. Check the merged
Compose model, Alloy configuration and the fresh `up` series in the target
stack. Jaeger remains the local trace destination.

For S21, `IssuanceTraceTests` sends a sampled HTTP request to the real test
application and correlates its 201 response, published outbox row, Kafka listener
and persisted receipt with the parent references in Jaeger. Configure
`BPL_IT_ALLOY_OTLP` for this test. Verify the cloud copy independently in Tempo
by the same trace ID when stack credentials are available.
