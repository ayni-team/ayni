import { act, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fakeExcalidraw } from '@/test/excalidraw';
import { fakeServer } from '@/test/fakeServer';
import { serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import { SESSION_OVER, backoffMs } from './channel';
import { FakeSocket } from './fakeSocket';
import { SEND_EVERY_MS } from './LiveBoard';
import { toBoardElement, type SceneElement } from './scene';
import { Whiteboard } from './Whiteboard';

/**
 * FE-22 with the double of Excalidraw (`src/test/excalidraw.tsx`): jsdom cannot run the real one.
 * What it checks is the wiring: the board arrives into the scene, what a person draws leaves as
 * operations, once, and nothing received goes back. The tools themselves are Excalidraw's.
 */
function shape(id: string, extra: Record<string, unknown> = {}) {
  return {
    id,
    type: 'rectangle',
    x: 0,
    y: 0,
    width: 10,
    height: 10,
    version: 1,
    versionNonce: 7,
    isDeleted: false,
    index: 'a0',
    ...extra,
  };
}

const THEIRS = toBoardElement(shape('theirs') as unknown as SceneElement);

function last() {
  return FakeSocket.instances[FakeSocket.instances.length - 1];
}

async function setup(onClosed?: () => void) {
  serveFrame(fakeServer());
  signIn('STUDENT');
  renderWithProviders(<Whiteboard sessionId="x" onClosed={onClosed} />);
  await screen.findByTestId('excalidraw');
  return fakeExcalidraw.current!;
}

/** The board arrives; Excalidraw takes it as its initial data, asynchronously, as it does. */
async function open(version = 1, elements: unknown[] = []) {
  act(() => last().accept(version, elements));
  await act(async () => {
    await Promise.resolve();
    vi.advanceTimersByTime(1);
    await Promise.resolve();
  });
  // And frames it once it has measured it.
  await act(async () => {
    vi.advanceTimersByTime(100);
  });
}

function chip() {
  return screen.getAllByRole('status').find((element) => element.tagName === 'P') as HTMLElement;
}

function sentOperations() {
  return last().sent.map((message) => message.operation);
}

/** A person draws: the scene changes and, once Excalidraw finished the element, it is sent. */
function draw(change: Parameters<NonNullable<typeof fakeExcalidraw.current>['draw']>[0]) {
  act(() => {
    fakeExcalidraw.current!.draw(change);
    vi.advanceTimersByTime(0);
  });
}

describe('FE-22 Whiteboard', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    FakeSocket.instances = [];
    vi.stubGlobal('WebSocket', FakeSocket);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('opens with the token, in Spanish, and draws only once the board has arrived', async () => {
    const board = await setup();

    expect(chip()).toHaveTextContent('Conectando la pizarra…');
    expect(last().protocols[0]).toBe('bearer');
    expect(screen.getByTestId('excalidraw')).toHaveAttribute('lang', 'es-ES');
    expect(screen.getByTestId('excalidraw')).toHaveAttribute('data-view-mode', 'true');

    await open(3, [THEIRS]);

    expect(chip()).toHaveTextContent('Guardado · podrás repasarla después');
    expect(screen.getByTestId('excalidraw')).toHaveAttribute('data-view-mode', 'false');
    expect(board.elements.map((element) => element.id)).toEqual(['theirs']);
    expect(board.scrolled).toBe(1);
    // And it is not sent back.
    expect(last().sent).toEqual([]);
  });

  it('sends what is drawn, once, and then each edit', async () => {
    await setup();
    await open();

    draw((elements) => [...elements, shape('mine') as never]);

    expect(sentOperations()).toEqual([
      expect.objectContaining({ action: 'PUT_ELEMENT', element: expect.objectContaining({ id: 'mine' }) }),
    ]);
    expect(chip()).toHaveTextContent('Guardando…');

    draw((elements) => elements.map((element) => ({ ...element, x: 30, version: 2, versionNonce: 8 })));
    draw((elements) => elements.map((element) => ({ ...element, isDeleted: true, version: 3, versionNonce: 9 })));

    expect(sentOperations().map((operation) => operation.action)).toEqual([
      'PUT_ELEMENT',
      'PUT_ELEMENT',
      'REMOVE_ELEMENT',
    ]);
  });

  it('while somebody draws, sends at most every so often, and the last state when they let go', async () => {
    const board = await setup();
    await open();

    act(() => {
      for (let step = 1; step <= 10; step += 1) {
        board.elements = [shape('stroke', { type: 'freedraw', version: step, versionNonce: step }) as never];
        board.props.onChange?.(board.elements);
      }
    });
    expect(last().sent).toHaveLength(0);

    act(() => {
      vi.advanceTimersByTime(SEND_EVERY_MS);
    });
    expect(last().sent).toHaveLength(1);

    draw((elements) => elements.map((element) => ({ ...element, version: 11, versionNonce: 11 })));
    expect(sentOperations().at(-1)?.element).toMatchObject({ data: { version: 11 } });
  });

  it('what the other person draws, edits and deletes arrives into the scene', async () => {
    const board = await setup();
    await open(1, [THEIRS]);

    const moved = toBoardElement(shape('theirs', { x: 80, version: 2, versionNonce: 3 }) as unknown as SceneElement);
    act(() =>
      last().say({ type: 'applied', version: 2, operation: { id: 'b-1', action: 'PUT_ELEMENT', element: moved } }),
    );
    expect(board.elements[0]).toMatchObject({ x: 80 });

    act(() => last().say({ type: 'applied', version: 3, operation: { id: 'b-2', action: 'CLEAR' } }));
    expect(board.elements.filter((element) => !element.isDeleted)).toEqual([]);
    expect(last().sent).toEqual([]);
  });

  it('says it is reconnecting, keeps the drawing, and sends it on coming back', async () => {
    await setup();
    await open(1, [THEIRS]);

    act(() => last().drop());
    expect(chip()).toHaveTextContent('Reconectando… la pizarra vuelve completa');
    draw((elements) => [...elements, shape('offline') as never]);
    expect(FakeSocket.instances).toHaveLength(1);

    act(() => {
      vi.advanceTimersByTime(backoffMs(1));
    });
    expect(FakeSocket.instances).toHaveLength(2);
    await open(2, [THEIRS]);

    expect(sentOperations()).toEqual([
      expect.objectContaining({ action: 'PUT_ELEMENT', element: expect.objectContaining({ id: 'offline' }) }),
    ]);
    expect(fakeExcalidraw.current?.elements.map((element) => element.id).sort()).toEqual(['offline', 'theirs']);
  });

  it('stays to be read once the session is over, and says so to the room', async () => {
    const onClosed = vi.fn();
    await setup(onClosed);
    await open(1, [THEIRS]);

    act(() => last().drop(SESSION_OVER));

    expect(chip()).toHaveTextContent('La sesión terminó. La pizarra quedó guardada');
    expect(screen.getByTestId('excalidraw')).toHaveAttribute('data-view-mode', 'true');
    expect(onClosed).toHaveBeenCalledTimes(1);
  });

  it('says when the board is full, in Spanish, and takes out what did not fit', async () => {
    const board = await setup();
    await open(
      1,
      Array.from({ length: 2000 }, (_, index) => ({ ...THEIRS, id: `e${index}` })),
    );

    draw((elements) => [...elements, shape('one-more') as never]);

    expect(last().sent).toHaveLength(0);
    expect(screen.getByText('La pizarra está llena. Borra algo para seguir.')).toBeInTheDocument();
    expect(board.elements.find((element) => element.id === 'one-more')).toMatchObject({ isDeleted: true });
  });
});
