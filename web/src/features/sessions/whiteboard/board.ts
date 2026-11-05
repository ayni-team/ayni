import type { Schema } from '@/shared/api';

/**
 * What the elements of a whiteboard hold. The server keeps `data` opaque (ADR 0009): this file is
 * where the web says what each `type` carries, for the live board and for the review of a session.
 *
 * - `stroke`: `{ points: [[x, y], ...], color, width }`
 * - `text`: `{ x, y, text, color }`, on one or several lines
 * - `note`: `{ x, y, text, author }`, a yellow note signed by whoever wrote it
 *
 * The coordinates are those of a board of `BOARD_WIDTH` × `BOARD_HEIGHT`, whatever the size of the
 * screen, so both participants see the same drawing.
 */
export type BoardElement = Schema<'BoardElement'>;

export const BOARD_WIDTH = 1200;
export const BOARD_HEIGHT = 800;

export type Point = readonly [number, number];

export type Drawing =
  | { kind: 'stroke'; id: string; points: Point[]; color: string; width: number }
  | { kind: 'text'; id: string; x: number; y: number; lines: string[]; color: string }
  | { kind: 'note'; id: string; x: number; y: number; lines: string[]; author: string | null };

const INK = '#1D2033';

function isNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value);
}

function colorOf(value: unknown): string {
  return typeof value === 'string' && /^#[0-9a-fA-F]{3,8}$/.test(value) ? value : INK;
}

function pointsOf(value: unknown): Point[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((point: unknown) =>
    Array.isArray(point) && isNumber(point[0]) && isNumber(point[1]) ? [[point[0], point[1]] as Point] : [],
  );
}

/** How many characters fit in a line of a note, wide as `NOTE_WIDTH`. */
const NOTE_LINE = 30;

/** Breaks a text at its line ends; in a note, also at the width of the note. */
export function linesOf(text: string, width: number | null = null): string[] {
  return text.split('\n').flatMap((line) => {
    if (!width || line.length <= width) return [line];
    const wrapped: string[] = [];
    let current = '';
    for (const word of line.split(' ')) {
      if (current && `${current} ${word}`.length > width) {
        wrapped.push(current);
        current = word;
      } else {
        current = current ? `${current} ${word}` : word;
      }
    }
    if (current) wrapped.push(current);
    return wrapped;
  });
}

/**
 * Reads an element as something to draw. The data comes from another client and may be of a later
 * version of the web: what cannot be read is left out, never a crash.
 */
export function drawingOf(element: BoardElement): Drawing | null {
  const data = (element.data ?? {}) as Record<string, unknown>;
  const id = element.id ?? '';
  if (element.type === 'stroke') {
    const points = pointsOf(data.points);
    if (points.length === 0) return null;
    return {
      kind: 'stroke',
      id,
      points,
      color: colorOf(data.color),
      width: isNumber(data.width) && data.width > 0 ? data.width : 3,
    };
  }
  if (!isNumber(data.x) || !isNumber(data.y) || typeof data.text !== 'string') return null;
  if (element.type === 'text') {
    return { kind: 'text', id, x: data.x, y: data.y, lines: linesOf(data.text), color: colorOf(data.color) };
  }
  if (element.type === 'note') {
    return {
      kind: 'note',
      id,
      x: data.x,
      y: data.y,
      lines: linesOf(data.text, NOTE_LINE),
      author: typeof data.author === 'string' ? data.author : null,
    };
  }
  return null;
}

export function drawingsOf(elements: readonly BoardElement[] | undefined): Drawing[] {
  return (elements ?? []).flatMap((element) => {
    const drawing = drawingOf(element);
    return drawing ? [drawing] : [];
  });
}

export const NOTE_WIDTH = 300;
const LINE_HEIGHT = 30;
const NOTE_PADDING = 18;

export function lineHeightOf(kind: 'text' | 'note'): number {
  return kind === 'note' ? 26 : LINE_HEIGHT;
}

export function noteHeight(lines: number, signed: boolean): number {
  return NOTE_PADDING * 2 + lines * lineHeightOf('note') + (signed ? 26 : 0);
}

function boxOf(drawing: Drawing): { left: number; top: number; right: number; bottom: number } {
  if (drawing.kind === 'stroke') {
    const xs = drawing.points.map(([x]) => x);
    const ys = drawing.points.map(([, y]) => y);
    return { left: Math.min(...xs), top: Math.min(...ys), right: Math.max(...xs), bottom: Math.max(...ys) };
  }
  if (drawing.kind === 'note') {
    return {
      left: drawing.x,
      top: drawing.y,
      right: drawing.x + NOTE_WIDTH,
      bottom: drawing.y + noteHeight(drawing.lines.length, Boolean(drawing.author)),
    };
  }
  const longest = Math.max(...drawing.lines.map((line) => line.length));
  return {
    left: drawing.x,
    top: drawing.y - LINE_HEIGHT,
    // About half a body of the font per character.
    right: drawing.x + longest * 12,
    bottom: drawing.y + (drawing.lines.length - 1) * LINE_HEIGHT + 8,
  };
}

/**
 * The part of the board that was used, for a board that is only read: a few lines in a corner of a
 * big board would otherwise read as nothing. Never smaller than a third of the board.
 */
export function fittedViewBox(drawings: readonly Drawing[]): string {
  if (drawings.length === 0) return `0 0 ${BOARD_WIDTH} ${BOARD_HEIGHT}`;
  const boxes = drawings.map(boxOf);
  const margin = 24;
  const left = Math.max(0, Math.min(...boxes.map((box) => box.left)) - margin);
  const top = Math.max(0, Math.min(...boxes.map((box) => box.top)) - margin);
  const right = Math.max(...boxes.map((box) => box.right)) + margin;
  const bottom = Math.max(...boxes.map((box) => box.bottom)) + margin;
  const width = Math.max(right - left, BOARD_WIDTH / 3);
  const height = Math.max(bottom - top, BOARD_HEIGHT / 3);
  return `${left} ${top} ${width} ${height}`;
}

/** A path through the points; a single point is a dot. */
export function pathOf(points: readonly Point[]): string {
  const [first, ...rest] = points;
  if (rest.length === 0) return `M ${first[0]} ${first[1]} l 0.01 0`;
  return `M ${first[0]} ${first[1]} ${rest.map(([x, y]) => `L ${x} ${y}`).join(' ')}`;
}

/**
 * Thins a stroke until it fits in the limit of the server, keeping its ends. A very long stroke
 * loses detail, not its shape.
 */
export function fittingPoints(points: readonly Point[], fits: (points: readonly Point[]) => boolean): Point[] {
  let current = [...points];
  while (!fits(current) && current.length > 2) {
    const last = current[current.length - 1];
    current = current.filter((_, index) => index % 2 === 0);
    if (current[current.length - 1] !== last) current.push(last);
  }
  return current;
}
