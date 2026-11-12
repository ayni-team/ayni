import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { BoardElement } from './board';
import type { BoardOperation } from './boardState';
import { WhiteboardChannel } from './channel';
import { FakeSocket } from './fakeSocket';
import { toBoardElement, type SceneElement } from './scene';
import { BoardSync, type Change } from './sync';

let nonce = 0;

function element(id: string, extra: Record<string, unknown> = {}): SceneElement {
  return {
    id,
    type: 'rectangle',
    x: 0,
    y: 0,
    width: 10,
    height: 10,
    version: 1,
    versionNonce: (nonce += 1),
    isDeleted: false,
    index: `a${id.length}`,
    boundElements: null,
    frameId: null,
    ...extra,
  } as unknown as SceneElement;
}

/** What Excalidraw does to an element on each edit: a later version and a new nonce. */
function edited(element: SceneElement, extra: Record<string, unknown> = {}): SceneElement {
  return { ...element, ...extra, version: element.version + 1, versionNonce: (nonce += 1) } as SceneElement;
}

const put = (scene: SceneElement): Change => ({ action: 'PUT', element: toBoardElement(scene) });
const alive = (scene: readonly SceneElement[]) =>
  scene
    .filter((each) => !each.isDeleted)
    .map((each) => `${each.id}@${each.version}`)
    .sort();

describe('the scene and the board in step', () => {
  it('sends what is new or edited, once, and nothing when nothing changed', () => {
    const sync = new BoardSync();
    const a = element('a');
    const b = element('b');

    expect(sync.changes([a, b])).toEqual([put(a), put(b)]);
    expect(sync.changes([a, b])).toEqual([]);

    const moved = edited(a, { x: 40 });
    expect(sync.changes([moved, b])).toEqual([put(moved)]);
  });

  it('sends a deleted element as its removal, only if the board had it', () => {
    const sync = new BoardSync();
    const a = element('a');
    const b = element('b');
    sync.changes([a, b]);

    const gone = edited(a, { isDeleted: true });
    const never = element('never', { isDeleted: true });
    expect(sync.changes([gone, b, never])).toEqual([{ action: 'REMOVE', elementId: 'a' }]);
    expect(sync.changes([gone, b, never])).toEqual([]);
    // Undoing the deletion brings it back with a later version: it goes again.
    const back = edited(gone, { isDeleted: false });
    expect(sync.changes([back, b])).toEqual([put(back)]);
  });

  it('sends "reset the canvas" as one CLEAR', () => {
    const sync = new BoardSync();
    const scene = [element('a'), element('b'), element('c')];
    sync.changes(scene);

    expect(sync.changes(scene.map((each) => edited(each, { isDeleted: true })))).toEqual([{ action: 'CLEAR' }]);
  });

  it('never sends back what it received', () => {
    const sync = new BoardSync();
    const theirs = element('theirs');

    const scene = sync.receive([toBoardElement(theirs)], []);
    expect(alive(scene ?? [])).toEqual(['theirs@1']);
    expect(sync.changes(scene ?? [])).toEqual([]);
    // The same board again changes nothing, so nothing is redrawn.
    expect(sync.receive([toBoardElement(theirs)], scene ?? [])).toBeNull();
  });

  it('takes the board over a local element nobody touched here, and keeps one edited and not yet sent', () => {
    const sync = new BoardSync();
    const a = element('a');
    const b = element('b');
    const start = sync.receive([toBoardElement(a), toBoardElement(b)], []) ?? [];

    const theirA = edited(a, { x: 99 });
    const theirB = edited(b, { x: 99 });
    const mineB = edited(b, { y: 5 });
    const next = sync.receive([toBoardElement(theirA), toBoardElement(theirB)], [start[0], mineB]) ?? [];

    expect(next.find((each) => each.id === 'a')).toMatchObject({ x: 99 });
    expect(next.find((each) => each.id === 'b')).toMatchObject({ y: 5 });
    // The local edit goes next, after theirs in the server's order.
    expect(sync.changes(next)).toEqual([put(mineB)]);
  });

  it('does not take an element from under the pointer while it is being edited', () => {
    const sync = new BoardSync();
    const a = element('a');
    const start = sync.receive([toBoardElement(a)], []) ?? [];

    const theirs = edited(a, { x: 99 });
    expect(sync.receive([toBoardElement(theirs)], start, new Set(['a']))).toBeNull();
  });

  it('deletes here what the board no longer has, without sending anything back', () => {
    const sync = new BoardSync();
    const a = element('a');
    const b = element('b');
    const start = sync.receive([toBoardElement(a), toBoardElement(b)], []) ?? [];

    const next = sync.receive([toBoardElement(b)], start) ?? [];

    expect(next.find((each) => each.id === 'a')).toMatchObject({ isDeleted: true });
    expect(alive(next)).toEqual(['b@1']);
    expect(sync.changes(next)).toEqual([]);
    // Somebody brings it back (their undo): it is drawn again here.
    const back = { ...a, version: 3, versionNonce: 4242 } as SceneElement;
    expect(alive(sync.receive([toBoardElement(back), toBoardElement(b)], next) ?? [])).toEqual(['a@3', 'b@1']);
  });

  it('keeps what was drawn here and not sent yet when the board arrives whole', () => {
    const sync = new BoardSync();
    const mine = element('mine');

    const next = sync.receive([toBoardElement(element('theirs'))], [mine]) ?? [];

    expect(alive(next)).toEqual(['mine@1', 'theirs@1']);
    expect(sync.changes(next)).toEqual([put(mine)]);
  });

  it('parses each edit of an element once', () => {
    const restore = vi.fn((elements: SceneElement[]) => elements);
    const sync = new BoardSync(restore);
    const a = toBoardElement(element('a'));
    const b = toBoardElement(element('b'));

    const first = sync.receive([a], []) ?? [];
    sync.receive([a, b], first);

    expect(restore.mock.calls.map(([elements]) => elements.map((each) => each.id))).toEqual([['a'], ['b']]);
  });
});

/**
 * Two people on the same board: each one an Excalidraw scene, a `BoardSync` and a real
 * `WhiteboardChannel` over a `FakeSocket`, and a server that applies the operations in the order it
 * takes them and tells both, as `WhiteboardEditing` does.
 */
class Person {
  scene: SceneElement[] = [];
  readonly sync = new BoardSync();
  readonly channel: WhiteboardChannel;
  socket!: FakeSocket;

  constructor(readonly name: string) {
    let count = 0;
    this.channel = new WhiteboardChannel({
      url: `ws://test/${name}`,
      token: () => 'token',
      newId: () => `${name}-${(count += 1)}`,
    });
    this.channel.subscribe(() => this.receive());
  }

  connect(server: Server) {
    this.channel.connect();
    this.socket = FakeSocket.instances[FakeSocket.instances.length - 1];
    server.join(this);
  }

  receive() {
    const board = this.channel.getSnapshot();
    if (!board.ready) return;
    const next = this.sync.receive(board.elements, this.scene);
    if (next) this.scene = next;
  }

  /** A change of the scene, as a person makes it, and what Excalidraw's onChange leads to. */
  draw(change: (scene: SceneElement[]) => SceneElement[]) {
    this.scene = change(this.scene);
    for (const each of this.sync.changes(this.scene)) {
      if (each.action === 'PUT') this.channel.put(each.element);
      else if (each.action === 'REMOVE') this.channel.remove(each.elementId);
      else this.channel.clear();
    }
  }
}

class Server {
  version = 0;
  elements: BoardElement[] = [];
  private readonly people: Person[] = [];
  private readonly read = new Map<FakeSocket, number>();

  join(person: Person) {
    this.people.push(person);
    this.read.set(person.socket, person.socket.sent.length);
    person.socket.accept(this.version, this.elements);
  }

  leave(person: Person) {
    this.people.splice(this.people.indexOf(person), 1);
  }

  /** Takes what each one sent, alternating between them, and tells everybody connected. */
  pump() {
    let more = true;
    while (more) {
      more = false;
      for (const person of [...this.people]) {
        const at = this.read.get(person.socket) ?? 0;
        const message = person.socket.sent[at];
        if (!message) continue;
        this.read.set(person.socket, at + 1);
        more = true;
        this.apply(message.operation as unknown as BoardOperation);
      }
    }
  }

  private apply(operation: BoardOperation) {
    if (operation.action === 'CLEAR') this.elements = [];
    else if (operation.action === 'REMOVE_ELEMENT') {
      this.elements = this.elements.filter((each) => each.id !== operation.elementId);
    } else if (operation.element) {
      const at = this.elements.findIndex((each) => each.id === operation.element?.id);
      this.elements =
        at === -1
          ? [...this.elements, operation.element]
          : this.elements.map((each, i) => (i === at ? operation.element! : each));
    }
    this.version += 1;
    for (const person of this.people) {
      person.socket.say({ type: 'applied', version: this.version, operation });
    }
  }
}

describe('two people on one board, through the channel', () => {
  let server: Server;
  let ana: Person;
  let bruno: Person;

  beforeEach(() => {
    vi.useFakeTimers();
    FakeSocket.instances = [];
    vi.stubGlobal('WebSocket', FakeSocket);
    server = new Server();
    ana = new Person('ana');
    bruno = new Person('bruno');
    ana.connect(server);
    bruno.connect(server);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  function same() {
    const stored = server.elements
      .map((each) => `${each.id}@${(each.data as unknown as { version: number }).version}`)
      .sort();
    expect(alive(ana.scene)).toEqual(stored);
    expect(alive(bruno.scene)).toEqual(stored);
  }

  it('what one draws, the other sees; edits and deletions too', () => {
    const square = element('square');
    ana.draw((scene) => [...scene, square]);
    server.pump();
    same();
    expect(alive(bruno.scene)).toEqual(['square@1']);

    bruno.draw((scene) => scene.map((each) => edited(each, { x: 50 })));
    server.pump();
    same();
    expect(ana.scene[0]).toMatchObject({ x: 50, version: 2 });

    bruno.draw((scene) => scene.map((each) => edited(each, { isDeleted: true })));
    server.pump();
    same();
    expect(alive(ana.scene)).toEqual([]);
    // Nobody echoed anything: one operation each time.
    expect(server.version).toBe(3);
  });

  it('ends with the same board when both change the same element at once', () => {
    const square = element('square');
    ana.draw((scene) => [...scene, square]);
    server.pump();

    ana.draw((scene) => scene.map((each) => edited(each, { x: 1 })));
    bruno.draw((scene) => scene.map((each) => edited(each, { x: 2, version: 9 })));
    ana.draw((scene) => [...scene, element('circle', { type: 'ellipse' })]);
    bruno.draw((scene) => [...scene, element('arrow', { type: 'arrow' })]);
    server.pump();

    same();
    expect(alive(ana.scene)).toHaveLength(3);
    // The server's order decides: the edit it took last is the one both have.
    expect(ana.scene.find((each) => each.id === 'square')).toEqual(bruno.scene.find((each) => each.id === 'square'));
  });

  it("one person's undo does not undo the other's", () => {
    const mine = element('mine');
    const theirs = element('theirs');
    ana.draw((scene) => [...scene, mine]);
    bruno.draw((scene) => [...scene, theirs]);
    server.pump();

    // Ana undoes her own: Excalidraw deletes it, with a later version.
    ana.draw((scene) => scene.map((each) => (each.id === 'mine' ? edited(each, { isDeleted: true }) : each)));
    server.pump();

    same();
    expect(alive(bruno.scene)).toEqual(['theirs@1']);
  });

  it('a reset clears both boards', () => {
    ana.draw((scene) => [...scene, element('a'), element('b')]);
    bruno.draw((scene) => [...scene, element('c')]);
    server.pump();

    ana.draw((scene) => scene.map((each) => edited(each, { isDeleted: true })));
    server.pump();

    same();
    expect(server.elements).toEqual([]);
    expect(alive(bruno.scene)).toEqual([]);
  });

  it('after a dropped connection, the board the server sends whole leaves both the same', () => {
    ana.draw((scene) => [...scene, element('a'), element('b')]);
    server.pump();

    // Ana loses the connection; meanwhile Bruno edits, deletes and draws.
    server.leave(ana);
    ana.socket.drop();
    bruno.draw((scene) =>
      scene.map((each) => (each.id === 'a' ? edited(each, { isDeleted: true }) : edited(each, { x: 7 }))),
    );
    bruno.draw((scene) => [...scene, element('c')]);
    // And Ana draws offline.
    ana.draw((scene) => [...scene, element('offline')]);
    server.pump();

    vi.advanceTimersByTime(1000);
    ana.connect(server);
    server.pump();

    same();
    expect(alive(ana.scene)).toEqual(['b@2', 'c@1', 'offline@1']);
  });

  it('a new page of the same person gets the whole board back', () => {
    ana.draw((scene) => [...scene, element('a'), element('b')]);
    server.pump();

    const again = new Person('ana-again');
    again.connect(server);

    expect(alive(again.scene)).toEqual(alive(ana.scene));
  });
});
