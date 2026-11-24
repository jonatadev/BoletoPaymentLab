import type {SidebarsConfig} from '@docusaurus/plugin-content-docs';

const sidebars: SidebarsConfig = {
  manual: [
    'index',
    {
      type: 'category',
      label: 'Começar',
      collapsed: false,
      items: ['inicio/executar', 'inicio/estudo', 'inicio/testes', 'inicio/arquitetura-de-testes', 'inicio/cobertura', 'inicio/sdd'],
    },
    {
      type: 'category',
      label: 'Arquitetura',
      collapsed: false,
      items: ['arquitetura/visao-geral', 'arquitetura/explorador', 'arquitetura/dominio', 'arquitetura/idempotencia', 'arquitetura/auditoria'],
    },
    {
      type: 'category',
      label: 'Referência',
      collapsed: false,
      items: ['referencia/api', 'referencia/eventos'],
    },
    {
      type: 'category',
      label: 'Operação',
      items: ['operacao/docker', 'operacao/dados', 'operacao/seguranca', 'operacao/configuracao-segura', 'operacao/kafka-seguro', 'operacao/observabilidade', 'operacao/mcp-analytics'],
    },
    {
      type: 'category',
      label: 'Decisões arquiteturais',
      link: {type: 'doc', id: 'decisoes/index'},
      items: [
        'decisoes/monolito',
        'decisoes/idempotencia',
        'decisoes/outbox',
        'decisoes/envers',
        'decisoes/jwt',
        'decisoes/avro',
        'decisoes/fronteiras-de-aplicacao',
        'decisoes/kafka',
      ],
    },
    'roadmap',
  ],
};

export default sidebars;
