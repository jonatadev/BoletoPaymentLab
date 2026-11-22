---
title: Testes e validação
---

## Executar com Java 25

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

| Suíte | Evidência |
| --- | --- |
| `IssuanceInputTests` | Normalização decimal e limites de emissão. |
| `BoletoStatusTests` | Vencimento e estados terminais. |
| `BoletoPaymentLabApplicationTests` | Inicialização do contexto Spring. |
| `BoletoPostgresTests` | Rotas HTTP, JWT, idempotência, concorrência, auditoria, falha de PDF e disponibilidade durante erro OTLP. |
| `BoletoEventsPostgresTests` | Retentativa, ordem de publicação, concorrência no Outbox e deduplicação. |
| `LegacyDatabaseUpgradeTests` | Migração V1–V5 no PostgreSQL com um registro preexistente. |
| `OutboxAuditMigrationTests` | Migração V4–V5 com eventos legados, rejeição de relações divergentes e rollback do DDL nos cenários S17–S19. |

As suítes de PostgreSQL e Kafka usam Testcontainers e exigem apenas um daemon
Docker acessível. Cada execução usa um esquema isolado `bpl_it_*` e tópico/grupo
Kafka próprios. A consulta opcional de trace ainda requer `BPL_IT_JAEGER_QUERY`
e `BPL_IT_JAEGER_OTLP` quando um Jaeger local estiver disponível.

## Cobertura de testes

O JaCoCo consolida `test` e `integrationTest`. Consulte [Arquitetura de testes e qualidade](./arquitetura-de-testes.md) e [Qualidade e cobertura](./cobertura.md) para comandos, relatórios e integração com o Codecov.

## Validar mudanças no portal

```powershell
npm --prefix docs-site ci
npm --prefix docs-site run typecheck
npm --prefix docs-site run build
npm --prefix docs-site run serve
```

O build falha quando encontra links quebrados. Use a versão estática para
conferir a pesquisa local e o render dos diagramas. O CI de documentação
executa a instalação, a checagem TypeScript e o build, sem etapa de publicação.
