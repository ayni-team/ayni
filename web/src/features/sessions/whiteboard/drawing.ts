import { fittingPoints, type BoardElement, type Point } from './board';
import { MAX_ELEMENT_BYTES, elementBytes } from './boardState';

/** The colours of the frame: the same at every university, so never the brand's. */
export const COLORS = [
  { value: '#1D2033', label: 'Negro' },
  { value: '#00A3DA', label: 'Azul' },
  { value: '#E6007E', label: 'Fucsia' },
  { value: '#FFC800', label: 'Amarillo' },
] as const;

export type Tool = 'pen' | 'rect' | 'ellipse' | 'eraser';

function newElementId(type: string): string {
  return `${type}-${crypto.randomUUID()}`;
}

/** The next point of a stroke, if it moved enough to matter: it keeps a stroke light. */
export function withPoint(points: readonly Point[], point: Point): readonly Point[] {
  const last = points[points.length - 1];
  if (last && Math.abs(last[0] - point[0]) < 2 && Math.abs(last[1] - point[1]) < 2) return points;
  return [...points, [Math.round(point[0]), Math.round(point[1])]];
}

const STROKE_WIDTH = 3;

/** A finished stroke, thinned if it would not fit in what the server takes for one element. */
export function strokeElement(points: readonly Point[], color: string, id = newElementId('stroke')): BoardElement {
  const build = (candidate: readonly Point[]): BoardElement => ({
    id,
    type: 'stroke',
    data: { points: candidate, color, width: STROKE_WIDTH } as never,
  });
  return build(fittingPoints(points, (candidate) => elementBytes(build(candidate)) <= MAX_ELEMENT_BYTES));
}

/** A rectangle or an ellipse between two corners, in any direction. Nothing if it has no size. */
export function shapeElement(
  shape: 'rect' | 'ellipse',
  from: Point,
  to: Point,
  color: string,
  id = newElementId('shape'),
): BoardElement | null {
  const width = Math.abs(to[0] - from[0]);
  const height = Math.abs(to[1] - from[1]);
  if (width < 4 && height < 4) return null;
  return {
    id,
    type: 'shape',
    data: {
      shape,
      x: Math.round(Math.min(from[0], to[0])),
      y: Math.round(Math.min(from[1], to[1])),
      width: Math.round(width),
      height: Math.round(height),
      color,
    } as never,
  };
}

/** Clear of the tools, which float over the left of the board on a desktop. */
const NOTE_LEFT = 160;
const NOTE_TOP = 80;
const NOTE_STEP = 48;
const NOTE_SPOTS = 8;

/** A note, signed. Each goes a little further along, so that two in a row do not cover each other. */
export function noteElement(
  text: string,
  author: string | null,
  notesSoFar: number,
  id = newElementId('note'),
): BoardElement {
  const spot = notesSoFar % NOTE_SPOTS;
  return {
    id,
    type: 'note',
    data: {
      text: text.trim(),
      author,
      x: NOTE_LEFT + spot * NOTE_STEP,
      y: NOTE_TOP + spot * NOTE_STEP,
    } as never,
  };
}

/** "Ana": the note is signed with the first name, as the frame draws it. */
export function firstName(fullName: string | undefined): string | null {
  return fullName?.trim().split(/\s+/)[0] || null;
}

/** What was drawn here and can be taken back; only one's own, never what the other person drew. */
export interface History {
  done: readonly BoardElement[];
  undone: readonly BoardElement[];
}

export const NO_HISTORY: History = { done: [], undone: [] };

export function afterDrawing(history: History, element: BoardElement): History {
  return { done: [...history.done, element], undone: [] };
}

export function afterUndo(history: History): { history: History; element: BoardElement } | null {
  const element = history.done[history.done.length - 1];
  if (!element) return null;
  return { element, history: { done: history.done.slice(0, -1), undone: [...history.undone, element] } };
}

export function afterRedo(history: History): { history: History; element: BoardElement } | null {
  const element = history.undone[history.undone.length - 1];
  if (!element) return null;
  return { element, history: { done: [...history.done, element], undone: history.undone.slice(0, -1) } };
}

/** Forgets an element nobody can bring back or take back any more: it was erased, or the board cleared. */
export function withoutElement(history: History, id: string): History {
  return {
    done: history.done.filter((element) => element.id !== id),
    undone: history.undone.filter((element) => element.id !== id),
  };
}
