import type { BoardElement } from './board';

/**
 * What travels on the live channel of a whiteboard (ADR 0009). The channel is not in the OpenAPI
 * document, so these types are written here, from `WhiteboardMessages` and `BoardOperation` of the
 * backend; `BoardElement` is the one of the document.
 */
export interface BoardOperation {
  id: string;
  action: 'PUT_ELEMENT' | 'REMOVE_ELEMENT' | 'CLEAR';
  element?: BoardElement;
  elementId?: string;
}

export interface BoardMessage {
  type: 'board';
  version: number;
  elements?: BoardElement[];
}

export interface AppliedMessage {
  type: 'applied';
  version: number;
  by?: string;
  operation: BoardOperation;
}

export interface ErrorMessage {
  type: 'error';
  status: number;
  message?: string;
}

export type ServerMessage = BoardMessage | AppliedMessage | ErrorMessage;

/** The limits of the server: more than this and it refuses the operation. */
export const MAX_ELEMENTS = 2000;
export const MAX_ELEMENT_BYTES = 16 * 1024;

export function elementBytes(element: BoardElement): number {
  return new TextEncoder().encode(JSON.stringify(element)).length;
}

/**
 * The board as this client knows it. The server decides the order: `confirmed` is the board at
 * `version`, exactly as the server has it, and both participants reach the same one because they
 * apply the same operations in the same order. `pending` are this client's operations the server
 * has not sent back, drawn on top so that drawing feels immediate.
 */
export interface BoardState {
  /** The whole board has arrived. Until then there is nothing to draw on. */
  ready: boolean;
  version: number;
  confirmed: readonly BoardElement[];
  pending: readonly BoardOperation[];
  /** Operations that arrived before their turn, waiting for the ones in between. */
  ahead: readonly AppliedMessage[];
}

export const EMPTY_BOARD: BoardState = { ready: false, version: 0, confirmed: [], pending: [], ahead: [] };

/** One operation over a list of elements. Each of them, applied twice, leaves the same board. */
export function applyOperation(elements: readonly BoardElement[], operation: BoardOperation): BoardElement[] {
  switch (operation.action) {
    case 'PUT_ELEMENT': {
      const element = operation.element;
      if (!element) return [...elements];
      const at = elements.findIndex((other) => other.id === element.id);
      if (at === -1) return [...elements, element];
      return elements.map((other, index) => (index === at ? element : other));
    }
    case 'REMOVE_ELEMENT':
      return elements.filter((element) => element.id !== operation.elementId);
    case 'CLEAR':
      return [];
  }
}

/** What to draw: the board of the server with this client's operations on top. */
export function visibleElements(state: BoardState): BoardElement[] {
  return state.pending.reduce<BoardElement[]>(applyOperation, [...state.confirmed]);
}

function withoutPending(pending: readonly BoardOperation[], id: string): readonly BoardOperation[] {
  return pending.some((operation) => operation.id === id)
    ? pending.filter((operation) => operation.id !== id)
    : pending;
}

/** Whether the board already shows what the operation asks for, so there is nothing to send again. */
function alreadyIn(elements: readonly BoardElement[], operation: BoardOperation): boolean {
  switch (operation.action) {
    case 'PUT_ELEMENT': {
      const found = elements.find((element) => element.id === operation.element?.id);
      return Boolean(found) && JSON.stringify(found) === JSON.stringify(operation.element);
    }
    case 'REMOVE_ELEMENT':
      return !elements.some((element) => element.id === operation.elementId);
    case 'CLEAR':
      return elements.length === 0;
  }
}

function drain(state: BoardState): BoardState {
  let next = state;
  let ahead = [...next.ahead].sort((a, b) => a.version - b.version);
  while (ahead.length > 0 && ahead[0].version <= next.version + 1) {
    const [message, ...rest] = ahead;
    ahead = rest;
    next =
      message.version === next.version + 1
        ? {
            ...next,
            version: message.version,
            confirmed: applyOperation(next.confirmed, message.operation),
            pending: withoutPending(next.pending, message.operation.id),
          }
        : { ...next, pending: withoutPending(next.pending, message.operation.id) };
  }
  return { ...next, ahead };
}

/**
 * The whole board arrived: on opening, on rejoining, or because somebody replaced it. It replaces
 * what was known. An operation of this client that it already shows arrived before the connection
 * dropped; the others stay, to be sent again, which does no harm because they are idempotent.
 */
export function receiveBoard(state: BoardState, message: BoardMessage): BoardState {
  const confirmed = message.elements ?? [];
  return drain({
    ready: true,
    version: message.version,
    confirmed,
    pending: state.pending.filter((operation) => !alreadyIn(confirmed, operation)),
    ahead: state.ahead,
  });
}

/**
 * An operation the server applied. Its own echo is the confirmation. It is applied in version
 * order: one that arrives early waits, and one already seen changes nothing.
 */
export function receiveApplied(state: BoardState, message: AppliedMessage): BoardState {
  if (message.version <= state.version && state.ready) {
    return { ...state, pending: withoutPending(state.pending, message.operation.id) };
  }
  const ahead = [...state.ahead.filter((other) => other.version !== message.version), message];
  return state.ready ? drain({ ...state, ahead }) : { ...state, ahead };
}

/** Whether something is missing between the board and what has arrived. */
export function hasGap(state: BoardState): boolean {
  return state.ahead.length > 0;
}

export function addPending(state: BoardState, operation: BoardOperation): BoardState {
  return { ...state, pending: [...state.pending, operation] };
}

/** The server answers a channel in order, so a refusal is about the oldest thing it had not answered. */
export function refuseOldest(state: BoardState): BoardState {
  return { ...state, pending: state.pending.slice(1) };
}
