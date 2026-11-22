import React, {lazy, Suspense} from 'react';
import BrowserOnly from '@docusaurus/BrowserOnly';
import Link from '@docusaurus/Link';

const ArchitectureMap = lazy(() => import('./ArchitectureMap'));
const loading = <p role="status">Carregando mapa de arquitetura…</p>;

export default function ArchitectureExplorer() {
  return <BrowserOnly fallback={<p>Consulte a <Link to="/arquitetura/visao-geral">arquitetura e os pacotes</Link> para uma descrição textual do sistema.</p>}>
    {() => <Suspense fallback={loading}><ArchitectureMap /></Suspense>}
  </BrowserOnly>;
}
