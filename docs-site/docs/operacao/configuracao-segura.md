---
title: Configuração segura
---

## Ambiente local

Crie `.env` a partir de `.env.example` e mantenha credenciais somente no
arquivo ignorado. PostgreSQL exige `POSTGRES_DB`, `POSTGRES_USER` e
`POSTGRES_PASSWORD`. Antes do primeiro boot do Keycloak, defina
`KC_BOOTSTRAP_ADMIN_USERNAME` e `KC_BOOTSTRAP_ADMIN_PASSWORD`; o Compose falha
de forma explícita quando uma dessas variáveis estiver ausente.

Não versione `.env`, `application-local.yaml`, tokens, segredos de clientes,
chaves privadas ou arquivos locais da IDE. O arquivo `application-local.yaml`
recebe credenciais do banco por `BPL_DB_USERNAME` e `BPL_DB_PASSWORD`.

## CI e rotação

O workflow de cobertura envia dados ao Codecov somente quando `CODECOV_TOKEN`
está configurado como secret do repositório no GitHub. Pull requests de forks
não recebem esse segredo. Execute `./gradlew test jacocoTestReport` para gerar
os relatórios antes de enviar uma mudança.

Se uma credencial for exposta, revogue-a, gere uma nova e atualize apenas o
gerenciador de segredos ou o mecanismo seguro da plataforma. Variáveis de
ambiente são adequadas para o laboratório local; em produção, use o secret
manager aprovado pela plataforma.

## Limites para produção

O Compose é para uso local. O [Kafka](./kafka-seguro.md) usa TLS e
autenticação, e o Keycloak usa `start-dev`. Uma implantação de produção
requer CA e cofre aprovados, imagens aprovadas e uma conta PostgreSQL de
menor privilégio.

Os escopos JWT são válidos para todo o laboratório e não implementam
isolamento de locatários ou propriedade de boleto. Essa decisão permanece
pendente antes de dados não sintéticos ou operação multi-tenant.
