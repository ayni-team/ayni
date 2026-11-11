import { Excalidraw } from '@excalidraw/excalidraw';
import type { ExcalidrawImperativeAPI } from '@excalidraw/excalidraw/types';
import { useEffect, useMemo, useState } from 'react';
import type { BoardElement } from './board';
import { BoardMenu, INITIAL_APP_STATE, UI_OPTIONS, rejectEmbeds, restore } from './excalidraw';
import { boardSummary, byIndex, sceneOf } from './scene';

/** The board of a past session, in Excalidraw's view mode, framed on what was drawn. */
export default function ReadOnlyBoard({ elements }: { elements: readonly BoardElement[] }) {
  const scene = useMemo(() => restore(sceneOf(elements)).sort(byIndex), [elements]);
  const [api, setApi] = useState<ExcalidrawImperativeAPI | null>(null);

  useEffect(() => {
    if (api && scene.length > 0) api.scrollToContent(undefined, { fitToContent: true, animate: false });
  }, [api, scene]);

  return (
    <div className="ayni-board ayni-board--read absolute inset-0">
      <p className="sr-only">{boardSummary(scene)}</p>
      <Excalidraw
        excalidrawAPI={setApi}
        initialData={{ elements: scene, appState: INITIAL_APP_STATE }}
        viewModeEnabled
        langCode="es-ES"
        theme="light"
        UIOptions={UI_OPTIONS}
        validateEmbeddable={rejectEmbeds}
        aiEnabled={false}
        name="Pizarra"
      >
        <BoardMenu live={false} />
      </Excalidraw>
    </div>
  );
}
