# Backend guide

What every module exposes, what it publishes, and what it answers over HTTP. **This is the contract.
Everything inside a module is yours; everything in this document is not.**

The interfaces and events below already exist in code: each `*Api` with its views at the root of
its module, and every event in `pe.ayni.shared.events`. Implement them; do not redefine them. Until
the module that owns an interface implements it, mock it in your unit tests.

Read `database/data-model.md` for the tables of your module and `domain/01-event-storming.md` for
the flows behind them. Nothing here is negotiable by editing your own code: if you need a change to
a published interface, an event or an endpoint, say so in the pull request and it gets decided
there.

---

## How a module is built

```
booking/
├── BookingApi.java          the published interface. The ONLY class others may use
├── domain/model/            entities with their rules, mapped to the tables
├── domain/services/         rules that do not belong to a single entity
├── application/             use cases. The transaction starts and ends here
├── infrastructure/          Spring Data repositories, adapters
└── interfaces/rest/         controllers and DTOs
```

**Layer rules**

- The controller calls a use case. It never touches an entity or a repository.
- The use case owns the transaction: `@Transactional` goes here, never on the controller.
- The entity holds the rules that concern it. A class with only getters and setters means the rules
  ended up somewhere else.
- The published interface is implemented by a class in `application`, and the interface itself sits
  at the module root so others can depend on it.

**Crossing a boundary.** Two ways, and the question is whether you need the answer to continue. If
you do, call the other module's published interface. If you do not, publish an event and forget
about it. Never import a class from another module's inner packages: `ModularityTest` fails the
build.

**The university of every request.** Read it with `TenantContext.require()` and filter every query
by it:

```java
List<Booking> findByTenantIdAndStudentId(String tenantId, UUID studentId);
```

A query without that filter reads another university's data. Until the database enforces it, review
is the only thing preventing it.

**Time.** Inject `java.time.Clock` and read time from it. Never `Instant.now()` inside domain or
application code: a rule that depends on the clock cannot be tested if it reads it statically.

**Errors.** Throw a domain exception from the domain; map it in a `@RestControllerAdvice` inside
your module. Every error answers with the same shape: `timestamp`, `status`, `error`, `message`,
`path`.

**HTTP conventions.** Paths start with `/api/v1`. Dates travel in ISO 8601 UTC. Lists that can grow
accept `page` and `size`. Every endpoint carries its `@Operation` summary so Swagger is useful.

---

## identity

Owns universities, students, coordinators and the academic profile.

```java
public interface IdentityApi {
  Optional<TenantView> findTenantByEmailDomain(String email);
  TenantView requireTenant(String tenantCode);
  UserView requireUser(UUID userId);
  boolean isActive(UUID userId);
  Optional<CreditPolicyView> currentPolicy(String tenantCode, PolicyKind kind);
  List<ApprovedCourseView> approvedCourses(UUID userId);   // used by skills to enable by grade
  List<String> activeTenantCodes();              // used by scheduled jobs
}
```

**Publishes:** `AccessRequested`, `StudentActivated`, `CoordinatorActivated`,
`UniversityRegistered`, `UniversitySuspended`.

**Endpoints**

| Method | Path | Who | Does |
|---|---|---|---|
| POST | `/api/v1/access/request` | anyone | receives an email, decides the university, sends the link |
| POST | `/api/v1/access/confirm` | anyone | consumes the token, opens the session, returns the profile |
| GET | `/api/v1/me` | signed in | own profile |
| PUT | `/api/v1/me/profile` | student | photo and description only. Name, career and term come from the academic system and are not editable |
| POST | `/api/v1/me/academic-record/import` | student | refreshes the approved courses from the academic system; an onboarding step (US40) |
| POST | `/api/v1/admin/universities` | admin | registers a university |
| GET | `/api/v1/admin/universities` | admin | list with counts, never with people |
| POST | `/api/v1/admin/universities/{code}/coordinators` | admin | invites by email |
| PUT | `/api/v1/coordinator/credit-policy` | coordinator | supersedes the policy |

The administrator endpoints must never return a student's name, email or courses. That restriction
is the reason `platform_admins` is a separate table.

---

## skills

Owns the catalogue and who may teach what.

```java
public interface SkillsApi {
  boolean isTutorEnabledFor(UUID tutorId, UUID catalogItemId);
  List<UUID> enabledSkillsOf(UUID tutorId);
  CatalogItemView requireItem(UUID catalogItemId);
  void declareLearningInterests(UUID studentId, List<UUID> catalogItemIds);   // US40 onboarding
  List<UUID> declareTeachingInterests(UUID studentId, List<UUID> catalogItemIds);  // US40 onboarding
}
```

The two `declare…` methods are the skills half of US40's initial configuration. Teaching interests
go through the same academic record path as `POST /api/v1/tutor/skills`: items that do not qualify
are skipped, not refused, and the answer lists the ones that ended up enabled.

**Publishes:** `SkillEnabled`, `SkillWithdrawn`, `ValidationResolved`.
**Calls:** `IdentityApi.approvedCourses`, `IdentityApi.requireTenant` for the grade threshold.
**Listens to:** `StudentActivated` — to enable the courses their grades already justify.

| Method | Path | Who | Does |
|---|---|---|---|
| GET | `/api/v1/catalog` | student | global items plus their university's, filtered by category or text, paged |
| GET | `/api/v1/tutor/skills` | student | own skills with their status |
| GET | `/api/v1/tutor/skills/suggestions` | student | approved courses whose grade clears the threshold and are not offered yet |
| POST | `/api/v1/tutor/skills` | student | offers a skill. A course enables itself if the grade allows |
| DELETE | `/api/v1/tutor/skills/{id}` | student | withdraws it. Confirmed bookings stand |
| POST | `/api/v1/tutor/skills/{id}/validation` | student | submits evidence for a global tool |
| GET | `/api/v1/coordinator/validations` | coordinator | pending queue |
| POST | `/api/v1/coordinator/validations/{id}/decision` | coordinator | approves or rejects with a reason |

A university course is enabled only by grade. A global tool is enabled only by reviewed evidence.

---

## wallet

Owns the credits. **The only module allowed to write the ledger.**

```java
public interface WalletApi {
  CreditBalance balanceOf(UUID userId);
  ChargeReceipt charge(UUID userId, Credits amount, UUID bookingId);   // throws InsufficientCredits
  void refund(UUID bookingId);
  void grant(UUID userId, Credits amount, CreditType type, Instant expiresAt, UUID sourceId);
  Credits earnedTotal(UUID userId);
}
```

**Publishes:** `CreditsGranted`, `CreditsExpiring`, `CreditsExpired`.
**Listens to:** `StudentActivated` (grants the policy), `SessionCompleted` (credits the tutor),
`BookingCancelled` (refunds), `PurchaseConfirmed`.

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/wallet` | student — balance broken down by origin with expiry dates |
| GET | `/api/v1/wallet/movements` | student — history, filterable by period and type |

**Rules that live here and nowhere else:** spend the group closest to expiring first; a refund
returns credits to the group they came from with the expiry they had; the ledger is append only and
a correction is a new entry; only `SEED` and `ALLOCATED` expire.

A refund is idempotent, and it has to be. `booking` calls `WalletApi.refund` inside its own
transaction, and the `BookingCancelled` listener above is a net behind that call, not a second one.
The reasoning is in ADR 0005.

---

## booking

Owns availability and reservations.

```java
public interface BookingApi {
  BookingView requireBooking(UUID bookingId);
  List<OpenHourView> openHoursOf(UUID tutorId, Instant from);   // used by matching
}
```

`openHoursOf` was added by US01. Matching keeps its search as a projection fed by events, and an
event only announces hours at the moment they appear: when a tutor enables a course after their
hours exist, or a cancellation gives hours back, matching asks here which of the tutor's hours in
the current university have not started and can still be booked. A held hour counts as open,
because a hold ends either in a booking, which is announced, or back in circulation, which is not.

**Publishes:** `BookingConfirmed`, `BookingCancelled`, `AvailabilityPublished`, `HoursGenerated`,
`HoursWithdrawn`.
**Calls:** `SkillsApi.isTutorEnabledFor`, `WalletApi.charge`, `IdentityApi.isActive`.

| Method | Path | Who | Does |
|---|---|---|---|
| POST | `/api/v1/bookings/holds` | student | tutor, start, hours: holds them for five minutes while the need is written |
| DELETE | `/api/v1/bookings/holds` | student | tutor, start, hours as parameters: gives back what the student holds there |
| POST | `/api/v1/bookings` | student | tutor, skill, start, hours, need description |
| GET | `/api/v1/bookings/mine` | student | upcoming and history |
| DELETE | `/api/v1/bookings/{id}` | student or tutor | cancels, always refunds |
| GET | `/api/v1/tutor/agenda` | tutor | what is booked and what is still free |
| POST | `/api/v1/tutor/availability` | tutor | weekly ranges |
| POST | `/api/v1/tutor/availability/exceptions` | tutor | add or remove a specific date |
| POST | `/api/v1/tutor/availability/pauses` | tutor | pause a period |

**The confirmation, in order, inside one transaction:** the tutor is enabled → the hours are
contiguous and free → charge the credits → mark the hours → save the booking → publish
`BookingConfirmed`. If anything fails, nothing happened.

**The hold.** Choosing hours takes them out of circulation for five minutes before the student
confirms, so nobody takes them while the need is being written (`data-model.md`, and the hour block
state diagram). Confirming requires a live hold of the same student on every hour: that is what
"the hours are free" means in the order above. Holding again what one already holds does not extend
it. Every minute a scheduled job returns expired holds to `AVAILABLE`, one university at a time
(`ayni.booking.hold-release-delay`); before it runs, anybody may already take over an expired
hold. Both hold endpoints were added by US03: the contract only listed `POST /api/v1/bookings`,
while the data model describes a hold with no way to take it.

**When the confirmation fails.** The transaction rolls back whole, so the balance is as it was, and
then a second transaction gives back the student's holds on those hours, so they return to the
search at once. The second step cannot happen inside the first: wallet's refusal has already marked
it for rollback. A refusal answers 409 with the reason (the hour was taken, the hold ran out, the
tutor is not enabled, or the credits that are missing); anything unexpected answers 500 saying that
nothing was charged.

**Cancelling:** always refunds. Inside twelve hours of the start it is recorded against whoever
cancelled. A tutor removing availability over a confirmed booking is cancelling that session.

---

## matching

Owns nothing but the search. **No published interface:** it is read only.

**Listens to:** `HoursGenerated`, `HoursWithdrawn`, `BookingConfirmed`, `BookingCancelled`, `SkillEnabled`,
`SkillWithdrawn`, `SessionRated` — and keeps its own projection updated.

**Calls:** `BookingApi.openHoursOf`, `SkillsApi.enabledSkillsOf`, `IdentityApi.requireUser` for
the tutor's name, `ReputationApi.standingOf` — only while updating the projection, never to answer a
search. `IdentityApi.requireTenant` for the university's time zone.

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/search/offers` | student — by skill and date range |

It answers from `matching.available_offers` alone. If the answer needs a join with another schema,
the projection is missing a column. When several tutors are free at the same hour, it returns all of
them with their standing, and the student chooses.

The search takes `catalogItemId`, `from` and `to` (ISO 8601 UTC, `[from, to)`), `page` and `size`.
It returns only hours that have not started, ordered by time and, at the same hour, by average with
new tutors last; the student's own hours are left out. When nothing falls inside the window it
returns the closest hours outside it, as one page, with `exactMatch: false`. The answer carries the
university's `timezone`, in which the client shows the UTC instants.

A hold does not remove an offer. It lasts five minutes and ends either in a booking, which
`BookingConfirmed` removes, or back in circulation; booking checks the hour again when it is held and
when it is booked, so a stale offer costs a refusal, never a double booking.

---

## sessions

Owns the live session and the evidence that it happened.

```java
public interface SessionsApi {
  SessionView requireSession(UUID sessionId);
  List<SessionSummary> completedSessionsOf(UUID tutorId);   // used by recognition
}
```

**Publishes:** `SessionStarted`, `SessionCompleted`, `SessionUnverified`, `PresenceCodeIssued`.
**Listens to:** `BookingConfirmed` — that is what creates the session.

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/sessions/{id}` | participant |
| POST | `/api/v1/sessions/{id}/join` | participant — only from fifteen minutes before |
| POST | `/api/v1/sessions/{id}/presence` | participant — submits the six digit code |
| POST | `/api/v1/sessions/{id}/end` | participant |
| GET/PUT | `/api/v1/sessions/{id}/whiteboard` | participant |

**The presence check.** Five minutes after the start, a code is issued to each participant and sent
by email. Whoever does not confirm makes the session end as `UNVERIFIED`: the tutor earns nothing,
the student is refunded and the audit is told. The code is stored hashed, expires and caps attempts.

Only participants may read a session. Anyone else gets a refusal, link or no link.

---

## recognition

Owns the request and the university's decision. **Ayni certifies nothing by itself.**

**Publishes:** `RecognitionRequested`, `RecognitionResolved`.
**Calls:** `SessionsApi.completedSessionsOf`, `WalletApi.earnedTotal`, `ReputationApi.standingOf`.

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/recognition/progress` | student — hours so far against what is required |
| POST | `/api/v1/recognition/requests` | student |
| GET | `/api/v1/recognition/requests/mine` | student |
| GET | `/api/v1/coordinator/recognition/requests` | coordinator — queue with audit flags |
| GET | `/api/v1/coordinator/recognition/requests/{id}` | coordinator — the full file |
| POST | `/api/v1/coordinator/recognition/requests/{id}/decision` | coordinator |

**Credits are not consumed.** What a request consumes are the sessions: each one can back exactly
one request, enforced by `UNIQUE (tenant_id, session_id)`. The figures are copied at submission so
the file does not move while it is being reviewed.

---

## reputation

```java
public interface ReputationApi {
  Optional<TutorStandingView> standingOf(UUID tutorId, UUID catalogItemId);
}
```

**Publishes:** `SessionRated`.
**Calls:** `SkillsApi.isTutorEnabledFor` — to tell a new tutor from one who does not teach the skill.
**Listens to:** `SessionCompleted` — opens the rating window.

| Method | Path | Who |
|---|---|---|
| POST | `/api/v1/sessions/{id}/rating` | participant |
| GET | `/api/v1/tutors/{id}/standing?catalogItemId=` | student |

`GET /api/v1/tutors/{id}/standing` requires `catalogItemId`, because standing is per skill. A tutor
enabled for that skill who never taught it has no row yet and answers `200` with 0 sessions, 0
ratings and no average: a new tutor, as matching shows them. It answers `404` only when the tutor
has no standing and `SkillsApi.isTutorEnabledFor` says they do not teach that skill in the
university. A missing `X-Tenant-Id`, a missing `catalogItemId` or an id that is not a UUID answer
`400`.

The student rates with stars and tags. The tutor answers three facts: punctual, connection held,
session flowed. One rating per session and per direction. Below three ratings there is no average,
only a new tutor mark. Standing is per skill, never overall.

---

## notifications

**No published interface, and nothing depends on it.** It only listens.

Reacts to `AccessRequested`, `BookingConfirmed`, `BookingCancelled`, `PresenceCodeIssued`,
`CreditsExpiring`, `RecognitionResolved`, `ValidationResolved`, and whatever is added later.

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/notifications` | signed in — own notices |

The row is written before the email leaves, and a failure is recorded. Two things depend on delivery
working: getting in, and confirming presence.

**Built so far:** `AccessRequested`, the email with the access link (US38). The notice is committed
before the email leaves and then marked sent or failed. Its payload records what the link was for and
when it expires, never the link: identity keeps only the token's hash and the email is the only
copy. A failed access link is not retried, because there is nothing stored to retry it with and it
expires in minutes; asking again issues a new one. Email goes over SMTP (`spring.mail.*`): to Mailpit
under `docker compose`, which shows it at http://localhost:8025, and to a real relay in a deployment.
The rest of the events above, and `GET /api/v1/notifications`, are still to do.

---

## audit

Records what happened and detects what does not add up.

**Listens to:** everything worth keeping, plus a scheduled analysis that raises anomalies.

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/coordinator/anomalies` | coordinator |
| POST | `/api/v1/coordinator/anomalies/{id}/dismiss` | coordinator |

Append only, like the ledger, and for the same reason.

---

## analytics and payments

Not in the first sprint. `analytics` answers `/api/v1/coordinator/indicators` and
`/api/v1/admin/overview`, always with aggregates. `payments` handles purchases with an idempotency
key so a provider confirming twice credits once.

---

## Definition of done

A pull request is finished when all of this is true:

- [ ] The migration matches `database/data-model.md`, in its own file with a timestamp name
- [ ] The entity maps to that table, with `schema = "<module>"`
- [ ] Every query filters by `tenant_id`
- [ ] The use case holds the transaction, and the controller only calls the use case
- [ ] The endpoint appears in Swagger with its summary and example
- [ ] There is a unit test for the domain rule, without Spring
- [ ] There is a `.feature` file with the story's acceptance criteria in Gherkin
- [ ] `./mvnw verify` passes, `ModularityTest` included
- [ ] `docker compose up` works
- [ ] Nothing imports another module's inner packages
