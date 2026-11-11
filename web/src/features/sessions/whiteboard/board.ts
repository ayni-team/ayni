import type { Schema } from '@/shared/api';

/**
 * An element of a whiteboard as the server stores it: `{ id, type, data }`, with `data` opaque to
 * the server (ADR 0009). Since ADR 0014 every element is an Excalidraw element (`scene.ts`).
 *
 * Boards drawn before that hold the types below, which `legacy.ts` reads to show them again:
 *
 * - `stroke`: `{ points: [[x, y], ...], color, width }`
 * - `text`: `{ x, y, text, color }`, on one or several lines, `y` at the foot of the first one
 * - `note`: `{ x, y, text, author }`, a yellow note signed by whoever wrote it
 * - `shape`: `{ shape: 'rect' | 'ellipse', x, y, width, height, color }`
 *
 * Their coordinates are those of a board of 1200 × 800, which on an Excalidraw canvas are simply
 * coordinates.
 */
export type BoardElement = Schema<'BoardElement'>;

export type Point = readonly [number, number];

export type Drawing =
  | { kind: 'stroke'; id: string; points: Point[]; color: string; width: number }
  | { kind: 'text'; id: string; x: number; y: number; lines: string[]; color: string }
  | { kind: 'note'; id: string; x: number; y: number; lines: string[]; author: string | null }
  | {
      kind: 'shape';
      id: string;
      shape: 'rect' | 'ellipse';
      x: number;
      y: number;
      width: number;
      height: number;
      color: string;
    };

export const INK = '#1D2033';

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
 * Reads an element of the old format. The data comes from another client and may be anything:
 * what cannot be read is left out, never a crash.
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
  if (element.type === 'shape') {
    if (!isNumber(data.x) || !isNumber(data.y) || !isNumber(data.width) || !isNumber(data.height)) return null;
    return {
      kind: 'shape',
      id,
      shape: data.shape === 'ellipse' ? 'ellipse' : 'rect',
      x: data.x,
      y: data.y,
      width: Math.abs(data.width),
      height: Math.abs(data.height),
      color: colorOf(data.color),
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

export const NOTE_WIDTH = 300;
export const NOTE_PADDING = 18;
export const TEXT_LINE_HEIGHT = 30;
export const NOTE_LINE_HEIGHT = 26;

export function noteHeight(lines: number, signed: boolean): number {
  return NOTE_PADDING * 2 + lines * NOTE_LINE_HEIGHT + (signed ? 26 : 0);
}
