import React from 'react';
import Mermaid from '@theme-original/Mermaid';
import InteractiveDiagram from '../../components/InteractiveDiagram';

export default function MermaidWrapper(props: {value: string}) {
  const title = props.value.match(/^\s*accTitle\s*:\s*(.+)$/m)?.[1];
  const description = props.value.match(/^\s*accDescr\s*:\s*(.+)$/m)?.[1];
  return <InteractiveDiagram label={title} caption={description}><Mermaid {...props} /></InteractiveDiagram>;
}
