---
title: ADR 005 · Avaliar Avro
---

**Situação:** proposta. O contrato vigente continua JSON.

## Contexto

Os eventos Kafka têm versão explícita e são serializados com Jackson.
Consumidores adicionais podem exigir validação de esquema e regras de
compatibilidade verificáveis.

## Proposta

Avaliar Apache Avro e um Schema Registry. A adoção depende da escolha do
registro, da política de compatibilidade e da estratégia de transição para
consumidores JSON existentes.

## Questões abertas

- Definir nomes, versões e donos dos esquemas.
- Escolher como migrar consumidores e tópicos sem perder eventos pendentes.
- Avaliar comportamento quando o registro estiver indisponível.
- Especificar testes de compatibilidade e atualização de contratos.

Consulte o [contrato Kafka vigente](../referencia/eventos.md) e o
[roadmap](../roadmap.md).
