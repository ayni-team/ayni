import { apiUrl } from '@/shared/api';
import type { BoardElement } from './board';
import {
  EMPTY_BOARD,
  MAX_ELEMENTS,
  MAX_ELEMENT_BYTES,
  addPending,
  elementBytes,
  hasGap,
  receiveApplied,
  receiveBoard,
  refuseOldest,
  visibleElements,
  type BoardOperation,
  type BoardState,
  type ServerMessage,
} from './boardState';

export type ChannelStatus = 'CONNECTING' | 'LIVE' | 'RECONNECTING' | 'OFFLINE' | 'CLOSED';

export interface ChannelSnapshot {
  status: ChannelStatus;
  ready: boolean;
  elements: readonly BoardElement[];
  /** Operations sent and not yet confirmed. */
  unconfirmed: number;
  /** The server refused the last thing drawn. Cleared by the next one. */
  refused: boolean;
}

/** The session is over: the server closes the channel with this code, and there is nothing to retry. */
export const SESSION_OVER = 4409;
/** After this many failures in a row it says it has no connection. It goes on trying. */
const OFFLINE_AFTER = 5;
const BACKOFF_START_MS = 500;
const BACKOFF_LIMIT_MS = 10_000;
/** How long an operation may wait for the ones before it, before asking for the whole board. */
const GAP_WAIT_MS = 2000;
const RESYNC = 4000;

/** The wait before trying again, after that many failures: 0.5 s, 1 s, 2 s ... up to 10 s. */
export function backoffMs(failures: number): number {
  return Math.min(BACKOFF_START_MS * 2 ** Math.max(0, failures - 1), BACKOFF_LIMIT_MS);
}

/** The address of the channel: the one of the API, with `ws` for `http`. */
export function channelUrl(sessionId: string): string {
  const path = apiUrl(`/api/v1/sessions/${encodeURIComponent(sessionId)}/whiteboard/live`);
  if (/^https?:/.test(path)) return path.replace(/^http/, 'ws');
  const { protocol, host } = window.location;
  return `${protocol === 'https:' ? 'wss' : 'ws'}://${host}${path}`;
}

export type PutResult = 'OK' | 'TOO_BIG' | 'FULL';

export interface ChannelOptions {
  url: string;
  /** The session token: a browser cannot set `Authorization` on a WebSocket. */
  token: () => string | null;
  newId?: () => string;
  onClosed?: () => void;
}

/**
 * The live channel of a whiteboard, without React. It keeps the board, sends this client's
 * operations, takes the server's echo as the confirmation, and reconnects with a bounded wait,
 * receiving the whole board again and sending what was left unconfirmed.
 */
export class WhiteboardChannel {
  private board: BoardState = EMPTY_BOARD;
  private status: ChannelStatus = 'CONNECTING';
  private refused = false;
  private socket: WebSocket | null = null;
  private failures = 0;
  private stopped = false;
  private retry: number | undefined;
  private gap: number | undefined;
  private snapshot: ChannelSnapshot;
  private readonly listeners = new Set<() => void>();

  constructor(private readonly options: ChannelOptions) {
    this.snapshot = this.read();
  }

  connect(): void {
    this.stopped = false;
    this.open();
  }

  stop(): void {
    this.stopped = true;
    window.clearTimeout(this.retry);
    window.clearTimeout(this.gap);
    const socket = this.socket;
    this.socket = null;
    socket?.close(1000);
  }

  /** The tab came back or the network returned: there is no reason to wait out the backoff. */
  retryNow(): void {
    if (this.stopped || this.status === 'CLOSED' || this.socket) return;
    window.clearTimeout(this.retry);
    this.open();
  }

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  getSnapshot = (): ChannelSnapshot => this.snapshot;

  put(element: BoardElement): PutResult {
    if (elementBytes(element) > MAX_ELEMENT_BYTES) return 'TOO_BIG';
    const known = visibleElements(this.board);
    if (known.length >= MAX_ELEMENTS && !known.some((other) => other.id === element.id)) return 'FULL';
    this.send({ id: this.newId(), action: 'PUT_ELEMENT', element });
    return 'OK';
  }

  remove(elementId: string): void {
    this.send({ id: this.newId(), action: 'REMOVE_ELEMENT', elementId });
  }

  clear(): void {
    this.send({ id: this.newId(), action: 'CLEAR' });
  }

  private newId(): string {
    return this.options.newId?.() ?? crypto.randomUUID();
  }

  private send(operation: BoardOperation): void {
    if (this.status === 'CLOSED') return;
    this.refused = false;
    this.board = addPending(this.board, operation);
    this.transmit(operation);
    this.publish();
  }

  private transmit(operation: BoardOperation): void {
    if (this.board.ready && this.socket?.readyState === WebSocket.OPEN) {
      this.socket.send(JSON.stringify({ type: 'operation', operation }));
    }
  }

  private open(): void {
    const token = this.options.token();
    if (!token) {
      this.setStatus('OFFLINE');
      return;
    }
    const socket = new WebSocket(this.options.url, ['bearer', token]);
    this.socket = socket;
    socket.onmessage = (event) => {
      if (socket === this.socket) this.receive(String(event.data));
    };
    socket.onclose = (event) => {
      if (socket === this.socket) this.closed(event.code);
    };
  }

  private receive(data: string): void {
    let message: ServerMessage;
    try {
      message = JSON.parse(data) as ServerMessage;
    } catch {
      return;
    }
    if (message.type === 'board') {
      this.board = receiveBoard(this.board, message);
      this.failures = 0;
      this.status = 'LIVE';
      this.watchGap();
      // Whatever is left was not seen by the server, or it does no harm to send it again.
      this.board.pending.forEach((operation) => this.transmit(operation));
    } else if (message.type === 'applied') {
      this.board = receiveApplied(this.board, message);
      this.watchGap();
    } else if (message.type === 'error' && message.status !== 409) {
      this.board = refuseOldest(this.board);
      this.refused = true;
    }
    this.publish();
  }

  private closed(code: number): void {
    this.socket = null;
    window.clearTimeout(this.gap);
    this.gap = undefined;
    if (this.stopped) return;
    if (code === SESSION_OVER) {
      this.board = { ...this.board, pending: [] };
      this.setStatus('CLOSED');
      this.options.onClosed?.();
      return;
    }
    this.failures += 1;
    this.setStatus(this.failures >= OFFLINE_AFTER ? 'OFFLINE' : 'RECONNECTING');
    this.retry = window.setTimeout(() => this.open(), backoffMs(this.failures));
  }

  /** An operation that does not arrive means the channel lost one: the whole board fixes it. */
  private watchGap(): void {
    if (!hasGap(this.board)) {
      window.clearTimeout(this.gap);
      this.gap = undefined;
    } else if (this.gap === undefined) {
      this.gap = window.setTimeout(() => {
        this.gap = undefined;
        if (hasGap(this.board)) this.socket?.close(RESYNC);
      }, GAP_WAIT_MS);
    }
  }

  private setStatus(status: ChannelStatus): void {
    this.status = status;
    this.publish();
  }

  private read(): ChannelSnapshot {
    return {
      status: this.status,
      ready: this.board.ready,
      elements: visibleElements(this.board),
      unconfirmed: this.board.pending.length,
      refused: this.refused,
    };
  }

  private publish(): void {
    this.snapshot = this.read();
    this.listeners.forEach((listener) => listener());
  }
}
