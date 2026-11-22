import React, {useEffect, useId, useRef, useState, type ReactNode} from 'react';

function DiagramViewport({children, label, onExpand}: {
  children: ReactNode;
  label: string;
  onExpand?: () => void;
}) {
  const [zoom, setZoom] = useState(100);
  const [ready, setReady] = useState(false);
  const viewport = useRef<HTMLDivElement>(null);
  const canvas = useRef<HTMLDivElement>(null);
  const drag = useRef<{x: number; y: number; left: number; top: number} | null>(null);
  const help = useId();

  useEffect(() => {
    const element = canvas.current!;
    const update = () => setReady(Boolean(element.querySelector('svg')));
    const observer = new MutationObserver(update);
    observer.observe(element, {childList: true, subtree: true});
    update();
    return () => observer.disconnect();
  }, []);

  function changeZoom(step: number) {
    setZoom((value) => Math.min(300, Math.max(50, value + step)));
  }

  function reset() {
    setZoom(100);
    viewport.current?.scrollTo(0, 0);
  }

  function download() {
    const original = canvas.current?.querySelector('svg');
    if (!original) return;
    const svg = original.cloneNode(true) as SVGSVGElement;
    svg.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
    if (original.viewBox.baseVal.width > 0) {
      svg.setAttribute('width', String(original.viewBox.baseVal.width));
      svg.setAttribute('height', String(original.viewBox.baseVal.height));
      svg.style.removeProperty('max-width');
    }
    const url = URL.createObjectURL(new Blob(
      [new XMLSerializer().serializeToString(svg)], {type: 'image/svg+xml;charset=utf-8'},
    ));
    const link = document.createElement('a');
    link.href = url;
    const slug = label.normalize('NFD').replace(/\p{M}/gu, '')
      .toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
    link.download = `${slug || 'diagrama'}.svg`;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  return <>
    <div className="bpl-tools" role="group" aria-label="Controles do diagrama">
      <button type="button" aria-label="Reduzir zoom" disabled={zoom === 50} onClick={() => changeZoom(-25)}>−</button>
      <output aria-label="Zoom do diagrama">{zoom}%</output>
      <button type="button" aria-label="Aumentar zoom" disabled={zoom === 300} onClick={() => changeZoom(25)}>+</button>
      <button type="button" onClick={reset}>Redefinir</button>
      {onExpand && <button type="button" onClick={onExpand}>Ampliar</button>}
      <button type="button" disabled={!ready} onClick={download}>Baixar SVG</button>
    </div>
    <div ref={viewport} className="bpl-diagram-viewport" role="region" tabIndex={0}
      aria-label={label} aria-describedby={help}
      onKeyDown={(event) => {
        if (event.target !== event.currentTarget) return;
        if (event.key === '+' || event.key === '-' || event.key === '0') {
          event.preventDefault();
          if (event.key === '0') reset();
          else changeZoom(event.key === '+' ? 25 : -25);
        }
      }}
      onPointerDown={(event) => {
        const element = event.currentTarget;
        if (event.pointerType === 'touch' || event.button !== 0
          || (event.target as Element).closest('a, button')
          || (element.scrollWidth <= element.clientWidth && element.scrollHeight <= element.clientHeight)) return;
        drag.current = {x: event.clientX, y: event.clientY, left: element.scrollLeft, top: element.scrollTop};
        element.setPointerCapture(event.pointerId);
      }}
      onPointerMove={(event) => {
        const start = drag.current;
        if (start) event.currentTarget.scrollTo(
          start.left + start.x - event.clientX, start.top + start.y - event.clientY,
        );
      }}
      onLostPointerCapture={() => { drag.current = null; }}>
      <div ref={canvas} className="bpl-diagram-canvas" style={{width: `${zoom}%`}}>{children}</div>
    </div>
    <p id={help} className="bpl-figure-help">Arraste ou use as setas para deslocar. Use +, − e 0 para ajustar o zoom.</p>
  </>;
}

export default function InteractiveDiagram({children, label = 'Diagrama Mermaid', caption}: {
  children: ReactNode;
  label?: string;
  caption?: string;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const expand = useRef<HTMLElement | null>(null);
  const title = useId();
  const [open, setOpen] = useState(false);
  useEffect(() => {
    if (open) dialog.current?.showModal();
  }, [open]);

  return <figure className="bpl-diagram">
    <DiagramViewport label={label} onExpand={() => {
      expand.current = document.activeElement as HTMLElement;
      setOpen(true);
    }}>{children}</DiagramViewport>
    {caption && <figcaption className="bpl-diagram-caption"><strong>{label}.</strong> {caption}</figcaption>}
    {open && <dialog ref={dialog} className="bpl-dialog" aria-labelledby={title}
      onClose={() => { setOpen(false); expand.current?.focus(); }}>
      <div className="bpl-dialog-heading">
        <strong id={title}>{label}</strong>
        <button type="button" autoFocus onClick={() => dialog.current?.close()}>Fechar</button>
      </div>
      <DiagramViewport label={label}>{children}</DiagramViewport>
    </dialog>}
  </figure>;
}
