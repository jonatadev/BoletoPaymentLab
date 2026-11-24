---
title: Registro de decisões
slug: /decisoes
---

| ADR | Situação | Decisão |
| --- | --- | --- |
| [001](./monolito.md) | Confirmada | Monólito modular e PostgreSQL como fonte de verdade. |
| [002](./idempotencia.md) | Confirmada | Restrição única e advisory lock para emissão idempotente. |
| [003](./outbox.md) | Confirmada | Outbox e deduplicação persistente. |
| [004](./jwt.md) | Confirmada | OAuth2 Resource Server com JWT e escopos por operação. |
| [005](./avro.md) | Proposta | Avaliar Avro e Schema Registry. |
| [006](./fronteiras-de-aplicacao.md) | Confirmada | Fronteiras de emissão, leitura, PDF e eventos. |
| [007](./envers.md) | Confirmada | Auditoria JPA e histórico seletivo de boletos com Envers. |
| [008](./kafka.md) | Confirmada | SCRAM sobre TLS, mTLS e ACLs no Kafka. |

Uma decisão confirmada descreve o que está em uso. Propostas registram
alternativas em avaliação. Se uma implementação substituir uma decisão,
documente a nova decisão e aponte para ela a partir da anterior.
