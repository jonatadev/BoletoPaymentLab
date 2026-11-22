---
title: ADR 006 · Fronteiras dos casos de uso
---

## Contexto

O serviço de emissão também fornecia o modelo de leitura do boleto e a
consulta usada pelo PDF. O controlador coordenava a busca, a renderização
e o tratamento de falhas. A auditoria e o consumidor Kafka mantinham nomes
de eventos em pontos distintos.

## Decisão

`BoletoDocuments` coordena a consulta e a renderização por `PdfRenderer`.
`BoletoQueries` encerra a transação de leitura antes da geração do PDF.
`Snapshot` é um record da aplicação compartilhado pelas respostas e pelo
renderizador. Os nomes dos eventos v1 ficam em `BoletoEventType` e continuam
representados como strings no banco e no Kafka.

Os comandos de ciclo de vida concluem a transação antes de comunicar um
conflito. Assim, uma tentativa de pagamento de um boleto vencido pode
confirmar o vencimento, a auditoria e o evento e ainda retornar HTTP 409.

## Alternativas avaliadas

Manter a orquestração no controlador faria a política de renderização
depender da entrada HTTP. Criar interfaces para cada repositório
duplicaria operações do Spring Data e esconderia detalhes relevantes de
bloqueio do PostgreSQL. Usar objetos por estado ampliaria a estrutura
necessária para as quatro situações do boleto. A decisão mantém o
[monólito modular](./monolito.md) e os limites do
[Transactional Outbox](./outbox.md).

A revisão em `specs/architecture-review.md` vincula esta decisão a
`RF-BPL-001`, `RF-BPL-007`, `RF-BPL-009` e `RF-BPL-010` e aos
respectivos cenários de aceitação.
