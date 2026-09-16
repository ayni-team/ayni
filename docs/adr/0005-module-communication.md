# 5. Commands cross module boundaries synchronously, facts as domain events

- **Status:** Accepted
- **Date:** 2026-09-16

## Context

Modules do not import one another, so every interaction between them has to pick a mechanism. Two
mistakes are available, and both are easy to make.

Making events the default decouples everything and breaks the cases that need an answer. Reserving a
slot has to know, right now, whether the student holds enough credits; expressed as an event, the
caller cannot act on the answer and the use case turns into a state machine waiting for a reply that
may never come.

The opposite mistake is calling everybody. If `sessions` has to tell `wallet`, `reputation`,
`recognition` and `notifications` what to do when a session ends, then closing a session becomes a
list of everything that must happen afterwards, and every new consequence edits that list again.

## Decision

Two mechanisms, and one question decides between them: **does the caller need the answer in order to
continue?**

**Yes — call the interface the other module publishes at its root package.** This is a command: the
caller asks for something and handles the refusal.

> `booking` asks `wallet` to charge the credits and receives the charge or a rejection.
> `booking` asks `skills` whether the tutor is enabled for that course.

**No — publish a domain event.** The publisher states what happened and does not know who reacts.

> `sessions` publishes `SessionCompleted`. `wallet` credits the earned credits, `reputation` opens
> the rating window, `recognition` advances the progress, `notifications` tells both participants.

Events live in `pe.ayni.shared.events`, not inside the module that publishes them, so that neither
side depends on the other. Every event carries its `tenantId`, because whoever reacts runs outside
the original request and has no other way to know which university it belongs to.

A module that needs to read a lot of data owned by others keeps its own read projection, updated
from their events, rather than querying them on every request. `matching` and `analytics` work that
way.

## Consequences

- Adding a consequence to something that already happened is a new listener, not a change to the
  code that caused it.
- Consistency between modules is eventual. A rating window that opens milliseconds after the session
  closes is fine; a credit balance that is wrong is not, which is why the charge is synchronous.
- **Events are delivered in memory and are not persisted.** If a listener throws, or the application
  stops between publishing and handling, that event is lost: there is no retry and no record of it.
  That is acceptable while this runs on a developer's machine, and it is what TS04 asks to fix. The
  fix is the Spring Modulith event publication registry, which stores every publication in the same
  transaction as the change that produced it and retries what failed, and it arrives in its own ADD
  iteration together with the compensation of the reservation saga. See
  `0004-start-simple-add-quality-attributes-per-iteration.md`.
- Until then, anything that must not be lost, such as charging or refunding credits, is a
  synchronous call inside the same transaction, not an event.
