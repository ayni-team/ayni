import { describe, expect, it } from 'vitest';
import { fromLegacy } from './legacy';
import {
  boardSummary,
  byIndex,
  indexAt,
  isSceneData,
  sceneOf,
  toBoardElement,
  withValidIds,
  type SceneElement,
} from './scene';

/** An Excalidraw element with what the mapping reads; the rest does not matter here. */
function scene(id: string, extra: Record<string, unknown> = {}): SceneElement {
  return {
    id,
    type: 'rectangle',
    x: 0,
    y: 0,
    width: 10,
    height: 10,
    version: 1,
    versionNonce: 1,
    isDeleted: false,
    index: 'a0',
    boundElements: null,
    frameId: null,
    ...extra,
  } as unknown as SceneElement;
}

describe('an Excalidraw scene as the elements of a board, and back', () => {
  it('sends each element as itself: its id, its type and the element as data', () => {
    const element = scene('Ab_3-x', { type: 'ellipse', strokeColor: '#e03131' });

    expect(toBoardElement(element)).toEqual({ id: 'Ab_3-x', type: 'ellipse', data: element });
  });

  it('keeps two decimals of the points of a stroke, which halves a long one', () => {
    const stroke = scene('s', {
      type: 'freedraw',
      points: [
        [0, 0],
        [12.345678901, -3.98765432],
      ],
      pressures: [0.512345, 0.5],
    });

    expect(toBoardElement(stroke).data).toMatchObject({
      points: [
        [0, 0],
        [12.35, -3.99],
      ],
      pressures: [0.51, 0.5],
    });
  });

  it('reads back what it sent, with the id and the type the server has', () => {
    const element = scene('r1', { strokeColor: '#1971c2' });
    const sent = toBoardElement(element);

    expect(isSceneData(sent)).toBe(true);
    expect(sceneOf([sent])).toEqual([element]);
    expect(sceneOf([{ ...sent, id: 'other' }])[0].id).toBe('other');
  });

  it('leaves out images, embedded pages and whatever cannot be read, never a crash', () => {
    const image = toBoardElement(scene('i', { type: 'image', fileId: 'f' }));
    const embed = toBoardElement(scene('e', { type: 'embeddable', link: 'https://example.com' }));

    expect(
      sceneOf([
        image,
        embed,
        { id: 'w', type: 'weird', data: {} },
        { id: '', type: 'rectangle', data: {} },
        { id: 'n', type: 'text', data: null as never },
      ]),
    ).toEqual([]);
  });

  it('orders by fractional index, and two equal indices by id, the same on both sides', () => {
    const elements = [scene('b', { index: 'a1' }), scene('z', { index: 'a0' }), scene('a', { index: 'a1' })];

    expect([...elements].sort(byIndex).map((element) => element.id)).toEqual(['z', 'a', 'b']);
    expect(indexAt(0)).toBe('a0');
    expect(indexAt(61)).toBe('az');
    expect(indexAt(62)).toBe('b00');
    expect(indexAt(1999) < indexAt(2000)).toBe(true);
  });

  it('replaces an id the server would refuse, and every reference to it', () => {
    const box = scene('bad id!', { boundElements: [{ id: 'label', type: 'text' }] });
    const label = scene('label', { type: 'text', containerId: 'bad id!' });
    const arrow = scene('arrow', {
      type: 'arrow',
      startBinding: { elementId: 'bad id!', focus: 0, gap: 0 },
      endBinding: null,
    });

    const fixed = withValidIds([box, label, arrow], () => 'fresh');

    expect(fixed?.map((element) => element.id)).toEqual(['fresh', 'label', 'arrow']);
    expect(fixed?.[1]).toMatchObject({ containerId: 'fresh' });
    expect(fixed?.[2]).toMatchObject({ startBinding: { elementId: 'fresh' } });
    expect(withValidIds([label], () => 'x')).toBeNull();
    expect(withValidIds([scene('a'.repeat(65))], () => 'ok')?.[0].id).toBe('ok');
  });

  it('describes the board in words for a screen reader', () => {
    expect(boardSummary([scene('a'), scene('t', { type: 'text', text: 'Hola\nmundo' })])).toBe(
      'Pizarra con 1 trazo. Hola mundo',
    );
    expect(boardSummary([scene('a'), scene('b'), scene('c', { isDeleted: true })])).toBe('Pizarra con 2 trazos');
  });
});

describe('a board drawn before Excalidraw', () => {
  it('reads the four old types as Excalidraw elements, in the order they were drawn', () => {
    const elements = sceneOf([
      { id: 'title', type: 'text', data: { text: 'Resumen', x: 40, y: 40, color: '#1F2937' } },
      {
        id: 'underline',
        type: 'stroke',
        data: {
          points: [
            [40, 70],
            [260, 80],
          ],
          color: '#E42322',
          width: 3,
        },
      },
      { id: 'box', type: 'shape', data: { shape: 'ellipse', x: 5, y: 6, width: -50, height: 40, color: '#2563EB' } },
      { id: 'n', type: 'note', data: { x: 300, y: 100, text: 'Revisar la 3FN', author: 'Ana' } },
    ]);

    expect(elements.map((element) => [element.id, element.type, element.index])).toEqual([
      ['title', 'text', 'a0'],
      ['underline', 'freedraw', 'a1'],
      ['box', 'ellipse', 'a2'],
      ['n', 'rectangle', 'a3'],
      ['n-text', 'text', 'a4'],
      ['n-author', 'text', 'a5'],
    ]);
    // The old text wrote `y` at the foot of its first line.
    expect(elements[0]).toMatchObject({ x: 40, y: 18, text: 'Resumen', strokeColor: '#1F2937', fontFamily: 6 });
    // A stroke keeps its place, its points relative to its corner.
    expect(elements[1]).toMatchObject({
      x: 40,
      y: 70,
      width: 220,
      height: 10,
      strokeColor: '#E42322',
      points: [
        [0, 0],
        [220, 10],
      ],
    });
    expect(elements[2]).toMatchObject({ width: 50, height: 40, strokeColor: '#2563EB' });
    // A note is a yellow card, its text and its signature, grouped so they move together.
    expect(elements[3]).toMatchObject({ backgroundColor: '#FFF6D6', groupIds: ['n-note'] });
    expect(elements[4]).toMatchObject({ text: 'Revisar la 3FN', groupIds: ['n-note'] });
    expect(elements[5]).toMatchObject({ text: '— Ana', groupIds: ['n-note'] });
  });

  it('is never taken for an Excalidraw element, even a text', () => {
    expect(isSceneData({ id: 't', type: 'text', data: { text: 'Hola', x: 1, y: 2 } })).toBe(false);
  });

  it('leaves out an old element that cannot be read', () => {
    expect(fromLegacy({ id: 's', type: 'stroke', data: { points: 'none' } as never })).toEqual([]);
    expect(fromLegacy({ id: 't', type: 'text', data: { x: 1 } as never })).toEqual([]);
  });

  it('gives the parts of a note ids the server takes, for a long id too', () => {
    const parts = fromLegacy({ id: 'x'.repeat(64), type: 'note', data: { x: 0, y: 0, text: 'a', author: 'B' } });

    expect(parts.every((part) => /^[A-Za-z0-9_-]{1,64}$/.test(part.id))).toBe(true);
    expect(new Set(parts.map((part) => part.id)).size).toBe(3);
  });
});
