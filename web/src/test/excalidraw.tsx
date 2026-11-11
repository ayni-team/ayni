import { useEffect, type ReactNode } from 'react';

/**
 * A double of `@excalidraw/excalidraw` for the tests: Excalidraw needs a canvas, fonts and layout
 * that jsdom does not have, so `setup.ts` puts this in its place everywhere. It keeps a scene as
 * Excalidraw would, answers the part of the imperative API the boards use, and lets a test draw
 * as a person would: the scene changes, then `onChange`, then `onPointerUp`.
 */
type Element = { id: string; type: string; version: number; versionNonce: number; isDeleted: boolean } & Record<
  string,
  unknown
>;

interface Props {
  excalidrawAPI?: (api: unknown) => void;
  initialData?: { elements?: Element[] } | (() => Promise<{ elements?: Element[] }>);
  onChange?: (elements: readonly Element[]) => void;
  onPointerUp?: () => void;
  viewModeEnabled?: boolean;
  langCode?: string;
  children?: ReactNode;
}

export interface FakeBoard {
  props: Props;
  elements: Element[];
  /** Calls of `updateScene`, with what they asked for. */
  updates: { elements?: Element[]; captureUpdate?: string }[];
  scrolled: number;
  /** A person changes the scene: Excalidraw tells `onChange` and then `onPointerUp`. */
  draw(change: (elements: Element[]) => Element[]): void;
}

export const fakeExcalidraw: { current: FakeBoard | null; all: FakeBoard[] } = { current: null, all: [] };

function Excalidraw(props: Props) {
  useEffect(() => {
    const board: FakeBoard = {
      props,
      elements: [],
      updates: [],
      scrolled: 0,
      draw(change) {
        board.elements = change(board.elements);
        board.props.onChange?.(board.elements);
        board.props.onPointerUp?.();
      },
    };
    fakeExcalidraw.current = board;
    fakeExcalidraw.all.push(board);
    // Like Excalidraw, the initial data is read once and applied asynchronously.
    void Promise.resolve(typeof props.initialData === 'function' ? props.initialData() : props.initialData).then(
      (data) => {
        board.elements = [...(data?.elements ?? [])];
      },
    );
    props.excalidrawAPI?.({
      getSceneElementsIncludingDeleted: () => board.elements,
      getSceneElements: () => board.elements.filter((element) => !element.isDeleted),
      getAppState: () => ({ selectedElementIds: {}, selectedElementsAreBeingDragged: false, width: 800, height: 600 }),
      updateScene: (scene: { elements?: Element[]; captureUpdate?: string }) => {
        board.updates.push(scene);
        if (scene.elements) board.elements = [...scene.elements];
        board.props.onChange?.(board.elements);
      },
      scrollToContent: () => {
        board.scrolled += 1;
      },
    });
    // The double is made once, as Excalidraw is; its props are refreshed below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  if (fakeExcalidraw.current) fakeExcalidraw.current.props = props;
  return (
    <div data-testid="excalidraw" data-view-mode={String(Boolean(props.viewModeEnabled))} lang={props.langCode}>
      {props.children}
    </div>
  );
}

function Item({ children }: { children?: ReactNode }) {
  return <span>{children}</span>;
}

const MainMenu = Object.assign(({ children }: { children?: ReactNode }) => <nav>{children}</nav>, {
  Separator: () => <hr />,
  DefaultItems: {
    ClearCanvas: () => <Item>clear</Item>,
    SaveAsImage: () => <Item>image</Item>,
    SearchMenu: () => <Item>search</Item>,
    Help: () => <Item>help</Item>,
    ChangeCanvasBackground: () => <Item>background</Item>,
  },
});

export const excalidrawModule = {
  Excalidraw,
  MainMenu,
  CaptureUpdateAction: { IMMEDIATELY: 'IMMEDIATELY', NEVER: 'NEVER', EVENTUALLY: 'EVENTUALLY' },
  restoreElements: (elements: Element[]) => elements.map((element) => ({ ...element })),
  getCommonBounds: () => [0, 0, 10, 10],
};
