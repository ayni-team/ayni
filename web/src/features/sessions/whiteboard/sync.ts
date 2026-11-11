import type { BoardElement } from './board';
import { byIndex, isKept, sceneOf, toBoardElement, type SceneElement } from './scene';

/** What this client has to tell the server about its scene. */
export type Change =
  { action: 'PUT'; element: BoardElement } | { action: 'REMOVE'; elementId: string } | { action: 'CLEAR' };

/** Which edit of an element: Excalidraw bumps `version` and draws a new `versionNonce` on each. */
interface Stamp {
  version: number;
  versionNonce: number;
  /** The board no longer has it. */
  gone: boolean;
}

function stampOf(element: SceneElement): Stamp {
  return { version: element.version, versionNonce: element.versionNonce, gone: false };
}

function sameEdit(element: SceneElement, stamp: Stamp | undefined): boolean {
  return Boolean(stamp) && element.version === stamp?.version && element.versionNonce === stamp?.versionNonce;
}

function randomNonce(): number {
  return Math.floor(Math.random() * 2 ** 31);
}

/**
 * Keeps an Excalidraw scene and a board of the server in step (ADR 0010). It knows, for each
 * element, the last edit the board has: the one this client sent or the one it received.
 *
 * - `changes` answers what to send for the scene as it is: the elements whose edit the board does
 *   not have, and the ones deleted that it still has. Anything received is already known, so what
 *   arrives is never sent back.
 * - `receive` answers the scene with what the board says, or null when nothing changes. The board
 *   orders everything: an element it has replaces the local one, unless this client edited it
 *   since and has yet to send it; one it no longer has is deleted here too. That includes what
 *   this client sent and the board did not take (too big, or the board full): receiving the board
 *   again after a refusal takes it out of the scene.
 *
 * Pure: Excalidraw is not imported, `restore` is given (Excalidraw's `restoreElements` in the room,
 * nothing in the tests).
 */
export class BoardSync {
  private readonly known = new Map<string, Stamp>();
  /** What each element of the board became after `restore`, while its edit stays the same. */
  private readonly parsed = new Map<string, SceneElement>();

  constructor(private readonly restore: (elements: SceneElement[]) => SceneElement[] = (elements) => elements) {}

  /** What to send so that the board has the scene. Takes it as sent. */
  changes(scene: readonly SceneElement[]): Change[] {
    const puts: SceneElement[] = [];
    const removes: SceneElement[] = [];
    for (const element of scene) {
      if (!isKept(element)) continue;
      const stamp = this.known.get(element.id);
      if (element.isDeleted) {
        if (stamp && !stamp.gone) removes.push(element);
      } else if (!stamp || stamp.gone || !sameEdit(element, stamp)) {
        puts.push(element);
      }
    }
    const left = [...this.known.values()].filter((stamp) => !stamp.gone).length;
    const alive = scene.some((element) => !element.isDeleted && isKept(element));
    // "Reset the canvas" deletes every element at once: the board has an action for that.
    const cleared = removes.length > 1 && removes.length === left && !alive && puts.length === 0;
    for (const element of removes) this.forget(element.id, element);
    for (const element of puts) this.known.set(element.id, stampOf(element));
    if (cleared) return [{ action: 'CLEAR' }];
    return [
      ...removes.map((element): Change => ({ action: 'REMOVE', elementId: element.id })),
      ...puts.map((element): Change => ({ action: 'PUT', element: toBoardElement(element) })),
    ];
  }

  /** The scene with what the board says; null when it already shows it. */
  receive(
    board: readonly BoardElement[],
    scene: readonly SceneElement[],
    editing: ReadonlySet<string> = new Set(),
  ): SceneElement[] | null {
    const before = new Map(this.known);
    const remote = this.parse(board);
    for (const [id, element] of remote) this.known.set(id, stampOf(element));
    for (const [id, stamp] of before) {
      if (!stamp.gone && !remote.has(id)) this.forget(id);
    }

    let changed = false;
    const next: SceneElement[] = [];
    const local = new Set<string>();
    for (const element of scene) {
      local.add(element.id);
      const theirs = remote.get(element.id);
      // Edited here (or deleted) since the board last said, and not sent yet: it goes with the
      // next changes. A deletion that came from the board is known, so it is not "mine".
      const mine = isKept(element) && !sameEdit(element, before.get(element.id));
      if (theirs) {
        if (mine || editing.has(element.id) || sameEdit(element, this.known.get(element.id))) {
          next.push(element);
        } else {
          next.push(theirs);
          changed = true;
        }
      } else if (
        !element.isDeleted &&
        before.has(element.id) &&
        !before.get(element.id)?.gone &&
        !mine &&
        !editing.has(element.id)
      ) {
        // The board had it and no longer does: somebody deleted it, or cleared the board. It stays
        // in the scene as deleted, which is how Excalidraw keeps the undo of this client sound.
        const deleted = {
          ...element,
          isDeleted: true,
          version: element.version + 1,
          versionNonce: randomNonce(),
        } as SceneElement;
        next.push(deleted);
        this.forget(element.id, deleted);
        changed = true;
      } else {
        next.push(element);
      }
    }
    for (const [id, element] of remote) {
      if (!local.has(id)) {
        next.push(element);
        changed = true;
      }
    }
    if (!changed) return null;
    return next.sort(byIndex);
  }

  /** The board no longer has it; `as` is how the scene keeps it, deleted, if it does. */
  private forget(id: string, as?: SceneElement): void {
    const stamp = as ? stampOf(as) : this.known.get(id);
    this.known.set(id, { version: stamp?.version ?? 0, versionNonce: stamp?.versionNonce ?? 0, gone: true });
  }

  private parse(board: readonly BoardElement[]): Map<string, SceneElement> {
    const elements = sceneOf(board);
    const fresh = elements.filter((element) => !sameEdit(element, this.cachedStamp(element.id)));
    const restored = fresh.length > 0 ? this.restore(fresh) : [];
    for (const element of restored) this.parsed.set(element.id, element);
    const result = new Map<string, SceneElement>();
    for (const element of elements) {
      const known = this.parsed.get(element.id);
      if (known) result.set(element.id, known);
    }
    for (const id of [...this.parsed.keys()]) {
      if (!result.has(id)) this.parsed.delete(id);
    }
    return result;
  }

  private cachedStamp(id: string): Stamp | undefined {
    const element = this.parsed.get(id);
    return element ? stampOf(element) : undefined;
  }
}
