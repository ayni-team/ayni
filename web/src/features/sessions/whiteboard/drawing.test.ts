import { describe, expect, it } from 'vitest';
import type { Point } from './board';
import { elementBytes, MAX_ELEMENT_BYTES } from './boardState';
import {
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
} from './drawing';

describe('FE-22 what is drawn', () => {
  it('skips a point that barely moved, and rounds the rest', () => {
    const first = withPoint([], [10.4, 10.6]);
    expect(first).toEqual([[10, 11]]);
    expect(withPoint(first, [11, 11])).toBe(first);
    expect(withPoint(first, [20, 20])).toEqual([
      [10, 11],
      [20, 20],
    ]);
  });

  it('thins a stroke that would not fit in what the server takes', () => {
    const points: Point[] = Array.from({ length: 6000 }, (_, index) => [index, index]);
    const stroke = strokeElement(points, '#000');
    expect(elementBytes(stroke)).toBeLessThanOrEqual(MAX_ELEMENT_BYTES);
    const kept = (stroke.data as unknown as { points: Point[] }).points;
    expect(kept[0]).toEqual([0, 0]);
    expect(kept[kept.length - 1]).toEqual([5999, 5999]);
  });

  it('draws a shape between two corners in any direction, and nothing without a size', () => {
    expect(shapeElement('rect', [100, 80], [40, 200], '#000', 's')?.data).toEqual({
      shape: 'rect',
      x: 40,
      y: 80,
      width: 60,
      height: 120,
      color: '#000',
    });
    expect(shapeElement('ellipse', [10, 10], [11, 11], '#000')).toBeNull();
  });

  it('signs a note with the first name, and does not pile them up', () => {
    expect(firstName('Ana Torres')).toBe('Ana');
    expect(firstName(undefined)).toBeNull();
    const data = (index: number) =>
      noteElement(' ¿Por qué? ', 'Ana', index, 'n').data as unknown as Record<string, unknown>;
    expect(data(0)).toMatchObject({ text: '¿Por qué?', author: 'Ana' });
    expect(data(1).x).not.toBe(data(0).x);
  });

  it('takes back only what was drawn here, and brings it back', () => {
    const one = { id: 'one' };
    const two = { id: 'two' };
    const history = afterDrawing(afterDrawing(NO_HISTORY, one), two);

    const undone = afterUndo(history);
    expect(undone?.element).toBe(two);
    const redone = afterRedo(undone!.history);
    expect(redone?.element).toBe(two);
    expect(redone?.history.done).toEqual([one, two]);

    expect(afterUndo(NO_HISTORY)).toBeNull();
    expect(afterRedo(NO_HISTORY)).toBeNull();
  });

  it('drawing again drops what could be redone; erasing forgets it', () => {
    const undone = afterUndo(afterDrawing(NO_HISTORY, { id: 'one' }))!.history;
    expect(afterDrawing(undone, { id: 'two' }).undone).toEqual([]);
    expect(withoutElement(afterDrawing(NO_HISTORY, { id: 'one' }), 'one').done).toEqual([]);
  });
});
