import {
  INK,
  NOTE_LINE_HEIGHT,
  NOTE_PADDING,
  NOTE_WIDTH,
  TEXT_LINE_HEIGHT,
  drawingOf,
  noteHeight,
  type BoardElement,
  type Drawing,
  type Point,
} from './board';
import type { SceneElement } from './scene';

/**
 * Boards drawn before Excalidraw (ADR 0014) are converted when they are read, never rewritten: the
 * server keeps what it has, and a board of the old format reads the same in the room and in the
 * review of a session. The result is a minimal Excalidraw element; Excalidraw's `restoreElements`
 * fills in the rest before it is drawn.
 */
const LEGACY_TYPES: ReadonlySet<string> = new Set(['stroke', 'text', 'note', 'shape']);

const NOTE_FILL = '#FFF6D6';
const NOTE_AUTHOR = '#7A5E00';
/** Nunito, the "normal" font of Excalidraw, the closest to the one the old board wrote in. */
const NUNITO = 6;
const TEXT_SIZE = 22;
const AUTHOR_SIZE = 16;

export function isLegacy(element: BoardElement): boolean {
  return LEGACY_TYPES.has(element.type ?? '');
}

/** A number from the id, so the same element is drawn the same on both sides. */
function seedOf(id: string): number {
  let hash = 7;
  for (const char of id) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return (hash % 2_000_000_000) + 1;
}

function base(id: string, type: string, x: number, y: number, width: number, height: number) {
  return {
    id,
    type,
    x,
    y,
    width,
    height,
    angle: 0,
    strokeColor: INK,
    backgroundColor: 'transparent',
    fillStyle: 'solid',
    strokeWidth: 2,
    strokeStyle: 'solid',
    roughness: 0,
    opacity: 100,
    roundness: null,
    seed: seedOf(id),
    version: 1,
    versionNonce: 0,
    index: null,
    isDeleted: false,
    groupIds: [] as string[],
    frameId: null,
    boundElements: null,
    updated: 0,
    link: null,
    locked: false,
  };
}

/** About half the body of the font per character: the width only matters until Excalidraw measures it. */
function textElement(
  id: string,
  x: number,
  top: number,
  lines: string[],
  color: string,
  fontSize: number,
  lineHeight: number,
) {
  const text = lines.join('\n');
  const longest = Math.max(1, ...lines.map((line) => line.length));
  return {
    ...base(id, 'text', x, top, longest * fontSize * 0.55, lines.length * lineHeight),
    strokeColor: color,
    fontSize,
    fontFamily: NUNITO,
    text,
    originalText: text,
    textAlign: 'left',
    verticalAlign: 'top',
    containerId: null,
    autoResize: true,
    lineHeight: lineHeight / fontSize,
  };
}

/** The id of a part an old element became, within the 64 characters of the server. */
function partId(id: string, part: string): string {
  return `${id.slice(0, 63 - part.length)}-${part}`;
}

function converted(drawing: Drawing): Record<string, unknown>[] {
  switch (drawing.kind) {
    case 'stroke': {
      const xs = drawing.points.map(([x]) => x);
      const ys = drawing.points.map(([, y]) => y);
      const left = Math.min(...xs);
      const top = Math.min(...ys);
      const points: Point[] = drawing.points.map(([x, y]) => [x - left, y - top]);
      return [
        {
          ...base(drawing.id, 'freedraw', left, top, Math.max(...xs) - left, Math.max(...ys) - top),
          strokeColor: drawing.color,
          strokeWidth: drawing.width > 3 ? 2 : 1,
          points,
          pressures: [],
          simulatePressure: true,
          lastCommittedPoint: null,
        },
      ];
    }
    case 'shape':
      return [
        {
          ...base(
            drawing.id,
            drawing.shape === 'ellipse' ? 'ellipse' : 'rectangle',
            drawing.x,
            drawing.y,
            drawing.width,
            drawing.height,
          ),
          strokeColor: drawing.color,
        },
      ];
    case 'text':
      // The old board wrote `y` at the foot of the first line; Excalidraw, at the top of the text.
      return [
        textElement(
          drawing.id,
          drawing.x,
          drawing.y - TEXT_SIZE,
          drawing.lines,
          drawing.color,
          TEXT_SIZE,
          TEXT_LINE_HEIGHT,
        ),
      ];
    case 'note': {
      const height = noteHeight(drawing.lines.length, Boolean(drawing.author));
      const group = [partId(drawing.id, 'note')];
      const parts: Record<string, unknown>[] = [
        {
          ...base(drawing.id, 'rectangle', drawing.x, drawing.y, NOTE_WIDTH, height),
          strokeColor: 'transparent',
          backgroundColor: NOTE_FILL,
          roundness: { type: 3 },
          groupIds: group,
        },
        {
          ...textElement(
            partId(drawing.id, 'text'),
            drawing.x + NOTE_PADDING,
            drawing.y + NOTE_PADDING,
            drawing.lines,
            INK,
            TEXT_SIZE,
            NOTE_LINE_HEIGHT,
          ),
          groupIds: group,
        },
      ];
      if (drawing.author) {
        parts.push({
          ...textElement(
            partId(drawing.id, 'author'),
            drawing.x + NOTE_PADDING,
            drawing.y + height - NOTE_PADDING - AUTHOR_SIZE,
            [`— ${drawing.author}`],
            NOTE_AUTHOR,
            AUTHOR_SIZE,
            20,
          ),
          groupIds: group,
        });
      }
      return parts;
    }
  }
}

/** One element of the old format as the Excalidraw elements that draw it; none if it cannot be read. */
export function fromLegacy(element: BoardElement): SceneElement[] {
  const drawing = drawingOf(element);
  return drawing ? (converted(drawing) as unknown as SceneElement[]) : [];
}
