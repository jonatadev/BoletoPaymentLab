# Boleto Payment Lab Requirements Catalog

Status: Draft for human review. This revision records the approved MVP
decisions. The REST contract and technical design remain proposals.

| ID | Type | Requirement or constraint | Verification |
| --- | --- | --- | --- |
| RF-BPL-001 | Functional | Persist first issuance and return HTTP 201. On a separate request, regenerate the educational PDF from persisted data with a prominent notice that it is invalid for payment. | UC-BPL-001#S01, S12 |
| RF-BPL-002 | Functional | A matching canonical payload and scoped key return HTTP 200 and the same completed boleto UUID, including after its original due date. | UC-BPL-001#S02, S04, S09 |
| RF-BPL-003 | Functional | A different payload with the same scoped key yields HTTP 409. | UC-BPL-001#S03, S05 |
| RF-BPL-004 | Functional | Require the `Idempotency-Key` header, synthetic `customerId` and BRL amount from 0.01 to 999999.99 normalized to two decimal places. Require a future due date on first issuance using an injectable clock in America/Sao_Paulo. | UC-BPL-001#S01, S07 |
| RF-BPL-005 | Functional | When a scoped key is processing, return a temporary conflict with `OPERATION_IN_PROGRESS` and `Retry-After`. | UC-BPL-001#S04, S05, S10, S13 |
| RF-BPL-006 | Functional | Allow local retrieval of a persisted boleto by UUID. | UC-BPL-001#S11 |
| RNF-BPL-001 | Non-functional | PostgreSQL guarantees uniqueness over customer, operation and key, including concurrent submissions. | UC-BPL-001#S04, S05, S08 |
| RNF-BPL-002 | Non-functional | PDF rendering and subsequent attempts preserve the original issuance identity and prevent duplicate issuance. PDF bytes are regenerated on demand and have no persistent storage. | UC-BPL-001#S06, S12 |
| RES-BPL-001 | Constraint | Use Java 25, Spring Boot 4.2.0, Gradle Kotlin DSL, PostgreSQL and Apache PDFBox. | Build and adapter review |
| RES-BPL-002 | Constraint | Run as a local lab using exclusively synthetic data. Exclude real bank integration and payment-capable or payment-like barcodes and digit lines. | UC-BPL-001#S01 and scope review |
| RES-BPL-003 | Constraint | Retain the modular monolith. Integrate Kafka through the transactional outbox in the lifecycle release. | Architecture review |
| RES-BPL-004 | Constraint | Obtain human authorization for the implementation scope. Record decisions and acceptance criteria before coding. | Reviewed work scope |
| RF-BPL-007 | Functional | Simulate payment and cancellation of eligible boletos. Resolve overdue records and preserve original due dates. | UC-BPL-002#S14, S15 |
| RF-BPL-008 | Functional | List boletos with bounded pagination and period, status and customer filters. | UC-BPL-002#S16 |
| RF-BPL-009 | Functional | Record and expose authorized audit history for issuance and state changes. | UC-BPL-002#S17 |
| RF-BPL-010 | Functional | Publish lifecycle events through a PostgreSQL outbox and demonstrate durable consumer deduplication. | UC-BPL-002#S18, S19 |
| RNF-BPL-003 | Non-functional | Require validated JWTs and operation-specific scopes. Protect metrics and deny administrative Actuator endpoints. | UC-BPL-002#S20 |
| RNF-BPL-004 | Non-functional | Provide health probes, metrics, JSON logs and correlated W3C traces, isolating exporter failures from API requests. | UC-BPL-002#S21 |

## Business rules

| ID | Rule | Requirement | Scenarios |
| --- | --- | --- | --- |
| RN-BPL-001 | Reuse the existing boleto UUID when scoped key and canonical payload match. Idempotency keys have no automatic expiry in the MVP. | RF-BPL-002, RNF-BPL-001 | S02, S04, S09 |
| RN-BPL-002 | Preserve the original operation and return HTTP 409 when a reused key has a different payload. | RF-BPL-003 | S03, S05 |
| RN-BPL-003 | Identify the PDF as educational and invalid for payment. | RF-BPL-001, RES-BPL-002 | S01, S12 |
| RN-BPL-004 | Use BRL amounts in the approved range with two decimal places. Validate future due date only for new issuance. | RF-BPL-004 | S01, S07, S09 |
| RN-BPL-005 | An active scoped operation can be retried after the indicated delay. A committed payload mismatch remains a permanent conflict for the same request. | RF-BPL-003, RF-BPL-005 | S05, S10, S13 |
| RN-BPL-006 | Only EMITIDO may transition, to PAGO, CANCELADO or VENCIDO. The due date remains unchanged. | RF-BPL-007 | UC-BPL-002#S14, S15 |
| RN-BPL-007 | Commit a state transition, audit record and outbox event together. | RF-BPL-009, RF-BPL-010 | UC-BPL-002#S17, S18 |

Scenario IDs refer to [UC-BPL-001](usecases/UC-BPL-001-emitir-boleto.md).
PENDENTE — approve the proposed HTTP contract, input format details and
concurrency mechanism in the use case.
