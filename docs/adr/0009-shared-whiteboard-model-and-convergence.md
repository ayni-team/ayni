# 9. The shared whiteboard: elements, operations in the server's order, and a snapshot to rejoin

- **Status:** Accepted
- **Date:** 2026-10-15

## Context

US10 asks for a whiteboard both participants of a session draw on at the same time, whose content
survives a dropped connection and the end of the session. TS08 puts numbers on it: the other
participant sees a change in less than 300 ms, a participant who reconnects has the whole board
again in less than 3 s, two people drawing at once end up seeing the same thing, and all of this
holds with twenty sessions at once.

Two decisions follow from that, and both are hard to change later: what the content of a board is,
and how two people editing it at once are made to converge.

The usual answers for concurrent editing are an operational transform or a CRDT. Both converge
without a central order, which is what they are for: peers that edit offline and merge later. Ayni
has neither need. A board only exists while its session is in progress, both participants are
online by definition, and every change already goes through the backend, which has to check that
the person is a participant and that the session is still on.

`data-model.md` already fixes the table: `sessions.whiteboards` with `content jsonb` and
`has_activity`.

## Decision

**The content is a set of elements.** Each element has an `id` chosen by the client that creates it
(1 to 64 letters, digits, `-` or `_`), a `type` (`stroke`, `text`, `shape`, whatever the client
draws; 1 to 32 characters) and a `data` object the backend stores as it comes. `content` holds
`{"version": <n>, "elements": [...]}` in the order the elements were first drawn. The backend never
interprets `data`: drawing is the client's business, and keeping the board opaque lets the screen
evolve without a migration.

**A change is an operation**, also with an `id` from the client:

| Action | Does | Applied twice |
|---|---|---|
| `PUT_ELEMENT` | adds the element, or replaces the one with its `id` | same board |
| `REMOVE_ELEMENT` | removes the element with `elementId`, if it is there | same board |
| `CLEAR` | removes every element | same board |

Every action is idempotent, so a client that resends an operation after a dropped connection does
no harm.

**The server decides the order.** Each operation is applied by the backend, which locks the session
while it does, gives the board its next `version` and commits. The operation is then sent to both
participants with that version, the sender included, who takes it as the confirmation. A client
applies what it receives in version order; for the same element the later version wins. Since both
participants apply the same operations in the same order, they end with the same board, and it is
the one stored. Convergence is a total order, not a merge.

**Rejoining is a snapshot.** Opening the live channel answers first with the whole board and its
version. A participant who reconnects does not replay what they missed: they receive the board as it
is, which is bounded by the board's size and not by how long they were away. `GET
/api/v1/sessions/{id}/whiteboard` answers the same snapshot over HTTP, and `PUT` replaces the whole
board when the client sends the version it last saw (a stale version answers `409`).

**The live channel is a WebSocket** at `/api/v1/sessions/{id}/whiteboard/live`, authenticated with
the same session token as every request (TS03). A browser cannot set `Authorization` on a WebSocket,
so the token may also travel as the subprotocol pair `bearer, <token>`; the server answers the
`bearer` subprotocol. Only the two participants of a session in progress are let in, and the channel
is closed by the server when the session closes.

**Limits.** At most 2000 elements, 16 KB per element and 256 KB per message. A board that would grow
past them refuses the operation.

**What the session leaves behind.** Every applied operation is persisted, so closing the session has
nothing to save: the board stays as it was. `has_activity` becomes true with the first operation and
never goes back. When a session closes, sessions publishes `WhiteboardClosed` with
`hadActivity`, which is what an audit rule like `NO_BOARD_ACTIVITY` needs; a board nobody touched
is stored too, empty, so the history says so.

## Consequences

- Convergence is simple to reason about and to test: compare what both clients and the database
  end with.
- Every stroke is a transaction and a row lock. At the size of a tutoring session, two people, it is
  far below what PostgreSQL handles, and TS08 measures it with twenty sessions at once.
- **The channels live in the memory of one instance.** A second instance of the backend would not
  see the other's sockets. That is fine for the modular monolith of ADR 0002; scaling out needs a
  broker in between, and the order would still come from the database.
- The token is checked when the channel opens. A token revoked while a session is in progress keeps
  that one channel until it closes, at most until the session ends.
- The backend does not know what a stroke looks like, so it cannot validate one beyond its size.
