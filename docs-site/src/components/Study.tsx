import React, {type ReactNode} from 'react';
import Link from '@docusaurus/Link';

export function Decision({adr, href, children}: {adr: string; href: string; children: ReactNode}) {
  return <aside className="alert alert--info margin-bottom--md" aria-label={`Decisão arquitetural · ${adr}`}>
    <strong>Decisão arquitetural · {adr}</strong>
    <p>{children}</p>
    <Link to={href}>Ler {adr}</Link>
  </aside>;
}

export function Comparison({rows}: {rows: {option: string; tradeoff: string}[]}) {
  return <div className="bpl-table-scroll"><table>
    <thead><tr><th scope="col">Alternativa</th><th scope="col">Implicação</th></tr></thead>
    <tbody>{rows.map(({option, tradeoff}) => <tr key={option}>
      <th scope="row">{option}</th><td>{tradeoff}</td>
    </tr>)}</tbody>
  </table></div>;
}

export function Steps({items}: {items: string[]}) {
  return <ol className="bpl-steps">{items.map((item) => <li key={item}>{item}</li>)}</ol>;
}

export function Glossary({terms}: {terms: {name: string; meaning: string; href?: string}[]}) {
  return <dl className="bpl-glossary">{terms.map(({name, meaning, href}) => <React.Fragment key={name}>
    <dt>{href ? <Link to={href}>{name}</Link> : name}</dt><dd>{meaning}</dd>
  </React.Fragment>)}</dl>;
}
