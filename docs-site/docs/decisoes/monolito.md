---
title: ADR 001 · Monólito modular
---

**Situação:** confirmada no código.

## Contexto

Emissão, idempotência, ciclo de vida e auditoria precisam compartilhar
transações. Os componentes operacionais podem rodar localmente como serviços
separados.

## Decisão

Manter uma aplicação Spring Boot em pacotes por responsabilidade. PostgreSQL
é a fonte de verdade. Kafka distribui eventos depois do commit por meio
do Outbox. PDFBox permanece dentro do processo e gera documentos sob demanda.

## Consequências

Um artefato de aplicação simplifica o ambiente de desenvolvimento e permite
consistência transacional. Escala e disponibilidade da API permanecem
compartilhadas entre as funcionalidades do processo.
Consulte [Arquitetura e pacotes](../arquitetura/visao-geral.md).
