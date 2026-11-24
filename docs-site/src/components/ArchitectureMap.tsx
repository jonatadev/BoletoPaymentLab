import React, {useState} from 'react';
import Link from '@docusaurus/Link';
import {useColorMode} from '@docusaurus/theme-common';
import {Background, Controls, MarkerType, MiniMap, Position, ReactFlow, type Edge, type Node} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

const components = [
  {id: 'client', label: 'Cliente HTTP', kind: 'Cliente', x: 0, y: 160, path: '/inicio/executar', detail: 'Postman ou outro cliente local obtém um token e chama a API.'},
  {id: 'oidc', label: 'Keycloak', kind: 'Identidade', x: 240, y: 0, path: '/operacao/seguranca', detail: 'Emite tokens e fornece as chaves públicas JWKS.'},
  {id: 'api', label: 'Spring MVC', kind: 'API', x: 240, y: 160, path: '/referencia/api', detail: 'Recebe requisições REST, valida escopos JWT e devolve respostas HTTP.'},
  {id: 'app', label: 'Casos de uso', kind: 'Domínio e aplicação', x: 500, y: 160, path: '/arquitetura/dominio', detail: 'Orquestram emissão, replay, consultas, transições e geração do documento.'},
  {id: 'pdf', label: 'PDFBox', kind: 'Documento', x: 770, y: 0, path: '/arquitetura/dominio#emissão-e-documento', detail: 'Renderiza o PDF educacional em memória, sob demanda.'},
  {id: 'db', label: 'PostgreSQL', kind: 'Banco', x: 770, y: 160, path: '/operacao/dados', detail: 'Armazena boletos, associações idempotentes, auditoria, outbox e recibos de deduplicação.'},
  {id: 'outbox', label: 'Relay e consumidor', kind: 'Mensageria', x: 500, y: 350, path: '/referencia/eventos', detail: 'O relay publica eventos pendentes. O consumidor registra recibos por ID do evento.'},
  {id: 'kafka', label: 'Kafka', kind: 'Mensageria', x: 770, y: 350, path: '/operacao/kafka-seguro', detail: 'Broker em KRaft com SCRAM-SHA-512 sobre TLS e ACLs literais. O plano de controle usa mTLS.'},
  {id: 'jaeger', label: 'Jaeger', kind: 'Observabilidade', x: 1030, y: 55, path: '/operacao/observabilidade', detail: 'Recebe spans por OTLP/HTTP quando a exportação está habilitada.'},
];

const connections = [
  ['client', 'oidc', 'OAuth2 HTTP'],
  ['client', 'api', 'HTTP + Bearer JWT'],
  ['api', 'oidc', 'JWKS HTTP'],
  ['api', 'app', 'Chamadas Java'],
  ['app', 'pdf', 'PDF em memória'],
  ['app', 'db', 'JPA / JDBC'],
  ['outbox', 'db', 'JDBC'],
  ['outbox', 'kafka', 'SASL_SSL'],
  ['api', 'jaeger', 'OTLP/HTTP'],
  ['outbox', 'jaeger', 'OTLP/HTTP'],
];

const colors: Record<string, string> = {
  Cliente: '#64748b', Identidade: '#9333a8', API: '#2563eb',
  'Domínio e aplicação': '#148067', Documento: '#7c639e', Banco: '#bf7219',
  Mensageria: '#b53c66', Observabilidade: '#32798c',
};

const edges: Edge[] = connections.map(([source, target, label]) => ({
  id: `${source}-${target}`, source, target, label, markerEnd: {type: MarkerType.ArrowClosed},
}));

export default function ArchitectureMap() {
  const {colorMode} = useColorMode();
  const [selected, setSelected] = useState(components[2]);
  const nodes: Node[] = components.map((component) => ({
    id: component.id,
    position: {x: component.x, y: component.y},
    sourcePosition: Position.Right,
    targetPosition: Position.Left,
    selected: component.id === selected.id,
    ariaLabel: `${component.label}, ${component.kind}`,
    data: {label: <><span>{component.kind}</span><strong>{component.label}</strong></>},
    style: {borderInlineStartColor: colors[component.kind]},
  }));

  function select(id: string) {
    const component = components.find((item) => item.id === id);
    if (component) setSelected(component);
  }

  return <div className="bpl-explorer">
    <div className="bpl-map" role="region" aria-label="Mapa de componentes">
      <ReactFlow nodes={nodes} edges={edges} fitView minZoom={0.18} maxZoom={2}
        colorMode={colorMode} nodesDraggable={false} nodesConnectable={false}
        preventScrolling={false} zoomOnScroll={false} zoomOnDoubleClick={false}
        ariaLabelConfig={{'controls.ariaLabel': 'Controles do mapa',
          'controls.zoomIn.ariaLabel': 'Aumentar zoom do mapa',
          'controls.zoomOut.ariaLabel': 'Reduzir zoom do mapa',
          'controls.fitView.ariaLabel': 'Ajustar mapa à tela',
          'minimap.ariaLabel': 'Minimapa da arquitetura',
          'node.a11yDescription.default': 'Pressione Enter ou Espaço para selecionar.'}}
        onNodeClick={(_, node) => select(node.id)}
        onNodesChange={(changes) => {
          for (const change of changes) {
            if (change.type === 'select' && change.selected) select(change.id);
          }
        }}>
        <Background gap={24} />
        <Controls showInteractive={false} />
        <MiniMap pannable zoomable nodeColor={(node) =>
          colors[components.find((item) => item.id === node.id)?.kind ?? 'Cliente']} />
      </ReactFlow>
    </div>
    <aside className="bpl-map-detail" aria-label="Detalhes do componente">
      <label htmlFor="bpl-component">Componente</label>
      <select id="bpl-component" value={selected.id} onChange={(event) => select(event.target.value)}>
        {components.map((node) => <option key={node.id} value={node.id}>{node.label}</option>)}
      </select>
      <div aria-live="polite">
        <h3>{selected.label}</h3>
        <p>{selected.detail}</p>
      </div>
      <Link to={selected.path}>Ler documentação</Link>
    </aside>
  </div>;
}
