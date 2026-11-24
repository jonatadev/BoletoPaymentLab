---
title: ADR 008 — Segurança do Kafka
---

O Kafka 4.2.2 permanece em KRaft no laboratório. Aplicação e UI usam
SCRAM-SHA-512 sobre TLS. O tráfego do nó e o provisionamento usam mTLS.
A identidade administrativa por certificado permite provisionar credenciais
SCRAM em um volume novo ou existente com a política de negação já ativa.

O StandardAuthorizer aplica ACLs literais. O escopo da UI permite consultar
metadados e mantém a leitura de mensagens negada pelo broker. O fluxo
transacional e a deduplicação preservam as garantias do
[ADR 003](./outbox.md).

A topologia reduz o alcance de clientes e separa as credenciais
operacionais. A contrapartida é a necessidade de distribuir a CA e
planejar rotação e reinício do nó único. O
[guia de operação e diagrama](../operacao/kafka-seguro.md) detalha as
identidades, o provisionamento, a validação e a recuperação.
