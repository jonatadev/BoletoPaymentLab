# Technical boleto revisions

This addition extends [RF-BPL-009 and RN-BPL-007](requirements_catalog.md)
for the Boleto entity in [the data model](entity_model.md). It follows
[UC-BPL-002](usecases/UC-BPL-002-boleto-lifecycle.md).

| Scenario | Expected outcome |
| --- | --- |
| S22 | Issuance records author, timestamps and one technical revision. Replay creates no new revision. |
| S23 | A committed transition retains creation metadata, records modification metadata and correlates with the business audit and outbox. Historical reads return the recorded state and can compare two revisions. |
| S24 | Rollback and stale optimistic updates leave no technical revisions for the failed writes. |
| S25 | Technical revision reads require the existing audit scope. Missing revisions return 404. Reads never materialize expiry. |

The history starts at deployment. Legacy issuance time is the creation
timestamp proxy. Unknown legacy authors and modification timestamps remain
null. Customer identifiers are excluded from technical history.
