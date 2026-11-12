/** A double of `WebSocket`, for the tests of the channel: it says what was sent, and takes what the server says. */
export class FakeSocket {
  static readonly OPEN = 1;
  static instances: FakeSocket[] = [];
  readyState = 0;
  sent: { type: string; operation: Record<string, unknown> }[] = [];
  onmessage: ((event: { data: string }) => void) | null = null;
  onclose: ((event: { code: number }) => void) | null = null;

  constructor(
    public url: string,
    public protocols: string[],
  ) {
    FakeSocket.instances.push(this);
  }

  send(data: string) {
    this.sent.push(JSON.parse(data));
  }

  close(code = 1000) {
    this.readyState = 3;
    this.onclose?.({ code });
  }

  /** The server opens the channel, and sends the whole board first of all. */
  accept(version = 0, elements: unknown[] = []) {
    this.readyState = FakeSocket.OPEN;
    this.say({ type: 'board', version, elements });
  }

  say(message: unknown) {
    this.onmessage?.({ data: JSON.stringify(message) });
  }

  /** The connection drops, as a network does. */
  drop(code = 1006) {
    this.readyState = 3;
    this.onclose?.({ code });
  }
}
