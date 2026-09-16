# 3. The module map

- **Status:** Accepted
- **Date:** 2026-09-15
- **Amended:** 2026-09-16, after the domain flows session

## Context

The backlog has thirteen Epics and sixty three items. Four groups of them had no module: buying
credits, moderation, notifications and institutional indicators. Leaving them unassigned means the
first person who implements one puts it wherever it is convenient, and the boundaries decided in
record 0002 erode on the first story.

## Decision

Twelve application modules, each owning one database schema.

| Module | Owns | Backlog |
|---|---|---|
| `identity` | Universities and their configuration, students, academic profile imported from the academic system | EP09, US50, US51, US52 |
| `skills` | Course and skill catalogue, offered skills, the two accreditation paths | EP03, EP10 |
| `booking` | Tutor availability, generated hour blocks, reservations, cancellation | EP01, EP04 |
| `matching` | Search for available tutors, kept fast by its own read projection | US01, US02, TS07 |
| `sessions` | Session lifecycle, attendance, whiteboard, support material | EP02 |
| `wallet` | Credit groups by origin, expiry, movements | EP05, US31 |
| `recognition` | Recognition requests, their evidence and the university's decision | EP06 except US31 |
| `reputation` | Ratings, tags, tutor standing per skill | EP07 |
| `payments` | Credit purchases | EP08 |
| `audit` | Append only activity log, and detection of behaviour that does not add up | US47, US48 |
| `analytics` | Usage and uncovered demand indicators, from read projections | US53 |
| `notifications` | Delivery of every notice, listening to domain events only | cross cutting |

Three of these deserve their reasoning written down.

`payments` is separate from `wallet` although a purchase ends as a movement in the ledger. Record
0002 names payments as the first candidate for extraction, and TS06 requires that a failing provider
not affect tutoring. Both are only true if the provider and its retries sit behind a boundary rather
than inside the ledger.

`audit` absorbed what an earlier version of this record called `trust`. They were split on the
assumption that users would report sessions and a moderator would resolve a queue of cases. The
domain flows session removed that: detection is automatic, and what the coordinator sees is a flag
attached to a recognition request, not an inbox. With the workflow gone, a separate module would own
nothing but a table nobody writes to by hand.

`notifications` is not an accessory. Registration depends on an email carrying an activation link,
so without this module there are no accounts at all. It belongs to the first sprint.

`config` and `shared` are not modules. They hold cross cutting wiring and building blocks, are
declared as shared packages to Spring Modulith, and must never be described as bounded contexts in
the report.

## Consequences

- Chapter IV has a container and component view with a defined number of elements, instead of one
  drawn from memory at the end.
- Twelve schemas means cross module reporting cannot use a join. `analytics` exists precisely to
  absorb that cost in one place.
- `notifications` gives the team a module that is isolated by construction: it depends on events,
  nothing depends on it, and a mistake inside it cannot reach the credit ledger.
- The administration of the platform, which crosses universities, has no module of its own yet. It
  currently lives in `identity`, next to the data it is not allowed to read. That tension is
  recorded in `../domain/01-event-storming.md` and is worth revisiting before the portal is built.
