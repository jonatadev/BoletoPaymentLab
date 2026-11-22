---
title: ADR 003 · Transactional Outbox
---

**Situação:** confirmada no código.

## Contexto

A alteração de um boleto deve ser recuperável se o broker estiver
indisponível após a atualização no banco.

## Decisão

Persistir o evento e a auditoria com a alteração no PostgreSQL. Um relay
consulta eventos pendentes com `SKIP LOCKED`, aguarda a confirmação Kafka
e registra a publicação. Consumidores usam um recibo único por evento.

## Consequências

A aplicação pode confirmar o comando sem Kafka disponível. A confirmação
no broker e a atualização do Outbox ocorrem em sistemas distintos. Uma
queda entre esses passos pode repetir a entrega. A estratégia exige
deduplicação e retenção dos registros.

A V5 exige que evento, boleto e versão do Outbox correspondam à mesma
linha de auditoria. O índice único adicional serve de referência para a
chave estrangeira composta. Manter o índice de polling evita alterar a
seleção de eventos durante este reforço de integridade. A revisão em
`specs/outbox-integrity-review.md` liga a decisão a `RN-BPL-007` e aos
cenários S17–S19 de `UC-BPL-002`.

Versões anteriores que gravam a mesma tupla continuam compatíveis. Em
caso de rollback da aplicação, mantenha a V5. Se a migração falhar por
contenção ou por dados divergentes, investigue a causa antes de tentar
novamente.

Consulte [Kafka e Transactional Outbox](../referencia/eventos.md).
