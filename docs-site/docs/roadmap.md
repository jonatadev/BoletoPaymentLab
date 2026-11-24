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
| OAuth do MCP no ChatGPT | Overlay e descoberta implementados. Validação externa pendente. | Confirmar callback, recurso anunciado e audiência pelo fluxo real. Veja o [guia do MCP](./operacao/mcp-analytics.md). |
| Kafka em produção | O laboratório usa CA local e um nó KRaft. | Definir CA e cofre corporativos, criptografia em repouso e disponibilidade. Veja [Segurança do Kafka](./operacao/kafka-seguro.md). |

O escopo permanece educacional. A documentação descreve boletos fictícios
e PDFs sem validade para pagamento.
