---
title: Qualidade e cobertura
---

## O que a cobertura mede

Cobertura indica quais instrucoes, linhas e desvios condicionais foram exercitados pelos testes. Ela e um sinal para encontrar caminhos sem verificacao, nao uma prova de correcao funcional, seguranca ou qualidade do desenho.

O Boleto Payment Lab relaciona os testes de comportamento aos cenarios `UC-BPL-001#S01-S13` e `UC-BPL-002#S14-S21`. Idempotencia, transacoes, autorizacao, auditoria e Outbox devem ser avaliados pela assertividade desses cenarios, e nao apenas por um percentual.

## JaCoCo e Codecov

- **JaCoCo** executa localmente com o Gradle, instrumenta as classes Java e gera os relatorios de linhas e branches.
- **Codecov** recebe o XML produzido pelo JaCoCo no CI e apresenta o impacto da cobertura no projeto e nas linhas alteradas em um pull request.

## Executar localmente

No Windows, execute:

```powershell
.\gradlew.bat test jacocoTestReport
```

Os relatorios sao gerados em:

- HTML: `build/reports/jacoco/test/html/index.html`;
- XML: `build/reports/jacoco/test/jacocoTestReport.xml`.

O HTML serve para inspecao local por pacote, classe, linha e branch. O XML e o artefato consumido pelo Codecov. A cobertura dos testes PostgreSQL, Kafka e Jaeger e incluida quando as variaveis `BPL_IT_*` documentadas em [Testes e validacao](./testes.md) estao configuradas; sem elas, as respectivas suites sao ignoradas deliberadamente.

## CI e pull requests

O workflow **Test coverage** executa em pull requests e em pushes para `main`. Ele primeiro roda os testes e gera o XML; portanto, uma falha de teste ou de relatorio interrompe o job antes do upload.

Para publicar no Codecov, conecte o repositorio ao projeto Codecov e cadastre `CODECOV_TOKEN` em **GitHub > Settings > Secrets and variables > Actions**. O segredo nao e impresso nos logs e nao deve ser salvo em arquivos versionados. Sem o segredo, a etapa de upload e ignorada com um aviso, mas a validacao local do relatorio continua.

Pull requests de forks executam testes e relatorios, mas nao fazem upload com o token do repositorio. Isso evita expor segredos a codigo nao confiavel e dispensa o uso de `pull_request_target`.

Os status iniciais de projeto e de patch sao informativos. O status de patch considera somente as linhas modificadas; o de projeto considera a base inteira. Use as diferencas para revisar cenarios novos e regressoes antes de adotar metas bloqueantes.
