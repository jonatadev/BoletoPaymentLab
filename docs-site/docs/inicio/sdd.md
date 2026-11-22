---
title: Especificações e rastreabilidade
---

As especificações do repositório registram requisitos, regras, fluxos e
critérios de aceitação. O portal traduz esse contexto para desenvolvimento,
operação e uso da API.

| Fonte no repositório | Use para |
| --- | --- |
| `specs/requirements_catalog.md` | Identificar requisitos `RF-BPL`, atributos `RNF-BPL`, restrições e regras de negócio. |
| `specs/entity_model.md` | Consultar entidades conceituais e invariantes. |
| `specs/usecases/UC-BPL-001-emitir-boleto.md` | Revisar emissão, PDF e idempotência nos cenários S01–S13. |
| `specs/usecases/UC-BPL-002-boleto-lifecycle.md` | Revisar ciclo de vida, listagem, auditoria, segurança e eventos nos cenários S14–S21. |
| `specs/implementation_plan.md` | Relacionar cada grupo de cenários à suíte de testes. |
| `specs/001-sample-microservice/` | Consultar a referência histórica da fundação do projeto. |

Ao alterar um fluxo, vincule requisito, cenário, teste e documentação afetada.
Por exemplo, `RF-BPL-005 → UC-BPL-001#S10 → BoletoPostgresTests` liga a resposta
de conflito temporário ao teste que segura a transação concorrente.
Registre mudanças de arquitetura nos [ADRs](../decisoes/index.md) e itens
ainda sem implementação no [roadmap](../roadmap.md).
