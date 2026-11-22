---
title: ADR 004 · JWT e escopos
---

**Situação:** confirmada no código e no realm local.

## Contexto

Emitir, consultar e alterar boletos exigem permissões diferentes, mesmo
com dados sintéticos.

## Decisão

Delegar a emissão de tokens ao Keycloak. Validar JWT no Spring Security
com chaves JWKS, emissor e audiência definidos. Usar escopos por rota e
clientes de serviço com permissões distintas.

## Consequências

O backend não recebe senhas de usuários. A API precisa das chaves públicas
do provedor para validar os tokens. Os escopos atuais têm alcance em todo
o laboratório. Consulte [Segurança e autenticação](../operacao/seguranca.md).
