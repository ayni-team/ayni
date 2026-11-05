import { describe, expect, it } from 'vitest';
import { drawingOf, drawingsOf, fittedViewBox, fittingPoints, linesOf, pathOf, type Point } from './board';

describe('what the elements of a board hold', () => {
  it('reads a stroke, a text and a note as the demo history and the live board write them', () => {
    expect(
      drawingOf({
        id: 's',
        type: 'stroke',
        data: {
          color: '#E42322',
          width: 3,
          points: [
            [40, 70],
            [260, 70],
          ],
        } as never,
      }),
    ).toEqual({
      kind: 'stroke',
      id: 's',
      color: '#E42322',
      width: 3,
      points: [
        [40, 70],
        [260, 70],
      ],
    });
    expect(
      drawingOf({ id: 't', type: 'text', data: { x: 40, y: 100, text: '1. Uno\n2. Dos', color: '#1F2937' } as never }),
    ).toEqual({ kind: 'text', id: 't', x: 40, y: 100, color: '#1F2937', lines: ['1. Uno', '2. Dos'] });
    expect(
      drawingOf({ id: 'n', type: 'note', data: { x: 1, y: 2, text: '¿Por qué?', author: 'Ana' } as never }),
    ).toEqual({
      kind: 'note',
      id: 'n',
      x: 1,
      y: 2,
      lines: ['¿Por qué?'],
      author: 'Ana',
    });
  });

  it('leaves out what it cannot read instead of breaking', () => {
    expect(drawingOf({ id: 'x', type: 'hologram', data: {} })).toBeNull();
    expect(drawingOf({ id: 'x', type: 'stroke', data: { points: 'no' } as never })).toBeNull();
    expect(drawingOf({ id: 'x', type: 'text', data: { x: 'a', y: 1, text: 'no' } as never })).toBeNull();
    expect(drawingsOf(undefined)).toEqual([]);
  });

  it('never lets a color through that is not a color', () => {
    const drawing = drawingOf({ id: 's', type: 'stroke', data: { color: 'url(http://x)', points: [[0, 0]] } as never });
    expect(drawing).toMatchObject({ color: '#1D2033' });
  });

  it('wraps a note at its width and a text only at its line ends', () => {
    expect(linesOf('uno dos tres', 7)).toEqual(['uno dos', 'tres']);
    expect(linesOf('uno dos tres')).toEqual(['uno dos tres']);
  });

  it('shows the part of the board that was used, and the whole when empty', () => {
    expect(fittedViewBox([])).toBe('0 0 1200 800');
    const [left, top, width, height] = fittedViewBox([
      {
        kind: 'stroke',
        id: 's',
        color: '#000',
        width: 3,
        points: [
          [100, 100],
          [900, 500],
        ],
      },
    ])
      .split(' ')
      .map(Number);
    expect([left, top]).toEqual([76, 76]);
    expect(width).toBe(848);
    expect(height).toBe(448);
  });

  it('draws a single point as a dot', () => {
    expect(pathOf([[1, 2]])).toBe('M 1 2 l 0.01 0');
    expect(
      pathOf([
        [1, 2],
        [3, 4],
      ]),
    ).toBe('M 1 2 L 3 4');
  });

  it('thins a stroke until it fits, keeping its ends', () => {
    const points: Point[] = Array.from({ length: 101 }, (_, index) => [index, index]);
    const fitted = fittingPoints(points, (candidate) => candidate.length <= 20);
    expect(fitted.length).toBeLessThanOrEqual(20);
    expect(fitted[0]).toEqual([0, 0]);
    expect(fitted[fitted.length - 1]).toEqual([100, 100]);
    expect(fittingPoints(points, () => true)).toHaveLength(101);
  });
});
