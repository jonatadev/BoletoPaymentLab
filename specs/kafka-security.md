# Kafka security hardening

Status: Approved for implementation by the repository owner. Validate on a
separate Compose project before scheduling a migration of the existing lab.

- [Catalog](requirements_catalog.md): RES-BPL-003, RES-BPL-004, RF-BPL-010,
  RN-BPL-007, RNF-BPL-004.
- [Model](entity_model.md): BoletoAudit, EventoOutbox and ProcessedBoletoEvent.
- [Issuance](usecases/UC-BPL-001-emitir-boleto.md): S01.
- [Lifecycle](usecases/UC-BPL-002-boleto-lifecycle.md): S17–S19 and S21.

Keep Kafka 4.2.2, the static KRaft quorum, the named volume, the existing
topic and group, and the at-least-once outbox protocol. API, relay and
consumer share a process and Kafka principal. The analytics MCP has no Kafka
client. Kafka UI is opt-in, authenticated and limited to metadata.

Use SASL_SSL with SCRAM-SHA-512 for INTERNAL and HOST. Require mutual TLS
on REPLICATION, CONTROLLER and ADMIN. Publish only HOST on loopback and
isolate the broker from the default Compose network. Require hostname
verification, use StandardAuthorizer with default deny and disable
automatic topic creation. Bootstrap through the privileged ADMIN certificate
with no dependency on SCRAM credentials. Keep application principals out
of super.users and the admin private key outside application mounts.

| Principal | Literal resource | Operations |
| --- | --- | --- |
| svc-boleto-api | Topic boleto.lifecycle.v1 | READ, WRITE |
| svc-boleto-api | Group boleto-lab-educational | READ |
| svc-kafka-ui | Topic boleto.lifecycle.v1 | DESCRIBE |
| svc-kafka-ui | Group boleto-lab-educational | DESCRIBE |

Node and bootstrap administrator are certificate-authenticated super users.
The application has no transactional ID. Kafka 4.2.2 authorizes nontransactional
InitProducerId based on topic WRITE without cluster IdempotentWrite.
Preserve serializer, consumer group, retries, acknowledgement and offsets.
Provision absent entries, verify the effective policy and refuse drift.
Keep rotation and revocation explicit administrative operations.

| Scenario | Acceptance |
| --- | --- |
| KAF-01 | Idempotent application producer publishes to the lifecycle topic. |
| KAF-02 | An authenticated principal without WRITE receives a topic authorization error. |
| KAF-03 | Application consumes and commits offsets in its group. |
| KAF-04 | An authenticated principal without READ receives a topic authorization error. |
| KAF-05 | Application receives a group authorization error for another group. |
| KAF-06 | Application cannot create or delete topics. |
| KAF-07 | Wrong SCRAM credentials fail authentication. |
| KAF-08 | An unauthenticated client is rejected. |
| KAF-09 | Untrusted CA and wrong endpoint identity fail TLS. |
| KAF-10 | Administrator can create, describe and revoke ACLs. |
| KAF-11 | Restart and repeated provisioning preserve cluster identity, topic ID, messages, offsets and credentials. Cover a fresh volume and a populated legacy volume. |
| KAF-12 | Issuance, outbox retry, publication, receipt and deduplication work through secure Kafka. |

PENDENTE for production: corporate CA and secret manager, retention and
backups, encrypted storage and highly available broker/controller topology.
