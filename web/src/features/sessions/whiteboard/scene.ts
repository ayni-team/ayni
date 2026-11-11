import type { ExcalidrawElement } from '@excalidraw/excalidraw/element/types';
import type { BoardElement } from './board';
import { fromLegacy, isLegacy } from './legacy';

/**
 * How an Excalidraw scene and the elements of a board stored by the server (ADR 0009, ADR 0014)
 * map to each other. Every Excalidraw element is one element of the board: `id` is its id, `type`
 * its type and `data` the element itself, as JSON. The server never reads `data`.
 *
 * Only types imports from the package: this file is pure, and the tests run it without Excalidraw.
 */
export type SceneElement = ExcalidrawElement;

/** What the server takes as an id: 1 to 64 letters, digits, `-` or `_`. */
const ID = /^[A-Za-z0-9_-]{1,64}$/;
const TYPE_LIMIT = 32;

/**
 * The types the board keeps. Images, embedded pages and iframes are left out on purpose: the board
 * does not carry files and does not load other sites (ADR 0014).
 */
const KEPT_TYPES: ReadonlySet<string> = new Set([
  'rectangle',
  'diamond',
  'ellipse',
  'arrow',
  'line',
  'freedraw',
  'text',
  'frame',
]);

export function isValidId(id: string): boolean {
  return ID.test(id);
}

export function isKept(element: { type: string }): boolean {
  return KEPT_TYPES.has(element.type) && element.type.length <= TYPE_LIMIT;
}

/** Two decimals are a hundredth of a pixel: nobody sees it, and a long stroke weighs half. */
function rounded(value: number): number {
  return Math.round(value * 100) / 100;
}

function compactPoints(points: readonly (readonly number[])[]): number[][] {
  return points.map((point) => point.map(rounded));
}

/** An Excalidraw element as an element of the board. */
export function toBoardElement(element: SceneElement): BoardElement {
  const data: Record<string, unknown> = { ...element };
  if ('points' in element && Array.isArray(element.points)) {
    data.points = compactPoints(element.points as readonly (readonly number[])[]);
  }
  if ('pressures' in element && Array.isArray(element.pressures)) {
    data.pressures = (element.pressures as readonly number[]).map(rounded);
  }
  return { id: element.id, type: element.type, data: data as BoardElement['data'] };
}

/** Whether an element of the board was drawn with Excalidraw: its data is a whole element. */
export function isSceneData(element: BoardElement): boolean {
  const data = element.data as Record<string, unknown> | undefined;
  return (
    typeof data === 'object' &&
    data !== null &&
    data.type === element.type &&
    typeof data.version === 'number' &&
    typeof data.versionNonce === 'number' &&
    typeof data.x === 'number' &&
    typeof data.y === 'number'
  );
}

/**
 * The scene a board holds: what Excalidraw drew as it was, the elements of the board before
 * Excalidraw converted (see `legacy.ts`), and what cannot be read left out, never a crash. The
 * result still goes through Excalidraw's `restoreElements` before it is drawn, which fills in
 * whatever a field of a later version or an older one lacks.
 */
export function sceneOf(elements: readonly BoardElement[] | undefined): SceneElement[] {
  let legacy = 0;
  return (elements ?? []).flatMap((element) => {
    if (!element.id || !element.type) return [];
    if (isSceneData(element)) {
      if (!isKept(element as { type: string })) return [];
      const data = element.data as Record<string, unknown>;
      // The id and the type are the ones the server has: the data cannot say otherwise.
      return [{ ...data, id: element.id, type: element.type, isDeleted: false } as unknown as SceneElement];
    }
    if (!isLegacy(element)) return [];
    // An old board has no order of its own but the one it was drawn in.
    return fromLegacy(element).map((part) => ({ ...part, index: indexAt(legacy++) }) as SceneElement);
  });
}

const DIGITS = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz';

/**
 * The fractional index of the position `n`, as `fractional-indexing` writes integers: `a0` to `az`,
 * then `b00` to `bzz`. Enough for every element a board may hold.
 */
export function indexAt(n: number): SceneElement['index'] {
  if (n < 62) return `a${DIGITS[n]}` as SceneElement['index'];
  const rest = Math.min(n - 62, 62 * 62 - 1);
  return `b${DIGITS[Math.floor(rest / 62)]}${DIGITS[rest % 62]}` as SceneElement['index'];
}

/**
 * The order to draw in: Excalidraw's fractional index, the same on both sides, and the id when two
 * indices are equal because both participants added something at the same time. Plain string
 * comparison is what fractional indices are made for.
 */
export function byIndex(a: SceneElement, b: SceneElement): number {
  const left = a.index ?? '￿';
  const right = b.index ?? '￿';
  if (left !== right) return left < right ? -1 : 1;
  if (a.id === b.id) return 0;
  return a.id < b.id ? -1 : 1;
}

/**
 * The same scene with every id the server would refuse replaced, and every reference to it too.
 * Excalidraw's ids already fit; this is for what a newer or another client may make. Answers null
 * when nothing had to change.
 */
export function withValidIds(scene: readonly SceneElement[], newId: () => string): SceneElement[] | null {
  const renamed = new Map<string, string>();
  for (const element of scene) {
    if (!isValidId(element.id)) renamed.set(element.id, newId());
  }
  if (renamed.size === 0) return null;
  const rename = (id: string | null | undefined) => (id && renamed.get(id)) ?? id;
  return scene.map((element) => {
    const next: Record<string, unknown> = { ...element, id: rename(element.id) };
    if (element.boundElements) {
      next.boundElements = element.boundElements.map((bound) => ({ ...bound, id: rename(bound.id) }));
    }
    if (element.frameId) next.frameId = rename(element.frameId);
    if ('containerId' in element && element.containerId) next.containerId = rename(element.containerId);
    for (const end of ['startBinding', 'endBinding'] as const) {
      const binding = (element as Record<string, unknown>)[end] as { elementId: string } | null | undefined;
      if (binding) next[end] = { ...binding, elementId: rename(binding.elementId) };
    }
    return next as unknown as SceneElement;
  });
}

/** Words a screen reader can read instead of the drawing: how much there is, and what it says. */
export function boardSummary(scene: readonly SceneElement[]): string {
  const alive = scene.filter((element) => !element.isDeleted);
  const words = alive.flatMap((element) =>
    element.type === 'text' && 'text' in element ? [String(element.text).replace(/\n/g, ' ')] : [],
  );
  const drawn = alive.length - words.length;
  return [`Pizarra con ${drawn === 1 ? '1 trazo' : `${drawn} trazos`}`, ...words].join('. ');
}
