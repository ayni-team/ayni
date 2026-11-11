import { Excalidraw } from '@excalidraw/excalidraw';
import type { ExcalidrawImperativeAPI } from '@excalidraw/excalidraw/types';
import { useCallback, useEffect, useMemo, useState } from 'react';
import type { BoardElement } from './board';
import { BoardMenu, INITIAL_APP_STATE, frame, UI_OPTIONS, rejectEmbeds, restore } from './excalidraw';
import { boardSummary, byIndex, sceneOf } from './scene';

/** The board of a past session, in Excalidraw's view mode, framed on what was drawn. */
export default function ReadOnlyBoard({ elements }: { elements: readonly BoardElement[] }) {
  const scene = useMemo(() => restore(sceneOf(elements)).sort(byIndex), [elements]);
  const [api, setApi] = useState<ExcalidrawImperativeAPI | null>(null);
  const [initialized, setInitialized] = useState(false);

  // Excalidraw sets the scene up asynchronously; framing it waits until it has.
  const initialData = useCallback(async () => {
    window.setTimeout(() => setInitialized(true), 0);
    return { elements: scene, appState: INITIAL_APP_STATE };
  }, [scene]);

  useEffect(() => {
    if (initialized && api) frame(api);
  }, [api, initialized]);

  return (
    <div className="ayni-board ayni-board--read absolute inset-0">
      <p className="sr-only">{boardSummary(scene)}</p>
      <Excalidraw
        excalidrawAPI={setApi}
        initialData={initialData}
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
