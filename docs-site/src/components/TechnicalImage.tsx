import React, {useState} from 'react';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Lightbox from 'yet-another-react-lightbox';
import Captions from 'yet-another-react-lightbox/plugins/captions';
import Zoom from 'yet-another-react-lightbox/plugins/zoom';
import 'yet-another-react-lightbox/styles.css';
import 'yet-another-react-lightbox/plugins/captions.css';

type Image = {src: string; alt: string; caption: string; width: number; height: number};

export default function TechnicalImage({images}: {images: Image[]}) {
  const baseUrl = useBaseUrl('/');
  const [index, setIndex] = useState<number | null>(null);
  const slides = images.map(({src, caption, ...image}) => ({
    ...image,
    src: src.startsWith('/') && !src.startsWith('//') ? baseUrl + src.slice(1) : src,
    description: caption,
  }));

  return <>
    <div className="bpl-image-grid">
      {slides.map(({src, alt, description, width, height}, position) => <figure key={src} className="bpl-image">
        <button type="button" aria-label={`Ampliar imagem: ${alt}`} onClick={() => setIndex(position)}>
          <img src={src} alt={alt} width={width} height={height} loading="lazy" />
        </button>
        <figcaption>{description}</figcaption>
      </figure>)}
    </div>
    {index !== null && <Lightbox open index={index} close={() => setIndex(null)} slides={slides}
      plugins={[Captions, Zoom]} carousel={{finite: true}}
      controller={{aria: true}} animation={{fade: 0, swipe: 0, zoom: 0}}
      labels={{Close: 'Fechar', Next: 'Próxima imagem', Previous: 'Imagem anterior',
        'Zoom in': 'Aumentar zoom', 'Zoom out': 'Reduzir zoom'}} />}
  </>;
}
