# 5. Commands cross module boundaries synchronously, facts as domain events

- **Status:** Accepted
- **Date:** 2026-09-16
- **Amended:** 2026-09-20, after implementing `wallet`

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

## Amendment, 2026-09-20: the refund travels both ways

Implementing `wallet` made a contradiction visible. The rule above says a refund is a synchronous
call. `backend-guide.md` says `wallet` listens to `BookingCancelled`. Both are true on purpose, and
the order between them matters.

`booking` calls `WalletApi.refund(bookingId)` inside the transaction that cancels the booking. That
call **is** the refund: if it fails, the cancellation fails with it, which is the property this
record was written to protect.

`wallet` also listens to `BookingCancelled`, and that listener is a net rather than a second
payment. A refund reads from the ledger what the booking took and subtracts what has already been
given back, so a cancellation that arrives twice returns nothing the second time. The net therefore
catches a cancellation published by something that did not call the interface, and cannot pay
twice if nothing went wrong.

Belt and braces is free here only because the operation is idempotent. A consequence that were not
idempotent would still have to choose one mechanism, and by this record it would choose the call.

## Amendment, 2026-10-22: events are stored and delivered again (TS04)

The consequence above that says events are delivered in memory and are not persisted is no longer
true. The original text stays as written, as ADR 0001 asks; this is what replaces it.

**The decision.** Spring Modulith's event publication registry, through
`spring-modulith-starter-jpa`, at the version the BOM already pinned. Publishing an event stores one
row per `@ApplicationModuleListener` (and per `@TransactionalEventListener` that runs after the
commit) in `public.event_publication`, in the same transaction as the change that produced the
event: a change that rolls back stores nothing, and a change that commits cannot lose its event.
The row is deleted once its listener returns. A row that stays is a delivery still owed, and it is
delivered again in two ways: when the application starts
(`spring.modulith.events.republish-outstanding-events-on-restart=true`), and every five minutes for
the publications older than a minute (`config.EventPublicationResubmission`, whose delay is
`ayni.events.resubmit-delay`).

**Listeners keep running on the publisher's thread, right after the commit.** The library switches
`@Async` on; `config.ReliableEventsConfig` gives it an executor that runs on the calling thread, so
a request, a test and its listeners keep the order they had. Moving listeners to a pool is a
separate decision, with costs of its own.

**Completed publications are deleted, not archived** (`spring.modulith.events.completion-mode=delete`).
What happened is already kept where it belongs: the ledger keeps every movement and audit keeps
every fact. A finished delivery has nothing left to say, and an archive would be a second copy of
every event, identifiers of people included, growing with every request and read by nobody.

**The registry is not tenant scoped, and its table has no `tenant_id`.** It lives in `public`, next
to `flyway_schema_history`, because it belongs to none of the twelve modules. Each listener binds
the university of its event with `TenantContext.runAs`, the first time and every time again, which
is why the resubmission does not loop over the universities as the other jobs do.

**Secrets are not stored.** `config.RedactingEventSerializer` writes `AccessRequested` without its
email and its link, `PresenceCodeIssued` without its code, and `StudentActivated` without its email
and its student code. The first delivery receives the event from memory, in full. A delivery
resumed from the registry receives the stored copy: notifications drops a resumed link or code,
which is what it already did with one that failed, and the listeners of `StudentActivated` never
read either field.

**Delivering again requires every listener to be idempotent**, and the ones that were not were made
so: wallet's credit for a completed session and for a confirmed purchase (the session and the
purchase are the idempotency key, and the ledger turn of the university is taken before looking),
the no-show and the purchase notices (their identifier comes from the event), and the activity log
(its entry identifier comes from the event).

**What it costs.** One insert per listener in the publisher's transaction, and one delete per
listener after it. `SessionCompleted` has six listeners, so closing a session writes twelve rows
more than before. Measured on a laptop, publishing it with all its listeners went from a median of
7.2 ms to 11.1 ms, the new idempotency checks included. Reads, the search among them, publish
nothing and pay nothing.

**What it does not do.** Events are not sent to a broker, and no event or `*Api` changed. A
listener that swallows its own failures, as audit, analytics and the detection of anomalies do on
purpose, completes its publication and is not retried. The compensation of the reservation saga
that ADR 0004 lists next to TS04 is not part of this change.

The last consequence above still holds for a different reason: a refund stays a synchronous call
because the caller needs the answer, not because an event could be lost.
