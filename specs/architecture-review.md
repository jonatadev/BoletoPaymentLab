# Boleto Payment Lab architecture review

Status: Scope approved for implementation under RES-BPL-004.

Traceability sources: [requirements catalog](requirements_catalog.md),
[entity model](entity_model.md), [UC-BPL-001](usecases/UC-BPL-001-emitir-boleto.md)
and [UC-BPL-002](usecases/UC-BPL-002-boleto-lifecycle.md).

## Findings and proposed scope

| Finding | Proposed decision | Trace |
| --- | --- | --- |
| BoletoService owns issuance and idempotency, but its nested Snapshot is also the common read model for lifecycle, listing, HTTP and PDF rendering. The PDF lookup shares the issuance service despite needing only a read-only query. | Promote Snapshot to a top-level application record. Move the existing read-only lookup to BoletoQueries. Keep detail and history on BoletoLifecycle because they can materialize expiry. | Boleto, RF-BPL-002, RF-BPL-006, RF-BPL-007, RF-BPL-008, UC-BPL-001 S02, S09, S11, UC-BPL-002 S15–S17 |
| The PDF endpoint coordinates data access, rendering observation and checked failure translation. | Move PDF orchestration to an application service using the existing PdfRenderer port. Keep the response status, MIME type and attachment filename in the controller. Run the renderer after the lookup transaction closes. | Boleto, RF-BPL-001, RNF-BPL-002, RN-BPL-003, UC-BPL-001 S01, S06, S12 |
| BoletoJournal maps status to event names and BoletoEventConsumer independently lists the same names. A future edit can make the producer emit events the consumer rejects. | Use one package-local event type with explicit wire values and a status mapping. Keep BoletoEvent.eventType as a string and preserve consumer rejection of null or unknown values. | BoletoAudit, EventoOutbox, ProcessedBoletoEvent, RF-BPL-009, RF-BPL-010, RN-BPL-007, UC-BPL-002 S17–S19 |
| BoletoLifecycle uses null to carry a rejected command out of the transaction. Throwing inside the callback would roll back expiry, audit and outbox. | Preserve commit-before-conflict and make the transaction outcome explicit with Optional. Preserve the existing observation outcome. | Boleto, BoletoAudit, EventoOutbox, RF-BPL-007, RN-BPL-006, RN-BPL-007, UC-BPL-002 S14–S15, S17 |

## SOLID and architectural boundaries

- SRP favors separating PDF orchestration and read-model ownership from issuance. Keep issuance, scoped association and journal writes in one transaction.
- OCP favors the existing PDF and Kafka adapter seams. The status set and v1 event vocabulary have intentionally closed contracts.
- LSP depends on the existing port contracts. PDF adapters return an in-memory document or signal failure. Outbox transports return only after acknowledgement or signal publication failure.
- ISP is already represented by the single-method PDF and transport ports. Introducing repository interfaces that mirror Spring Data adds little while the use cases remain bound to PostgreSQL locking and JPA transactions.
- DIP favors those two external-adapter seams. Application queries use JPA specifications and map results before crossing the API boundary. The JPA model contains an issuance-to-boleto association accessed by scoped lookup. The list maps scalar boleto fields and shows no structural N+1 path in source. Query counts remain unmeasured.
- Keep package-by-feature, the modular monolith and the current transactional model. The outbox relay holds one row lock while waiting for bounded Kafka acknowledgement. A claim-and-release design would require ownership recovery and a separate ordering decision. The relay's use of one deferred publication to end the current poll remains a throughput trade-off.
- Keep exact JSON parsing, decimal canonicalization, JWT scopes, deny-by-default rules, and public ProblemDetail responses. Authorization is lab-wide by design. Keep trace propagation and observation names. The event listener uses a permanent event ID receipt and parameterized SQL. Operational retention and handling of messages the consumer rejects remain separate concerns.
- The use case draft describes guard-before-lookup and write-free lookup generally. The implementation plan, lifecycle use case and ADRs describe committed replay before the guard and expiry materialization on detail/history access. This proposal preserves those implemented paths and the PDF read-only path.

## Pattern evaluation

| Pattern | Assessment |
| --- | --- |
| Builder | Records and small constructors already express the required data. A broad fixture builder would obscure scenario inputs. |
| Strategy | Existing renderer and transport ports already support substitution for tests. No variable business policy warrants an additional strategy. |
| Chain of Responsibility | Input rules have a fixed order and no independently composable stages. |
| Command | Existing application methods express the use cases. Command envelopes add little without dispatch, scheduling or storage needs. |
| Factory Method / Factory | A status mapping and a named Snapshot mapping method cover current creation needs. A factory hierarchy adds no benefit. |
| Adapter | Retain PdfBoxRenderer and KafkaOutboxTransport. Route PDF orchestration through the existing renderer contract. |
| State | BoletoStatus and the entity transition guard cover the small closed state space. State objects would duplicate these rules. |
| Decorator | BoletoTelemetry and Spring transaction boundaries already wrap operations that require instrumentation and transactions. |
| Template Method | No family of operations requires a shared algorithm with overridable stages. |
| Singleton | Use Spring's default bean lifecycle with constructor injection and immutable dependencies. |

## Acceptance and verification

- Preserve endpoint paths, statuses, headers, serialization, security scopes, Kafka JSON, tracing, transaction timeouts, locks and all Flyway migrations.
- Add focused tests for PDF failure translation and bypassing the renderer on missing data, and for the four lifecycle event names. Use the existing PostgreSQL tests for concurrent issuance, rollback, state transitions, audit and delivery deduplication.
- Run the Gradle Wrapper build, including configured PostgreSQL coverage for UC-BPL-001 S01–S13 and UC-BPL-002 S14–S21. Report broker and Jaeger scenarios separately when those endpoints are unavailable.
- After implementation, record the decision in a portal ADR and document the actual Adapter and Transactional Outbox implementations with class references. Run portal typecheck and build.

PENDENTE — resolve the existing UC-BPL-001 open business decisions in their
own review.
