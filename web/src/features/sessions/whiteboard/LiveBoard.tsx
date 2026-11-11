import { CaptureUpdateAction, Excalidraw } from '@excalidraw/excalidraw';
import type { AppState, ExcalidrawImperativeAPI } from '@excalidraw/excalidraw/types';
import { useCallback, useEffect, useMemo, useRef, useState, type DragEvent } from 'react';
import { useToast } from '@/shared/ui';
import type { WhiteboardChannel } from './channel';
import { BoardMenu, INITIAL_APP_STATE, frame, UI_OPTIONS, onPaste, rejectEmbeds, restore } from './excalidraw';
import { withValidIds, type SceneElement } from './scene';
import { BoardSync } from './sync';

/** While somebody draws, what changed goes at most this often; letting go sends it at once. */
export const SEND_EVERY_MS = 80;

/** The elements Excalidraw is editing right now: what arrives does not take them from under the pointer. */
function editingOf(state: AppState): Set<string> {
  const ids = [
    state.newElement?.id,
    state.editingTextElement?.id,
    state.resizingElement?.id,
    state.multiElement?.id,
    state.editingLinearElement?.elementId,
  ];
  if (state.selectedElementsAreBeingDragged) ids.push(...Object.keys(state.selectedElementIds));
  return new Set(ids.filter((id): id is string => Boolean(id)));
}

/** Dropping a file would open it or insert it: neither is part of the board. */
function stopFiles(event: DragEvent) {
  if (Array.from(event.dataTransfer?.types ?? []).includes('Files')) {
    event.preventDefault();
    event.stopPropagation();
  }
}

/**
 * FE-22 · the board of the room, drawn with Excalidraw (ADR 0010). The channel orders what both
 * participants do (ADR 0009): this component only turns the scene into operations and the board
 * into the scene, through `BoardSync`. Undo and redo are Excalidraw's, of each person's own
 * changes, because what arrives never enters the history.
 */
export default function LiveBoard({
  channel,
  ready,
  readOnly,
  refused,
}: {
  channel: WhiteboardChannel;
  ready: boolean;
  readOnly: boolean;
  /** The server refused the last thing sent. */
  refused: boolean;
}) {
  const toast = useToast();
  const [api, setApi] = useState<ExcalidrawImperativeAPI | null>(null);
  const sync = useMemo(() => new BoardSync(restore), []);
  const timer = useRef<number | undefined>(undefined);
  // Excalidraw sets its scene up asynchronously from `initialData`, replacing whatever was put in
  // it before: the first board is that initial data, and what arrives next waits for it.
  const first = useMemo(() => {
    let resolve: (elements: SceneElement[]) => void = () => {};
    const promise = new Promise<SceneElement[]>((done) => (resolve = done));
    return { promise, resolve, given: false };
  }, []);
  const [initialized, setInitialized] = useState(false);

  /** What the board says, into the scene, without entering the undo history. */
  const receive = useCallback(() => {
    const board = channel.getSnapshot();
    if (!board.ready) return;
    if (!initialized || !api) {
      if (!first.given) {
        first.given = true;
        first.resolve(sync.receive(board.elements, []) ?? []);
      }
      return;
    }
    const next = sync.receive(board.elements, api.getSceneElementsIncludingDeleted(), editingOf(api.getAppState()));
    if (next) api.updateScene({ elements: next, captureUpdate: CaptureUpdateAction.NEVER });
  }, [api, channel, first, initialized, sync]);

  const initialData = useCallback(async () => {
    const elements = await first.promise;
    // Excalidraw applies what this answers as soon as it is answered; from then on, the scene is its.
    window.setTimeout(() => setInitialized(true), 0);
    return { elements, appState: INITIAL_APP_STATE };
  }, [first]);

  useEffect(() => {
    if (initialized && api) frame(api);
  }, [initialized, api]);

  /** What changed in the scene, to the server. */
  const send = useCallback(() => {
    window.clearTimeout(timer.current);
    timer.current = undefined;
    if (!api || !initialized || !channel.getSnapshot().ready) return;
    const scene = api.getSceneElementsIncludingDeleted() as SceneElement[];
    const renamed = withValidIds(scene, () => crypto.randomUUID());
    if (renamed) {
      api.updateScene({ elements: renamed, captureUpdate: CaptureUpdateAction.NEVER });
      return;
    }
    let refused: 'TOO_BIG' | 'FULL' | null = null;
    for (const change of sync.changes(scene)) {
      if (change.action === 'PUT') {
        const result = channel.put(change.element);
        if (result !== 'OK') refused = result;
      } else if (change.action === 'REMOVE') {
        channel.remove(change.elementId);
      } else {
        channel.clear();
      }
    }
    if (refused) {
      toast.error(
        refused === 'FULL'
          ? 'La pizarra está llena. Borra algo para seguir.'
          : 'Eso es demasiado grande para la pizarra. Prueba con un trazo más corto.',
      );
      // The board does not have it: receiving it again takes it out of the scene too.
      receive();
    }
  }, [api, channel, initialized, receive, sync, toast]);

  const sendSoon = useCallback(() => {
    if (timer.current === undefined) timer.current = window.setTimeout(send, SEND_EVERY_MS);
  }, [send]);

  useEffect(() => {
    receive();
    return channel.subscribe(receive);
  }, [channel, receive]);

  useEffect(() => () => window.clearTimeout(timer.current), []);

  useEffect(() => {
    if (refused) toast.error('La pizarra no guardó lo último que dibujaste. Inténtalo otra vez.');
  }, [refused, toast]);

  return (
    <div className="ayni-board absolute inset-0" onDragOverCapture={stopFiles} onDropCapture={stopFiles}>
      <Excalidraw
        excalidrawAPI={setApi}
        initialData={initialData}
        onChange={sendSoon}
        // After Excalidraw has finished the element it was drawing.
        onPointerUp={() => window.setTimeout(send, 0)}
        onPaste={onPaste}
        langCode="es-ES"
        theme="light"
        viewModeEnabled={!ready || !initialized || readOnly}
        UIOptions={UI_OPTIONS}
        validateEmbeddable={rejectEmbeds}
        aiEnabled={false}
        name="Pizarra"
      >
        <BoardMenu live />
      </Excalidraw>
    </div>
  );
}
