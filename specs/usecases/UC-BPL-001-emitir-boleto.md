# UC-BPL-001 — Issue an educational boleto

Status: Draft for human review. The listed MVP decisions are recorded here.
HTTP paths, JSON shape and locking mechanism are proposed.

## Actor and objective

A local laboratory user issues for a fictional customer identified by
`customerId`, consults the record and requests its educational PDF separately.
The lifecycle release retains loopback and applies the JWT scopes and additive
`status` and `issuedAt` response fields in
[UC-BPL-002](UC-BPL-002-boleto-lifecycle.md).

## Proposed HTTP contract

| Request | Success | Payload or result |
| --- | --- | --- |
| POST /api/v1/boletos | 201 on first commit, 200 on completed replay | Request body and required `Idempotency-Key` header. Return the persisted record as JSON. Include `Location: /api/v1/boletos/{id}` on 201. |
| GET /api/v1/boletos/{id} | 200 | Return the same persisted record shape as JSON. |
| GET /api/v1/boletos/{id}/pdf | 200 | Return `application/pdf` rendered from the persisted record. |

Example request body.

```json
{"customerId":"customer-demo-001","amount":10.00,"dueDate":"2099-01-15"}
```

Example issue or lookup response.

```json
{"id":"18e79e20-f5c8-4a18-8e1e-6387061927bf","customerId":"customer-demo-001","amount":10.00,"currency":"BRL","dueDate":"2099-01-15"}
```

### Error contract

Use Spring `ProblemDetail` with `application/problem+json`. The proposed
extensions include a stable `code`. The body may also include `instance`.
Sanitize error details and omit credentials, payloads and stack traces.

| Condition | HTTP | Code | Client action |
| --- | --- | --- | --- |
| Malformed or invalid request, missing key, or malformed UUID | 400 | `INVALID_REQUEST` | Correct the request. |
| UUID not found for lookup or PDF | 404 | `BOLETO_NOT_FOUND` | Verify the UUID. |
| Committed key with different canonical payload | 409 | `IDEMPOTENCY_CONFLICT` | Use the original payload or a new key for a new issuance. |
| Scoped operation still in progress | 409 | `OPERATION_IN_PROGRESS` | Retry the same request after the `Retry-After` delay. |
| PDF rendering failure | 500 | `PDF_RENDER_FAILED` | Retry the PDF request when appropriate. |
| Database temporarily unavailable | 503 | `DATABASE_UNAVAILABLE` | Retry the same operation when available. |

Example temporary conflict body with a proposed delay header of
`Retry-After: 1`. PENDENTE — confirm the delay and proposed error contract.

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "Issuance is in progress. Retry after the indicated delay.",
  "code": "OPERATION_IN_PROGRESS"
}
```

## Preconditions and canonicalization

- The API is accessed locally. Inputs contain only synthetic data. Issuance
  requires one nonblank `Idempotency-Key` and the three request fields.
- The proposed canonical tuple is `customerId`, normalized `amount` and
  `dueDate` in that order. Use fixed BRL. Scope is the exact customer ID,
  stable issuance operation identifier and exact key. The key belongs to the
  scope. It does not participate in payload comparison.
- Parse amount as an exact decimal from a JSON number, scale to two places
  without rounding, then enforce the inclusive range 0.01 to 999999.99.
  Compare its plain two-decimal representation, so 10 and 10.00 match.
- Parse `dueDate` as a strict ISO local date such as `2099-01-15`. Compare its ISO
  representation without time or zone conversion. JSON field order and
  formatting whitespace have no effect. Treat the key and customer identifier
  as case-sensitive opaque strings in the proposed contract.
- PENDENTE — operation identifier literal, header and customer ID limits,
  unknown fields and duplicate JSON field handling, and whether to reject
  exponents or excess trailing zeros in JSON amounts.

## Main flow and alternatives

1. Validate request structure and canonicalize without applying the relative
   due date check. Enter the scoped transaction guard.
2. If a committed scoped record exists, compare canonical payloads. Return
   200 with its UUID for a match, even after its due date. Return 409
   `IDEMPOTENCY_CONFLICT` for a mismatch.
3. For a new key, verify that the due date is strictly after the current
   date from the injected clock in America/Sao_Paulo. Persist the issuance
   and scoped association in one PostgreSQL transaction.
4. Return 201 after commit. Issuance performs no PDF rendering.
5. On a separate GET, load by UUID. For the PDF route, use PDFBox and persisted
   data to regenerate the document. Include a prominent educational notice
   indicating it is invalid for payment. Exclude payment-like barcodes and
   digit lines. A render failure leaves the record and key association intact.

### Proposed concurrency strategy

Use a PostgreSQL unique constraint over customer, operation and key as the
database guarantee. At READ COMMITTED, before searching or inserting, attempt
a transaction-scoped nonblocking guard, proposed as
`pg_try_advisory_xact_lock` on a stable encoding of the scope. Keep the guard
through commit or rollback. Failure to acquire it produces the temporary 409
and `Retry-After`. An uncommitted payload cannot be reliably compared, so
a contender with a different payload can receive this temporary response first.
After commit, the same contender receives the permanent 409. Rollback releases
the guard and permits a new attempt.

A SELECT before INSERT is insufficient for uniqueness. If the constraint
rejects an insert, roll back the failed transaction before rereading the
committed result in a usable transaction. PENDENTE — protection against
advisory key collisions, key derivation and final guard implementation.

## Postconditions

- A confirmed issuance has one persisted UUID and committed key association.
  There is no automatic key expiry.
- Conflict responses do not create another boleto or mutate the original.
- Lookup and PDF requests do not modify issuance state or store PDF bytes.

## Acceptance scenarios

| ID | Given and when | Then | Trace |
| --- | --- | --- | --- |
| S01 | Submit valid synthetic data with a new key, then request its PDF. | POST returns 201 without invoking PDF rendering. The separate PDF request uses persisted data, shows the notice and contains no payment-like identifiers. | RF-BPL-001, RF-BPL-004, RES-BPL-002, RN-BPL-003, RN-BPL-004 |
| S02 | Replay a completed key with equivalent payload, including 10 vs 10.00. | Return 200 with the original UUID. | RF-BPL-002, RN-BPL-001 |
| S03 | Submit a different payload with a completed scoped key. | Return permanent 409 and preserve the original issuance. | RF-BPL-003, RN-BPL-002 |
| S04 | Race equivalent requests on PostgreSQL. | Persist one issuance. An active contender receives temporary 409. A completed replay returns 200 with the same UUID. | RF-BPL-002, RF-BPL-005, RNF-BPL-001, RN-BPL-001 |
| S05 | Race different payloads in one scope on PostgreSQL. | Persist one issuance. The active contender receives temporary 409. After the winner commits, replaying the different payload returns permanent 409. | RF-BPL-003, RF-BPL-005, RNF-BPL-001, RN-BPL-002, RN-BPL-005 |
| S06 | A PDF GET fails after issuance, then the PDF GET is repeated. | The failure returns the proposed render error. Both attempts preserve the same UUID and one issuance. | RNF-BPL-002 |
| S07 | Submit a missing key or customer, amount outside range or requiring rounding, malformed date, or date of today or earlier on first issuance. | Reject with proposed 400 and persist nothing. Values at both permitted amount bounds are accepted with valid remaining fields. | RF-BPL-004, RN-BPL-004 |
| S08 | Two customers use the same key for issuance. | Two distinct scoped issuances receive distinct UUIDs. | RNF-BPL-001 |
| S09 | Advance the injected clock past the due date after a successful issuance and replay. | Return 200 and the same UUID. | RF-BPL-002, RN-BPL-001, RN-BPL-004 |
| S10 | Pause the original transaction with its guard held and submit a replay. | Return temporary 409, `OPERATION_IN_PROGRESS` and `Retry-After` before releasing the original transaction. | RF-BPL-005, RNF-BPL-001, RN-BPL-005 |
| S11 | Look up a known or unknown UUID. | Return the persisted record or the proposed 404. | RF-BPL-006 |
| S12 | Download a known PDF twice and request one for an unknown UUID. | Return PDF on each known request with no persisted bytes. Return proposed 404 for the unknown UUID. | RF-BPL-001, RNF-BPL-002, RN-BPL-003 |
| S13 | A contender receives a temporary conflict and the original transaction then rolls back. | A subsequent valid attempt can create the issuance with 201. No failed attempt leaves a committed association. | RF-BPL-005, RNF-BPL-001, RN-BPL-005 |

See the [requirements catalog](../requirements_catalog.md) for referenced IDs.
