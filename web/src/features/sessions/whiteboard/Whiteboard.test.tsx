import { act, fireEvent, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import { SESSION_OVER, backoffMs } from './channel';
import { FakeSocket } from './fakeSocket';
import { Whiteboard } from './Whiteboard';

const STROKE = {
  id: 's1',
  type: 'stroke',
  data: {
    points: [
      [1, 1],
      [50, 50],
    ],
    color: '#000000',
    width: 3,
  },
};
const NOTE = { id: 'n1', type: 'note', data: { x: 10, y: 10, text: 'Hola', author: 'Bruno' } };

function last() {
  return FakeSocket.instances[FakeSocket.instances.length - 1];
}

function setup(onClosed?: () => void) {
  serveFrame(fakeServer());
  signIn('STUDENT');
  return renderWithProviders(<Whiteboard sessionId="x" onClosed={onClosed} />);
}

function open(version = 1, elements: unknown[] = []) {
  act(() => last().accept(version, elements));
}

function board() {
  return screen.getByRole('img');
}

/** jsdom has no PointerEvent: a mouse event of that name carries what the handlers read. */
function pointer(type: string, x: number, y: number) {
  fireEvent(board(), new MouseEvent(type, { bubbles: true, button: 0, clientX: x, clientY: y }));
}

function strokeAcross() {
  pointer('pointerdown', 10, 10);
  pointer('pointermove', 40, 40);
  pointer('pointerup', 40, 40);
}

function chip() {
  return screen.getAllByRole('status').find((element) => element.tagName === 'P') as HTMLElement;
}

describe('FE-22 Whiteboard', () => {
  beforeEach(() => {
    FakeSocket.instances = [];
    vi.stubGlobal('WebSocket', FakeSocket);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('says it is connecting, opens with the token, and draws the board that arrives', () => {
    setup();

    expect(chip()).toHaveTextContent('Conectando la pizarra…');
    expect(last().protocols[0]).toBe('bearer');
    expect(screen.getByRole('button', { name: 'Nota' })).toBeDisabled();

    open(3, [NOTE]);

    expect(chip()).toHaveTextContent('Guardado · podrás repasarla después');
    expect(board()).toHaveAccessibleName(/Hola/);
  });

  it('draws at once and sends the stroke when it ends, once', () => {
    setup();
    open();

    pointer('pointerdown', 10, 10);
    pointer('pointermove', 40, 40);
    expect(last().sent).toHaveLength(0);
    expect(document.querySelector('path[stroke="#1D2033"]')).not.toBeNull();

    pointer('pointerup', 40, 40);

    expect(last().sent).toHaveLength(1);
    const { action, element } = last().sent[0].operation as { action: string; element: { type: string } };
    expect(action).toBe('PUT_ELEMENT');
    expect(element.type).toBe('stroke');
    expect(chip()).toHaveTextContent('Guardando…');

    act(() => last().say({ type: 'applied', version: 2, operation: last().sent[0].operation }));
    expect(chip()).toHaveTextContent('Guardado');
  });

  it('draws with the chosen colour', () => {
    setup();
    open();

    fireEvent.click(screen.getByRole('button', { name: 'Fucsia' }));
    strokeAcross();

    const { element } = last().sent[0].operation as { element: { data: { color: string } } };
    expect(element.data.color).toBe('#E6007E');
  });

  it('adds a note signed by whoever writes it', async () => {
    const { user } = setup();
    open();

    await user.click(screen.getByRole('button', { name: 'Nota' }));
    const dialog = within(screen.getByRole('dialog', { name: 'Agregar una nota' }));
    expect(dialog.getByRole('button', { name: 'Agregar' })).toBeDisabled();
    await user.type(dialog.getByLabelText('La nota'), '¿Por qué 1.96?');
    await user.click(dialog.getByRole('button', { name: 'Agregar' }));

    const { element } = last().sent[0].operation as { element: { type: string; data: Record<string, unknown> } };
    expect(element.type).toBe('note');
    expect(element.data).toMatchObject({ text: '¿Por qué 1.96?', author: 'Ana' });
  });

  it('takes back only its own drawing, and brings it back', () => {
    setup();
    open(1, [NOTE]);

    expect(screen.getByRole('button', { name: 'Deshacer' })).toBeDisabled();
    strokeAcross();
    const drawn = last().sent[0].operation as { element: { id: string } };

    fireEvent.click(screen.getByRole('button', { name: 'Deshacer' }));
    expect(last().sent[1].operation).toMatchObject({ action: 'REMOVE_ELEMENT', elementId: drawn.element.id });
    expect(screen.getByRole('button', { name: 'Deshacer' })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: 'Rehacer' }));
    expect(last().sent[2].operation).toMatchObject({ action: 'PUT_ELEMENT', element: { id: drawn.element.id } });
  });

  it('asks before clearing everything', async () => {
    const { user } = setup();
    open(1, [STROKE]);

    await user.click(screen.getByRole('button', { name: 'Limpiar la pizarra' }));
    expect(last().sent).toHaveLength(0);

    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Limpiar' }));

    expect(last().sent[0].operation).toMatchObject({ action: 'CLEAR' });
    expect(screen.getByRole('button', { name: 'Limpiar la pizarra' })).toBeDisabled();
  });

  it('says it is reconnecting, keeps the drawing, and sends it on coming back', () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    setup();
    open(1, [NOTE]);

    act(() => last().drop());
    expect(chip()).toHaveTextContent('Reconectando… la pizarra vuelve completa');
    expect(board()).toHaveAccessibleName(/Hola/);

    strokeAcross();
    expect(FakeSocket.instances).toHaveLength(1);

    act(() => {
      vi.advanceTimersByTime(backoffMs(1));
    });
    expect(FakeSocket.instances).toHaveLength(2);
    open(2, [NOTE]);

    expect(last().sent).toHaveLength(1);
    expect(chip()).toHaveTextContent('Guardando…');
  });

  it('stays to be read once the session is over, and says so to the room', () => {
    const onClosed = vi.fn();
    setup(onClosed);
    open(1, [NOTE]);

    act(() => last().drop(SESSION_OVER));

    expect(chip()).toHaveTextContent('La sesión terminó. La pizarra quedó guardada');
    expect(screen.queryByRole('toolbar')).not.toBeInTheDocument();
    expect(board()).toHaveAccessibleName(/Hola/);
    expect(onClosed).toHaveBeenCalledTimes(1);

    strokeAcross();
    expect(last().sent).toHaveLength(0);
  });

  it('says when the board is full, in Spanish', () => {
    setup();
    open(
      1,
      Array.from({ length: 2000 }, (_, index) => ({ ...STROKE, id: `e${index}` })),
    );

    strokeAcross();

    expect(last().sent).toHaveLength(0);
    expect(screen.getByText('La pizarra está llena. Borra algo para seguir.')).toBeInTheDocument();
  });
});
