import { describe, expect, it } from 'vitest';
import type { BoardElement } from './board';
import {
  EMPTY_BOARD,
  addPending,
  applyOperation,
  hasGap,
  receiveApplied,
  receiveBoard,
  refuseOldest,
  visibleElements,
  type BoardOperation,
} from './boardState';

const el = (id: string, color = 'a'): BoardElement => ({ id, type: 'stroke', data: { color } as never });
const put = (id: string, element = el(id)): BoardOperation => ({ id: `op-${id}`, action: 'PUT_ELEMENT', element });
const ids = (elements: readonly BoardElement[]) => elements.map((element) => element.id);
const READY = receiveBoard(EMPTY_BOARD, { type: 'board', version: 5, elements: [el('a')] });
const applied = (version: number, operation: BoardOperation) => ({ type: 'applied', version, operation }) as const;

describe('FE-22 the state of the board', () => {
  it('applies each action, and applying it twice leaves the same board', () => {
    const once = applyOperation([el('a')], put('b'));
    expect(ids(once)).toEqual(['a', 'b']);
    expect(applyOperation(once, put('b'))).toEqual(once);

    const removal: BoardOperation = { id: 'r', action: 'REMOVE_ELEMENT', elementId: 'a' };
    expect(ids(applyOperation(applyOperation(once, removal), removal))).toEqual(['b']);

    const clear: BoardOperation = { id: 'c', action: 'CLEAR' };
    expect(applyOperation(applyOperation(once, clear), clear)).toEqual([]);
  });

  it('replaces an element in its place, keeping the order in which it was first drawn', () => {
    const elements = applyOperation([el('a'), el('b')], put('a', el('a', 'new')));
    expect(ids(elements)).toEqual(['a', 'b']);
    expect(elements[0].data).toEqual({ color: 'new' });
  });

  it('applies what arrives in version order, whatever order it arrives in', () => {
    let state = receiveApplied(READY, applied(7, put('c')));
    expect(ids(state.confirmed)).toEqual(['a']);
    expect(hasGap(state)).toBe(true);

    state = receiveApplied(state, applied(6, put('b')));
    expect(ids(state.confirmed)).toEqual(['a', 'b', 'c']);
    expect(state.version).toBe(7);
    expect(hasGap(state)).toBe(false);
  });

  it('for the same element the later version wins, even when it arrives first', () => {
    const early = put('x', el('x', 'first'));
    const late = put('x', el('x', 'second'));
    const state = receiveApplied(receiveApplied(READY, applied(7, late)), applied(6, early));
    expect(state.confirmed.find((element) => element.id === 'x')?.data).toEqual({ color: 'second' });
  });

  it('ignores an operation it has already seen', () => {
    const state = receiveApplied(READY, applied(5, put('z')));
    expect(state).toEqual(READY);
  });

  it('shows its own drawing at once, and the echo confirms it', () => {
    const operation = put('mine');
    let state = addPending(READY, operation);
    expect(ids(visibleElements(state))).toEqual(['a', 'mine']);
    expect(ids(state.confirmed)).toEqual(['a']);

    state = receiveApplied(state, applied(6, operation));
    expect(state.pending).toEqual([]);
    expect(ids(state.confirmed)).toEqual(['a', 'mine']);
    expect(ids(visibleElements(state))).toEqual(['a', 'mine']);
  });

  it('keeps its own drawing on top of what the other one does meanwhile', () => {
    const state = receiveApplied(addPending(READY, put('mine')), applied(6, put('theirs')));
    expect(ids(visibleElements(state))).toEqual(['a', 'theirs', 'mine']);
  });

  it('on rejoining takes the whole board, keeps what the server did not see, and drops what it did', () => {
    const seen = put('seen');
    const lost = put('lost');
    const state = receiveBoard(addPending(addPending(READY, seen), lost), {
      type: 'board',
      version: 9,
      elements: [el('a'), el('other'), el('seen')],
    });
    expect(state.version).toBe(9);
    expect(state.pending).toEqual([lost]);
    expect(ids(visibleElements(state))).toEqual(['a', 'other', 'seen', 'lost']);
  });

  it('holds what arrives before the whole board, and discards what the board already has', () => {
    let state = receiveApplied(EMPTY_BOARD, applied(3, put('old')));
    state = receiveApplied(state, applied(6, put('new')));
    state = receiveBoard(state, { type: 'board', version: 5, elements: [el('old')] });
    expect(ids(state.confirmed)).toEqual(['old', 'new']);
    expect(state.version).toBe(6);
    expect(state.ahead).toEqual([]);
  });

  it('drops the oldest of its own when the server refuses one', () => {
    const state = refuseOldest(addPending(addPending(READY, put('first')), put('second')));
    expect(ids(visibleElements(state))).toEqual(['a', 'second']);
  });
});
