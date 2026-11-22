# Portal técnico do Boleto Payment Lab

Use Node.js 22 ou superior. A partir da raiz do repositório:

```powershell
npm --prefix docs-site ci
npm --prefix docs-site start
```

Abra [http://127.0.0.1:3000](http://127.0.0.1:3000).

Para conferir a versão estática e o índice de pesquisa local:

```powershell
npm --prefix docs-site run typecheck
npm --prefix docs-site run build
npm --prefix docs-site run serve
```

O build fica em `docs-site/build/`. O CI verifica TypeScript e o build em PRs
sem publicar o portal. A pesquisa usa um índice local gerado durante o build.

As páginas ficam em `docs/`. Atualize `sidebars.ts` ao adicionar uma rota.
Use links relativos entre páginas e mantenha requisitos e cenários em
`../specs/`. Exemplos do portal devem usar somente dados sintéticos.

O tema envolve os blocos Mermaid existentes com zoom, deslocamento,
expansão e exportação SVG. Inclua `accTitle`, `accDescr` e uma explicação
visível com links no texto da página. O React Flow é carregado no
Explorador de Arquitetura. Para imagens técnicas, informe texto
alternativo, legenda e dimensões no componente `TechnicalImage`.
