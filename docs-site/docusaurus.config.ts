import type {Config} from '@docusaurus/types';

const config: Config = {
  title: 'Boleto Payment Lab',
  tagline: 'Documentação técnica do laboratório educacional de boletos',
  url: 'http://127.0.0.1:3000',
  baseUrl: '/',
  favicon: 'img/favicon.png',
  trailingSlash: false,
  noIndex: true,
  clientModules: ['./src/mermaid-transitions.js'],
  onBrokenLinks: 'throw',
  i18n: {
    defaultLocale: 'pt-BR',
    locales: ['pt-BR'],
  },
  markdown: {
    mermaid: true,
    hooks: {onBrokenMarkdownLinks: 'throw'},
  },
  themes: [
    '@docusaurus/theme-mermaid',
    [
      '@easyops-cn/docusaurus-search-local',
      {
        language: 'pt',
        docsRouteBasePath: '/',
        indexBlog: false,
        hashed: true,
        forceIgnoreNoIndex: true,
      },
    ],
  ],
  presets: [
    [
      'classic',
      {
        docs: {
          routeBasePath: '/',
          sidebarPath: './sidebars.ts',
        },
        blog: false,
        theme: {customCss: './src/css/custom.css'},
      },
    ],
  ],
  themeConfig: {
    colorMode: {respectPrefersColorScheme: true},
    navbar: {
      title: 'Boleto Payment Lab',
      items: [
        {to: '/inicio/executar', label: 'Começar', position: 'left'},
        {to: '/arquitetura/explorador', label: 'Explorador', position: 'left'},
        {to: '/referencia/api', label: 'API REST', position: 'left'},
        {to: '/operacao/observabilidade', label: 'Operação', position: 'left'},
        {to: '/roadmap', label: 'Roadmap', position: 'right'},
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Documentação',
          items: [
            {label: 'Visão geral', to: '/'},
            {label: 'Executar o laboratório', to: '/inicio/executar'},
            {label: 'Decisões arquiteturais', to: '/decisoes'},
          ],
        },
      ],
      copyright: 'Boleto Payment Lab · Laboratório educacional',
    },
  },
};

export default config;
