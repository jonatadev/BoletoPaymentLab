# Boleto Payment Lab Entity Model

Status: Preliminary draft for human review.

| Candidate | Meaning | Relationships and open questions |
| --- | --- | --- |
| `Boleto` | Educational issuance identified by UUID. Holds synthetic `customerId`, BRL amount from 0.01 to 999999.99 as `BigDecimal` at scale 2, and a due date. | Issue persists this data. Separate PDF requests regenerate from it without persisting PDF bytes. |
| `SolicitacaoIdempotente` | Association between customer, issue operation, key and the completed boleto UUID. | The persisted boleto supplies canonical comparison fields. There is no automatic expiry. |
| `EventoOutbox` | Durable Kafka delivery record containing a versioned, minimal lifecycle event, trace context, retry schedule and publication receipt. | Belongs to one boleto and one aggregate sequence. |
| `BoletoAudit` | Immutable issuance or transition entry with technical origin, time, correlation and old/new state. | Belongs to one boleto and shares an event ID with its outbox record. |
| `ProcessedBoletoEvent` | Consumer receipt and educational processing result. | Unique event ID makes repeat delivery harmless. |

## Invariants

- RN-BPL-001 and RN-BPL-002 govern reuse and conflict for the boleto UUID.
  PostgreSQL enforces uniqueness of customer, operation and key
  (RNF-BPL-001). The proposed transaction commits issuance and association
  together. An active transaction is distinct from the committed association.
- RN-BPL-004 applies the future-date check only to new issuance, using a
  clock in America/Sao_Paulo. Completed replays survive the original due date.
- RN-BPL-003 governs the educational PDF representation. The issuance concept
  carries no bank registration or payment validity. PDF requests and
  rendering failures preserve issuance identity and state (RNF-BPL-002).

The lifecycle adds immutable original due date, recorded issuance time and
monotonic aggregate version. V2 records migration time for preexisting
boletos because their original issuance time was unavailable.

V5 requires each outbox event to reference the audit record with the same
event ID, boleto ID and aggregate version. See the
[integrity review](outbox-integrity-review.md) and UC-BPL-002 S17–S19.

### Technical entity history

V6 adds `created_at`, `updated_at`, `created_by` and `updated_by` to
Boleto. BoletoRevision owns revision timestamp, actor and correlation.
BoletoRevisionState is identified by boleto ID and revision ID, with a
foreign key to BoletoRevision. See [technical audit scenarios](boleto-revisions.md).
