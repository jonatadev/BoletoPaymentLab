---
title: Idempotência e concorrência
---

O `POST /api/v1/boletos` requer exatamente um `Idempotency-Key`. Seu escopo
é a combinação do cliente fictício, da operação fixa `ISSUE_BOLETO` e da
chave. A chave é sensível a maiúsculas e minúsculas, tem entre 1 e 160
caracteres ASCII imprimíveis sem espaços e não admite vírgula no HTTP.
Chaves não expiram automaticamente.

A comparação usa o cliente, o valor normalizado e a data ISO de vencimento.
`10` e `10.00` são equivalentes. Ordem e espaços do JSON não influenciam a
comparação. Após a emissão, o replay equivalente continua válido após a data
original de vencimento e retorna o mesmo UUID com o estado efetivo atual.

## Fluxo de emissão

```mermaid
sequenceDiagram
  accTitle: Emissão de boleto e commit
  accDescr: As setas indicam a ordem entre cliente, API e PostgreSQL.
  participant C as Cliente autorizado
  participant A as API de boletos
  participant P as PostgreSQL
  C->>A: POST com JWT, chave e corpo JSON
  A->>P: Consultar associação de idempotência
  P-->>A: Associação ausente
  A->>P: Obter bloqueio transacional sem esperar
  A->>P: Reconsultar associação e validar data de nova emissão
  A->>P: Persistir boleto, associação, auditoria e Outbox
  P-->>A: Commit e liberação do bloqueio
  A-->>C: 201 Created e Location
```

## Repetições e conflitos

```mermaid
flowchart TD
  accTitle: Resultados de uma chave idempotente
  accDescr: O fluxo distingue replay confirmado, conflito permanente e contenção temporária.
  Start["POST com o mesmo escopo"] --> Found{"Associação confirmada?"}
  Found -->|Sim| Match{"Dados equivalentes?"}
  Match -->|Sim| Replay["200 com o mesmo UUID"]
  Match -->|Não| Conflict["409 IDEMPOTENCY_CONFLICT"]
  Found -->|Não| Guard{"Bloqueio disponível?"}
  Guard -->|Não| Busy["409 OPERATION_IN_PROGRESS e Retry-After"]
  Guard -->|Sim| Read["Reler e decidir entre replay, conflito ou nova emissão"]
```

A restrição única `uq_issuance_scope` é a garantia de unicidade. O advisory
lock transacional de 64 bits permite responder rapidamente a disputas.
Uma colisão de hash pode induzir contenção temporária de escopos distintos,
mas a busca pelo escopo completo e a restrição impedem que os escopos
compartilhem um boleto. Após rollback, a chave pode ser tentada novamente.

Para o conflito temporário, aguarde o `Retry-After: 1` e repita a mesma
requisição. Para `IDEMPOTENCY_CONFLICT`, mantenha os dados originais ou use
uma chave nova para uma emissão diferente. Veja a
[tabela de respostas HTTP](../referencia/api.md).
