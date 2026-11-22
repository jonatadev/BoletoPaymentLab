# Adoção seletiva do Hibernate Envers

## Contexto

O requisito RF-BPL-009 prevê histórico autorizado. A trilha atual registra os eventos do ciclo de vida, e o Outbox garante sua publicação eventual. A investigação de alterações também precisa consultar o estado persistido associado a uma revisão.

## Decisão

Usar Spring Data JPA Auditing para metadados correntes e Envers para versionar somente `Boleto`. A revisão guarda o subject ou a identidade técnica e compartilha a correlação da transação com a trilha de negócio. Flyway mantém o schema e o Hibernate valida o mapeamento. O histórico exclui o identificador do cliente e usa o mesmo escopo da trilha existente.

## Alternativas consideradas

- Ampliar a trilha de negócio com snapshots duplicaria responsabilidades e exigiria mapear cada gravação.
- Triggers cobririam SQL externo, mas exigiriam transmitir autoria e correlação à sessão PostgreSQL.
- Auditar todas as entidades aumentaria o armazenamento e a exposição de dados além do caso de uso.

## Consequências

A revisão técnica acompanha o commit e pode ser consultada com Envers, sem uma transação de mensageria adicional. O custo inclui uma linha de estado por boleto alterado e uma linha de metadados por transação com revisão. Operações bulk e SQL direto ficam fora da cobertura. O legado mantém a trilha existente e passa a gerar revisões após a migração.

Veja o [fluxo de auditoria e as consultas](../arquitetura/auditoria.md) e a [decisão sobre Outbox](./outbox.md).
