# Outbox and audit integrity review

Status: Approved under RES-BPL-004 and implemented in
`src/main/resources/db/vendor/postgresql/V5__outbox_audit_identity.sql`.

## Existing model and invariant

On PostgreSQL 17.12, Flyway V1–V4 define five tables. Boleto uses a UUID, a
calendar due date and NUMERIC(8,2) with an inclusive BRL range of 0.01 to
999999.99. It stores the issuance instant with time zone and a Hibernate
version. The issuance association uniquely identifies customer, operation
and request key and references one boleto. Audit records and outbox events
are unique per boleto and aggregate version. Consumer receipts are unique
by event ID and independent of outbox retention.

The outbox currently references audit by event ID and boleto separately.
Those foreign keys allow an outbox row to cite an existing audit event
while identifying a different existing boleto or aggregate version.
BoletoJournal writes one matching tuple to both tables today. A read-only
check of the local application schema found one joined event and no tuple
mismatch. This sample cannot establish large-table performance.

This proposal protects BoletoAudit and EventoOutbox from inconsistent
references. Relevant sources are the [entity model](entity_model.md),
[requirements catalog](requirements_catalog.md) (RF-BPL-009, RF-BPL-010,
RN-BPL-007, RES-BPL-004), and
[UC-BPL-002](usecases/UC-BPL-002-boleto-lifecycle.md) S17–S19.

## Flyway V5 SQL

For the small local lab, run the following in one Flyway transaction during
a maintenance window. Preserve V1–V4 and the existing foreign keys in
this revision.

```sql
SET LOCAL lock_timeout = '3s';
SET LOCAL statement_timeout = '30s';

ALTER TABLE boleto_audit
    ADD CONSTRAINT uq_boleto_audit_event_identity
    UNIQUE (event_id, boleto_id, aggregate_version);

ALTER TABLE boleto_outbox
    ADD CONSTRAINT fk_boleto_outbox_audit_identity
    FOREIGN KEY (event_id, aggregate_id, aggregate_version)
    REFERENCES boleto_audit (event_id, boleto_id, aggregate_version)
    ON UPDATE NO ACTION
    ON DELETE NO ACTION;
```

The audit index is the unique reference target for the composite foreign
key. Existing keys continue to support audit history, earlier events of
the same aggregate and consumer deduplication. The poll index already
supports pending events ordered by retry time and event ID. Issuance and
expiry queries continue to use the existing scope, time and status indexes.
The outbox primary key narrows child lookup by event ID, so this change
needs no additional child or polling index. Determine whether a partial
polling index or keyset pagination helps using representative distributions,
query plans and write load before proposing either change.

## Transactions and deployment

- Keep READ COMMITTED, the transaction-scoped nonblocking issuance
  advisory lock and the scope uniqueness constraint. Keep the row lock
  and Hibernate version for lifecycle commands, the relay's SKIP LOCKED
  claim and at-least-once delivery with durable consumer deduplication.
- The unique constraint builds an index under ACCESS EXCLUSIVE on audit.
  Adding the foreign key takes SHARE ROW EXCLUSIVE on both tables and
  validates existing rows. Locks persist until transaction completion.
  This script is intended for the lab maintenance window. For a large or
  continuously written schema, design a separate deployment using concurrent
  index creation outside a transaction, a foreign key added NOT VALID and
  subsequent validation. Plan recovery of invalid concurrent indexes.
- V5 is additive. Older application versions that write matching tuples
  remain compatible. Leave V5 in place on application rollback. If V5 fails
  to acquire locks, times out or detects divergent data, its DDL rolls back.
  Investigate the cause before retrying. Any later removal would use a new
  migration; correction of legacy audit is a separate decision.
- Keep the JPA mappings, scalar list queries and JDBC journal. Measure
  query counts before adjusting fetch behavior. Preserve existing decimal
  validation and the original due-date invariant in application code.

## Acceptance

- Apply V5 through Flyway on new and populated PostgreSQL schemas. Verify a
  matching tuple succeeds and that both a different existing boleto and a
  different version fail with SQLSTATE 23503. Verify V5 rejects deliberately
  divergent legacy data without partially applying its DDL.
- Exercise existing concurrency, rollback, Hibernate schema validation,
  Kafka delivery and deduplication tests. Tie results to S17–S19 and issuance
  regressions. Update the entity model, ADR and Docusaurus after validation.

PENDENTE — retention, reconciliation of any divergent history, and
performance changes require separate evidence and decisions.
