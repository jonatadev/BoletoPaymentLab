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

As suítes de PostgreSQL exigem `BPL_IT_DB_URL`, `BPL_IT_DB_USERNAME` e
`BPL_IT_DB_PASSWORD` em um banco com permissão de criação de esquemas.
Cada execução usa um esquema isolado `bpl_it_*`, mantido para inspeção.

Configure `BPL_IT_KAFKA_BOOTSTRAP` para testar a entrega no broker real.
A suíte usa tópico e grupo próprios. Sem essa variável, o teste de recuperação
usa o transporte controlado e exercita a deduplicação diretamente. Configure
`BPL_IT_JAEGER_QUERY` para testar a consulta de um trace no Jaeger, e
`BPL_IT_JAEGER_OTLP` se o coletor usar endereço diferente do padrão local.

## Cobertura de testes

O JaCoCo gera relatórios a partir da mesma tarefa `test`. Consulte [Qualidade e cobertura](./cobertura.md) para os comandos, os arquivos gerados e a integração com o Codecov.

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
