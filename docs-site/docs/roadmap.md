---
title: Roadmap
---

Os itens abaixo são propostas ou lacunas conhecidas. A API e os eventos
vigentes estão nas páginas de [API REST](./referencia/api.md) e
[Kafka e Outbox](./referencia/eventos.md).

| Item | Situação | Próxima decisão |
| --- | --- | --- |
| Apache Avro e Schema Registry | Proposta, sem implementação. | Escolher registro, compatibilidade e plano de transição a partir do JSON. Ver [ADR 005](./decisoes/avro.md). |
| Retenção de auditoria, Outbox e recibos | Retenção atual manual. | Definir prazos e critérios de arquivamento antes de automatizar. |
| Isolamento por cliente | Escopos atuais abrangem o laboratório. | Avaliar modelo de propriedade se o laboratório vier a ser compartilhado. |

O escopo permanece educacional. A documentação descreve boletos fictícios
e PDFs sem validade para pagamento.
