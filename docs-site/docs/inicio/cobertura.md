---
title: Qualidade e cobertura
---

## O que a cobertura mede

Cobertura indica quais instrucoes, linhas e desvios condicionais foram exercitados pelos testes. Ela e um sinal para encontrar caminhos sem verificacao, nao uma prova de correcao funcional, seguranca ou qualidade do desenho.

O Boleto Payment Lab relaciona os testes de comportamento aos cenarios `UC-BPL-001#S01-S13` e `UC-BPL-002#S14-S21`. Idempotencia, transacoes, autorizacao, auditoria e Outbox devem ser avaliados pela assertividade desses cenarios, e nao apenas por um percentual.

## JaCoCo e Codecov

- **JaCoCo** executa localmente com o Gradle, instrumenta as classes Java e gera os relatorios de linhas e branches.
- **Codecov** recebe o XML produzido pelo JaCoCo no CI e apresenta o impacto da cobertura no projeto e nas linhas alteradas em um pull request.

## Formatação Java

O Spotless verifica o Java da API e do MCP, incluindo testes unitários e de
integração. O Google Java Format usa o estilo AOSP, com quatro espaços de
indentação por nível e largura de 120 colunas. O Spotless encurta referências
quando pode importar o tipo com segurança, remove imports não utilizados, ordena os imports e
converte curingas em imports explícitos. Se um curinga não puder ser resolvido
pelo classpath, substitua-o por imports explícitos e rode a verificação novamente.
Fontes geradas pelo build ficam fora do escopo.

No Windows, execute na raiz do projeto:

```powershell
.\gradlew.bat spotlessCheck
.\gradlew.bat spotlessApply
```

Em Linux ou macOS, use `./gradlew` com as mesmas tarefas. Revise o diff e
execute `spotlessCheck` novamente após a correção. O GitHub Actions executa
`spotlessCheck` em uma etapa própria antes dos testes, sem editar os fontes.

No IntelliJ IDEA, abra o projeto Gradle com o Wrapper e selecione o JDK 25
em **Settings > Build, Execution, Deployment > Build Tools > Gradle > Gradle JVM**.
Na janela **Gradle**, use **Execute Gradle Task** para executar `spotlessCheck`
ou `spotlessApply` a partir da raiz. Para limitar ao MCP, use
`:boleto-analytics-mcp:spotlessCheck` ou `:boleto-analytics-mcp:spotlessApply`.
Em **Settings > Tools > Actions on Save**, desative **Reformat code** e
**Optimize imports** para Java se essas ações estiverem aplicando regras
diferentes das do Spotless.

## Executar localmente

No Windows, execute:

```powershell
.\gradlew.bat test jacocoTestReport
```

Os relatorios sao gerados em:

- HTML: `build/reports/jacoco/test/html/index.html`;
- XML: `build/reports/jacoco/test/jacocoTestReport.xml`.

O HTML permite inspecionar a cobertura por pacote, classe, linha e branch.
O Codecov consome o XML. Os testes de PostgreSQL e Kafka usam
Testcontainers com Docker. A consulta ao Jaeger é opcional e usa as
variáveis descritas em [Testes e validação](./testes.md).

## CI e pull requests

O workflow **Test coverage** executa em pull requests e em pushes para `main`. Uma falha de formatação, teste ou geração do XML interrompe o job antes do upload.

Para publicar no Codecov, conecte o repositorio ao projeto Codecov e cadastre `CODECOV_TOKEN` em **GitHub > Settings > Secrets and variables > Actions**. O segredo nao e impresso nos logs e nao deve ser salvo em arquivos versionados. Sem o segredo, a etapa de upload e ignorada com um aviso, mas a validacao local do relatorio continua.

Pull requests de forks executam testes e relatorios, mas nao fazem upload com o token do repositorio. Isso evita expor segredos a codigo nao confiavel e dispensa o uso de `pull_request_target`.

Os status iniciais de projeto e de patch sao informativos. O status de patch considera somente as linhas modificadas; o de projeto considera a base inteira. Use as diferencas para revisar cenarios novos e regressoes antes de adotar metas bloqueantes.
