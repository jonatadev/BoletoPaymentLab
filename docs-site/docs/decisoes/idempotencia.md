---
title: ADR 002 · Idempotência no PostgreSQL
---

**Situação:** confirmada no código.

## Contexto

Um cliente pode repetir uma emissão quando perde a resposta. Solicitações
concorrentes com a mesma chave também precisam de resultado previsível.

## Decisão

Usar a restrição única no escopo cliente, operação e chave. Reaproveitar
registros confirmados, serializar emissões novas com advisory lock sem
espera e reconsultar depois de obter o bloqueio.

## Consequências

A restrição impede duplicatas. A chave de bloqueio de 64 bits pode causar
contenção temporária em uma colisão de hash. Nesse caso a comparação
do escopo integral protege a identidade de cada emissão.
Consulte [Idempotência e concorrência](../arquitetura/idempotencia.md).
