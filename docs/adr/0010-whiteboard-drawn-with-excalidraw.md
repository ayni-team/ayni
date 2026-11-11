# 10. The whiteboard is drawn with Excalidraw, over the channel of ADR 0009

- **Status:** Accepted
- **Date:** 2026-11-11

## Context

The first whiteboard of the web was our own: an SVG with a pen, two shapes, notes, an eraser and an
undo of one's own strokes. It worked, but a tutoring needs what any whiteboard has (arrows, text
anywhere, selecting and moving, layers, zoom, shortcuts), and building that well is a product of its
own. The lead decided to use Excalidraw, whole, with its toolbar as it is, and to keep everything
ADR 0009 decided on the server.

## Decision

**Excalidraw 0.18.1, pinned exactly** (`@excalidraw/excalidraw`, MIT, React 17 to 19). It is the one
dependency the frontend guide allows beyond its stack, and how it was vetted is in the pull request
that added it: 230 packages, none with an install script, installed with `--ignore-scripts`.

**What stays of ADR 0009: all of it.** The server stores `BoardElement { id, type, data }` without
reading `data`, applies `PUT_ELEMENT`, `REMOVE_ELEMENT` and `CLEAR` in its order, and answers the
whole board on rejoining. The protocol and the shape of the elements do not change.

**The mapping.** Each Excalidraw element is one element of the board: `id` is its id (Excalidraw's
fit the server's rule; one that did not would be renamed, with every reference to it), `type` its
type, and `data` the element itself, with the points of a stroke kept to two decimals.

- **Sending.** On every change of the scene the web compares each element's `version` and
  `versionNonce` with the last edit the board has, the one it sent or received, and sends only what
  differs: at most every 80 ms while somebody draws, and at once when they let go.
- **Deleting.** A deleted element is sent as `REMOVE_ELEMENT`, not kept as a tombstone: tombstones
  would count against the 2000 elements and fill the stored board with what nobody sees. Excalidraw
  keeps the deleted element in its own scene, so undoing a deletion sends the element again.
- **Receiving.** What arrives enters the scene with `captureUpdate: NEVER`: it never reaches the undo
  history, so undo and redo are each person's own, as in Excalidraw. It is never sent back, because
  it is already the last edit the board has. An element this person edited and has not sent yet,
  or is dragging right now, is not taken from under them.
- **Reset.** "Reset the canvas" deletes everything at once and is sent as one `CLEAR`.
- **Order.** Elements are drawn by Excalidraw's fractional index, and by id when two are equal, so
  both sides draw them in the same order.
- **Old boards.** Boards drawn before this hold the old types (`stroke`, `text`, `note`, `shape`).
  They are converted when read, by a pure function, and never rewritten: the room and the review of a
  session (FE-24, in Excalidraw's view mode) show them as they were.

**What is off, on purpose.** Images (the board carries no files), the library of templates (it is
downloaded from another site), embedded pages, Excalidraw's own live collaboration (its relay),
links to Excalidraw+ and social networks, Mermaid and AI diagrams, and opening or saving files.
Exporting the board as an image stays: it happens in the browser. Nothing is stored in the browser
and nothing is requested from another host: the fonts of the canvas are served from our origin
(`web/scripts/excalidraw-assets.mjs`).

**Limits.** An element may take 32 KB, twice the 16 KB of ADR 0009: a freehand stroke weighs about
15 bytes a point, so 16 KB was some 1000 points, ten seconds of drawing without lifting the pointer.
The board keeps its 2000 elements; a long session measured far below that.

## Consequences

- The web gains a whiteboard with every tool, and loses the code of its own: the SVG canvas and its
  tools are gone.
- Excalidraw is about 400 kB gzip (375 kB of JavaScript, 23 kB of CSS, 8.5 kB of Spanish), loaded only
  by the room and the review; the entry of the application does not grow.
- **Risks.** A new dependency with 230 packages in a repository that suffered injected code
  (`docs/security.md`); its transitive advisories, today in Mermaid and `nanoid`, are not reachable
  from the board but have to be read on each update. An update of Excalidraw may move what the
  stylesheet hides (`excalidraw.css`) or change its element format; the version is pinned and is
  changed by a pull request that runs the checks of this one again.
- The cursors of the other person are not shown: they need a message on the channel that is not
  stored, which is the next step.
