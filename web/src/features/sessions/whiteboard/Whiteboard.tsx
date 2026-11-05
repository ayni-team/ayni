import { useRef, useState, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react';
import { useApiQuery } from '@/shared/api';
import { Button, Dialog, Field, Spinner, TextArea, cx, useToast } from '@/shared/ui';
import { BOARD_HEIGHT, BOARD_WIDTH, drawingOf, drawingsOf, type BoardElement, type Point } from './board';
import { BoardDrawing, boardSummary } from './BoardDrawings';
import {
  COLORS,
  NO_HISTORY,
  afterDrawing,
  afterRedo,
  afterUndo,
  firstName,
  noteElement,
  shapeElement,
  strokeElement,
  withPoint,
  withoutElement,
  type History,
  type Tool,
} from './drawing';
import { useWhiteboard } from './useWhiteboard';
import type { ChannelSnapshot } from './channel';

const NOTE_LIMIT = 200;

function Glyph({ children }: { children: ReactNode }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className="h-5 w-5"
      aria-hidden="true"
    >
      {children}
    </svg>
  );
}

const TOOL_ICONS: Record<Tool | 'note' | 'undo' | 'redo' | 'clear', ReactNode> = {
  pen: <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" />,
  rect: <rect x="4" y="4" width="16" height="16" rx="2" />,
  ellipse: <circle cx="12" cy="12" r="9" />,
  note: (
    <>
      <path d="M4 7V4h16v3" />
      <path d="M9 20h6" />
      <path d="M12 4v16" />
    </>
  ),
  eraser: (
    <>
      <path d="m7 21-4.3-4.3c-1-1-1-2.5 0-3.4l9.6-9.6c1-1 2.5-1 3.4 0l5.6 5.6c1 1 1 2.5 0 3.4L13 21" />
      <path d="M22 21H7" />
      <path d="m5 11 9 9" />
    </>
  ),
  undo: (
    <>
      <path d="M3 7v6h6" />
      <path d="M21 17a9 9 0 0 0-15-6.7L3 13" />
    </>
  ),
  redo: (
    <>
      <path d="M21 7v6h-6" />
      <path d="M3 17a9 9 0 0 1 15-6.7L21 13" />
    </>
  ),
  clear: (
    <>
      <path d="M3 6h18" />
      <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6" />
      <path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
    </>
  ),
};

function ToolButton({
  label,
  icon,
  active,
  disabled,
  onClick,
}: {
  label: string;
  icon: keyof typeof TOOL_ICONS;
  active?: boolean;
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      aria-pressed={active}
      disabled={disabled}
      onClick={onClick}
      className={cx(
        'flex h-10 w-10 shrink-0 items-center justify-center rounded-xl focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand disabled:text-ink-3',
        active ? 'bg-ink text-white' : 'text-ink-2 hover:bg-surface',
      )}
    >
      <Glyph>{TOOL_ICONS[icon]}</Glyph>
    </button>
  );
}

function statusOf(board: ChannelSnapshot): { text: string; tone: 'quiet' | 'loud' } {
  switch (board.status) {
    case 'CONNECTING':
      return { text: 'Conectando la pizarra…', tone: 'loud' };
    case 'RECONNECTING':
      return { text: 'Reconectando… la pizarra vuelve completa', tone: 'loud' };
    case 'OFFLINE':
      return { text: 'Sin conexión. Lo que dibujes se guarda al volver', tone: 'loud' };
    case 'CLOSED':
      return { text: 'La sesión terminó. La pizarra quedó guardada', tone: 'quiet' };
    case 'LIVE':
      return { text: board.unconfirmed > 0 ? 'Guardando…' : 'Guardado · podrás repasarla después', tone: 'quiet' };
  }
}

/** Where a pointer is on the board, whatever the size at which the board is drawn. */
function boardPoint(svg: SVGSVGElement, event: { clientX: number; clientY: number }): Point {
  const matrix = svg.getScreenCTM?.();
  if (!matrix) return [event.clientX, event.clientY];
  const point = new DOMPoint(event.clientX, event.clientY).matrixTransform(matrix.inverse());
  return [point.x, point.y];
}

function elementAt(event: { clientX: number; clientY: number }): string | null {
  const found = document.elementFromPoint?.(event.clientX, event.clientY);
  return found?.closest('[data-element]')?.getAttribute('data-element') ?? null;
}

type Draft = { kind: 'stroke'; points: readonly Point[] } | { kind: 'shape'; from: Point; to: Point };

/**
 * FE-22 · the shared whiteboard, in the room. Drawing shows at once and is sent when the stroke
 * ends; the server orders what both participants draw, and each ends with the same board. When the
 * session is over it stays to be read.
 */
export function Whiteboard({ sessionId, onClosed }: { sessionId: string; onClosed?: () => void }) {
  const toast = useToast();
  const board = useWhiteboard(sessionId, onClosed);
  const me = useApiQuery(['identity', 'me'], '/api/v1/me');
  const svg = useRef<SVGSVGElement>(null);
  const [tool, setTool] = useState<Tool>('pen');
  const [color, setColor] = useState<string>(COLORS[0].value);
  const [draft, setDraft] = useState<Draft | null>(null);
  const [history, setHistory] = useState<History>(NO_HISTORY);
  const [noteOpen, setNoteOpen] = useState(false);
  const [noteText, setNoteText] = useState('');
  const [notes, setNotes] = useState(0);
  const [clearing, setClearing] = useState(false);

  const readOnly = board.status === 'CLOSED';
  const canDraw = board.ready && !readOnly;
  const status = statusOf(board);
  const drawings = drawingsOf(board.elements as BoardElement[]);

  function commit(element: BoardElement) {
    const result = board.put(element);
    if (result === 'OK') {
      setHistory((current) => afterDrawing(current, element));
    } else if (result === 'FULL') {
      toast.error('La pizarra está llena. Borra algo para seguir.');
    } else {
      toast.error('Eso es demasiado grande para la pizarra.');
    }
  }

  function erase(id: string | null) {
    if (!id) return;
    board.remove(id);
    setHistory((current) => withoutElement(current, id));
  }

  function down(event: ReactPointerEvent<SVGSVGElement>) {
    if (!canDraw || !svg.current || event.button !== 0) return;
    event.preventDefault();
    if (tool === 'eraser') {
      event.currentTarget.setPointerCapture?.(event.pointerId);
      erase(elementAt(event));
      setDraft({ kind: 'stroke', points: [] });
      return;
    }
    event.currentTarget.setPointerCapture?.(event.pointerId);
    const point = boardPoint(svg.current, event);
    setDraft(
      tool === 'pen' ? { kind: 'stroke', points: withPoint([], point) } : { kind: 'shape', from: point, to: point },
    );
  }

  function move(event: ReactPointerEvent<SVGSVGElement>) {
    if (!draft || !svg.current) return;
    if (tool === 'eraser') {
      erase(elementAt(event));
      return;
    }
    const point = boardPoint(svg.current, event);
    setDraft((current) => {
      if (current?.kind === 'stroke') return { kind: 'stroke', points: withPoint(current.points, point) };
      if (current?.kind === 'shape') return { ...current, to: point };
      return current;
    });
  }

  function up() {
    const finished = draft;
    setDraft(null);
    if (!finished || tool === 'eraser') return;
    if (finished.kind === 'stroke') {
      if (finished.points.length > 0) commit(strokeElement(finished.points, color));
    } else if (tool === 'rect' || tool === 'ellipse') {
      const shape = shapeElement(tool, finished.from, finished.to, color);
      if (shape) commit(shape);
    }
  }

  function saveNote() {
    if (!noteText.trim()) return;
    commit(noteElement(noteText, firstName(me.data?.fullName), notes));
    setNotes((current) => current + 1);
    setNoteText('');
    setNoteOpen(false);
  }

  function undo() {
    const step = afterUndo(history);
    if (!step) return;
    board.remove(step.element.id ?? '');
    setHistory(step.history);
  }

  function redo() {
    const step = afterRedo(history);
    if (!step) return;
    if (board.put(step.element) === 'OK') setHistory(step.history);
  }

  function clearAll() {
    board.clear();
    setHistory(NO_HISTORY);
    setClearing(false);
  }

  const draftDrawing =
    draft?.kind === 'stroke' && draft.points.length > 0
      ? drawingOf(strokeElement(draft.points, color, 'draft'))
      : draft?.kind === 'shape' && (tool === 'rect' || tool === 'ellipse')
        ? drawingOf(shapeElement(tool, draft.from, draft.to, color, 'draft') ?? { id: 'none' })
        : null;

  return (
    <div className="relative flex h-full min-h-[360px] flex-col overflow-hidden rounded-[20px] border border-line bg-white lg:min-h-0">
      <p
        role="status"
        className={cx(
          'absolute left-1/2 top-3 z-10 flex max-w-[90%] -translate-x-1/2 items-center gap-2 whitespace-nowrap rounded-full px-3 py-1.5 text-xs font-medium shadow-sm',
          status.tone === 'loud' ? 'bg-ink text-white' : 'border border-line bg-white text-ink-2',
        )}
      >
        {status.tone === 'loud' && board.status !== 'OFFLINE' && <Spinner size="sm" decorative />}
        {status.text}
      </p>

      <svg
        ref={svg}
        role="img"
        aria-label={boardSummary(drawings)}
        viewBox={`0 0 ${BOARD_WIDTH} ${BOARD_HEIGHT}`}
        onPointerDown={down}
        onPointerMove={move}
        onPointerUp={up}
        onPointerCancel={up}
        style={{ touchAction: canDraw ? 'none' : 'auto' }}
        className={cx('min-h-0 w-full flex-1', canDraw && tool !== 'eraser' && 'cursor-crosshair')}
      >
        <defs>
          <pattern id={`grid-${sessionId}`} width="40" height="40" patternUnits="userSpaceOnUse">
            <path d="M 40 0 L 0 0 0 40" fill="none" stroke="#E6E7EE" strokeWidth="1" />
          </pattern>
        </defs>
        {/* Wider than the board: the grid also fills the sides a window of another shape leaves. */}
        <rect
          x={-2000}
          y={-2000}
          width={BOARD_WIDTH + 4000}
          height={BOARD_HEIGHT + 4000}
          fill={`url(#grid-${sessionId})`}
        />
        {drawings.map((drawing) => (
          <g key={drawing.id} data-element={drawing.id} className={tool === 'eraser' ? 'cursor-pointer' : undefined}>
            {tool === 'eraser' && drawing.kind === 'stroke' && (
              <BoardDrawing drawing={{ ...drawing, color: '#00000000', width: 18 }} />
            )}
            <BoardDrawing drawing={drawing} />
          </g>
        ))}
        {draftDrawing && <BoardDrawing drawing={draftDrawing} />}
      </svg>

      {!readOnly && (
        <div
          role="toolbar"
          aria-label="Herramientas de la pizarra"
          className="flex items-center gap-1 overflow-x-auto border-t border-line bg-white p-2 lg:absolute lg:left-4 lg:top-1/2 lg:-translate-y-1/2 lg:flex-col lg:overflow-visible lg:rounded-2xl lg:border lg:shadow-md"
        >
          <ToolButton label="Lápiz" icon="pen" active={tool === 'pen'} onClick={() => setTool('pen')} />
          <ToolButton label="Rectángulo" icon="rect" active={tool === 'rect'} onClick={() => setTool('rect')} />
          <ToolButton label="Círculo" icon="ellipse" active={tool === 'ellipse'} onClick={() => setTool('ellipse')} />
          <ToolButton label="Nota" icon="note" disabled={!canDraw} onClick={() => setNoteOpen(true)} />
          <ToolButton label="Borrador" icon="eraser" active={tool === 'eraser'} onClick={() => setTool('eraser')} />
          <span aria-hidden="true" className="mx-1 h-6 w-px shrink-0 bg-line lg:mx-0 lg:my-1 lg:h-px lg:w-6" />
          {COLORS.map((option) => (
            <button
              key={option.value}
              type="button"
              aria-label={option.label}
              title={option.label}
              aria-pressed={color === option.value}
              onClick={() => setColor(option.value)}
              className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
            >
              <span
                className={cx('block h-5 w-5 rounded-full', color === option.value && 'ring-2 ring-ink ring-offset-2')}
                style={{ backgroundColor: option.value }}
              />
            </button>
          ))}
          <span aria-hidden="true" className="mx-1 h-6 w-px shrink-0 bg-line lg:mx-0 lg:my-1 lg:h-px lg:w-6" />
          <ToolButton label="Deshacer" icon="undo" disabled={history.done.length === 0} onClick={undo} />
          <ToolButton label="Rehacer" icon="redo" disabled={history.undone.length === 0} onClick={redo} />
          <ToolButton
            label="Limpiar la pizarra"
            icon="clear"
            disabled={!canDraw || drawings.length === 0}
            onClick={() => setClearing(true)}
          />
        </div>
      )}

      <Dialog
        open={noteOpen}
        onClose={() => setNoteOpen(false)}
        title="Agregar una nota"
        footer={
          <>
            <Button onClick={saveNote} disabled={!noteText.trim()}>
              Agregar
            </Button>
            <Button variant="secondary" onClick={() => setNoteOpen(false)}>
              Cancelar
            </Button>
          </>
        }
      >
        <Field label="La nota">
          <TextArea
            rows={3}
            maxLength={NOTE_LIMIT}
            value={noteText}
            onChange={(event) => setNoteText(event.target.value)}
          />
        </Field>
      </Dialog>

      <Dialog
        open={clearing}
        onClose={() => setClearing(false)}
        title="¿Limpiar la pizarra?"
        description="Se borra todo lo que dibujaron los dos, y no se puede deshacer."
        footer={
          <>
            <Button variant="danger" onClick={clearAll}>
              Limpiar
            </Button>
            <Button variant="secondary" onClick={() => setClearing(false)}>
              Cancelar
            </Button>
          </>
        }
      />
    </div>
  );
}
