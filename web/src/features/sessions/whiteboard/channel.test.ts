import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { BoardElement } from './board';
import { WhiteboardChannel, backoffMs, channelUrl, SESSION_OVER } from './channel';
import { FakeSocket } from './fakeSocket';

const el = (id: string): BoardElement => ({ id, type: 'stroke', data: { color: '#000' } as never });
const ids = (channel: WhiteboardChannel) => channel.getSnapshot().elements.map((element) => element.id);

function newChannel(options: { onClosed?: () => void; id?: string } = {}) {
  let count = 0;
  const channel = new WhiteboardChannel({
    url: 'ws://test/live',
    token: () => 'the-token',
    newId: () => `${options.id ?? 'op'}-${(count += 1)}`,
    onClosed: options.onClosed,
  });
  channel.connect();
  return channel;
}

function last(): FakeSocket {
  return FakeSocket.instances[FakeSocket.instances.length - 1];
}

describe('FE-22 the live channel', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    FakeSocket.instances = [];
    vi.stubGlobal('WebSocket', FakeSocket);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('opens with the token as a subprotocol, and the board arrives whole', () => {
    const channel = newChannel();
    expect(last().url).toBe('ws://test/live');
    expect(last().protocols).toEqual(['bearer', 'the-token']);
    expect(channel.getSnapshot()).toMatchObject({ status: 'CONNECTING', ready: false });

    last().accept(4, [el('a')]);

    expect(channel.getSnapshot()).toMatchObject({ status: 'LIVE', ready: true });
    expect(ids(channel)).toEqual(['a']);
  });

  it('draws at once, sends the operation, and takes the echo as the confirmation', () => {
    const channel = newChannel();
    last().accept();

    expect(channel.put(el('mine'))).toBe('OK');

    expect(ids(channel)).toEqual(['mine']);
    expect(channel.getSnapshot().unconfirmed).toBe(1);
    expect(last().sent).toEqual([
      { type: 'operation', operation: { id: 'op-1', action: 'PUT_ELEMENT', element: el('mine') } },
    ]);

    last().say({ type: 'applied', version: 1, operation: last().sent[0].operation });

    expect(channel.getSnapshot().unconfirmed).toBe(0);
    expect(ids(channel)).toEqual(['mine']);
  });

  it('sends a removal and a clear', () => {
    const channel = newChannel();
    last().accept(1, [el('a'), el('b')]);

    channel.remove('a');
    expect(ids(channel)).toEqual(['b']);
    channel.clear();
    expect(ids(channel)).toEqual([]);

    expect(last().sent.map((message) => message.operation.action)).toEqual(['REMOVE_ELEMENT', 'CLEAR']);
    expect(last().sent[0].operation.elementId).toBe('a');
  });

  it('reconnects with a bounded wait, takes the whole board and sends what was left', () => {
    const channel = newChannel();
    last().accept(1, [el('a')]);
    const first = last();

    channel.put(el('sent-before'));
    first.drop();
    expect(channel.getSnapshot().status).toBe('RECONNECTING');

    // Drawn while there is no connection: it shows, and waits.
    channel.put(el('drawn-offline'));
    expect(ids(channel)).toEqual(['a', 'sent-before', 'drawn-offline']);
    expect(first.sent).toHaveLength(1);

    vi.advanceTimersByTime(backoffMs(1));
    expect(FakeSocket.instances).toHaveLength(2);

    // The server saw the first one before the connection dropped; the other, not.
    last().accept(2, [el('a'), el('sent-before')]);

    expect(channel.getSnapshot().status).toBe('LIVE');
    expect(last().sent.map((message) => (message.operation.element as BoardElement).id)).toEqual(['drawn-offline']);
    expect(ids(channel)).toEqual(['a', 'sent-before', 'drawn-offline']);
  });

  it('sends nothing until the whole board arrives', () => {
    const channel = newChannel();
    channel.put(el('early'));
    expect(last().sent).toEqual([]);

    last().accept();

    expect(last().sent).toHaveLength(1);
  });

  it('waits longer each time, says it is offline, and keeps trying', () => {
    expect([1, 2, 3, 4, 5, 6, 7, 8].map(backoffMs)).toEqual([500, 1000, 2000, 4000, 8000, 10_000, 10_000, 10_000]);

    const channel = newChannel();
    for (let failures = 1; failures <= 5; failures += 1) {
      last().drop();
      if (failures < 5) expect(channel.getSnapshot().status).toBe('RECONNECTING');
      vi.advanceTimersByTime(backoffMs(failures));
    }
    expect(channel.getSnapshot().status).toBe('OFFLINE');
    expect(FakeSocket.instances).toHaveLength(6);

    last().accept();
    expect(channel.getSnapshot().status).toBe('LIVE');
  });

  it('closes clean when the session is over: no retry, and nothing more is sent', () => {
    const onClosed = vi.fn();
    const channel = newChannel({ onClosed });
    last().accept(1, [el('a')]);

    last().drop(SESSION_OVER);

    expect(channel.getSnapshot().status).toBe('CLOSED');
    expect(onClosed).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(60_000);
    expect(FakeSocket.instances).toHaveLength(1);

    channel.put(el('late'));
    expect(ids(channel)).toEqual(['a']);
  });

  it('asks for the whole board when an operation never arrives', () => {
    const channel = newChannel();
    last().accept(1);
    last().say({ type: 'applied', version: 3, operation: { id: 'x', action: 'PUT_ELEMENT', element: el('x') } });
    expect(ids(channel)).toEqual([]);

    vi.advanceTimersByTime(2000);
    expect(channel.getSnapshot().status).toBe('RECONNECTING');
    vi.advanceTimersByTime(backoffMs(1));

    last().accept(3, [el('v2'), el('x')]);
    expect(ids(channel)).toEqual(['v2', 'x']);
  });

  it('respects the limits of the server, on this side', () => {
    const channel = newChannel();
    const big: BoardElement = { id: 'big', type: 'stroke', data: { text: 'x'.repeat(17 * 1024) } as never };
    last().accept(
      1,
      Array.from({ length: 2000 }, (_, index) => el(`e${index}`)),
    );

    expect(channel.put(big)).toBe('TOO_BIG');
    expect(channel.put(el('one-more'))).toBe('FULL');
    // Replacing one already on the board does not make it grow.
    expect(channel.put(el('e7'))).toBe('OK');
    expect(last().sent).toHaveLength(1);
  });

  it('forgets what the server refused, and says so', () => {
    const channel = newChannel();
    last().accept();
    channel.put(el('bad'));

    last().say({ type: 'error', status: 400, message: 'malformed' });

    expect(ids(channel)).toEqual([]);
    expect(channel.getSnapshot().refused).toBe(true);

    channel.put(el('good'));
    expect(channel.getSnapshot().refused).toBe(false);
  });

  it('does not reconnect after being stopped', () => {
    const channel = newChannel();
    last().accept();

    channel.stop();
    vi.advanceTimersByTime(60_000);

    expect(FakeSocket.instances).toHaveLength(1);
  });

  it('two clients drawing at once end with the same board', () => {
    const ana = newChannel({ id: 'ana' });
    const anaSocket = last();
    const bruno = newChannel({ id: 'bruno' });
    const brunoSocket = last();
    anaSocket.accept(0);
    brunoSocket.accept(0);

    // A tiny server: it orders what it receives and sends it to both, in a shuffled arrival for one.
    ana.put(el('a1'));
    bruno.put(el('b1'));
    ana.put(el('same'));
    bruno.put({ ...el('same'), data: { color: '#fff' } as never });
    bruno.remove('a1');

    const operations = [
      anaSocket.sent[0].operation,
      brunoSocket.sent[0].operation,
      anaSocket.sent[1].operation,
      brunoSocket.sent[1].operation,
      brunoSocket.sent[2].operation,
    ];
    const applied = operations.map((operation, index) => ({ type: 'applied', version: index + 1, operation }));
    applied.forEach((message) => anaSocket.say(message));
    [applied[2], applied[0], applied[4], applied[1], applied[3]].forEach((message) => brunoSocket.say(message));

    expect(ana.getSnapshot().elements).toEqual(bruno.getSnapshot().elements);
    expect(ana.getSnapshot().unconfirmed).toBe(0);
    expect(bruno.getSnapshot().unconfirmed).toBe(0);
    expect(ids(ana)).toEqual(['b1', 'same']);
    expect(ana.getSnapshot().elements[1].data).toEqual({ color: '#fff' });
  });

  it('writes the address of the channel with ws for http', () => {
    expect(channelUrl('s1')).toBe(`ws://${window.location.host}/api/v1/sessions/s1/whiteboard/live`);
  });
});
