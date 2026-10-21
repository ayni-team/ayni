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

A query without that filter is still a mistake, and since TS01 the database refuses it: it answers
nothing instead of another university's rows (see "Row level security" below).

**Time.** Inject `java.time.Clock` and read time from it. Never `Instant.now()` inside domain or
application code: a rule that depends on the clock cannot be tested if it reads it statically.

**Errors.** Throw a domain exception from the domain; map it in a `@RestControllerAdvice` inside
your module. Every error answers with the same shape: `timestamp`, `status`, `error`, `message`,
`path`.

**HTTP conventions.** Paths start with `/api/v1`. Dates travel in ISO 8601 UTC. Lists that can grow
accept `page` and `size`. Every endpoint carries its `@Operation` summary so Swagger is useful.

**Status codes.** Use the same meaning in every module, so the client can react without reading the
message:

| Status | When |
|---|---|
| 400 | The request is malformed: a missing header, a field that is not a UUID, a code that is not six digits |
| 401 | No session token, or one that is unknown, expired or revoked. Answered by the access filter before any controller runs (TS03) |
| 403 | The person is not allowed: a role that is not the route's (the access filter), not a participant, not the owner |
| 404 | It does not exist **in this university**. Another university's row is a 404, never a 403 |
| 409 | Well formed, but the current state refuses it: a room not open yet, a session already closed |
| 422 | Well formed, but the value is wrong and the attempt counts: a wrong presence code |

---

## Lessons from the first iteration

Each rule below comes from a mistake that reached a pull request or `develop`. They are here so the
second iteration does not pay for them again.

**Events and interfaces**

- **An event needs a publisher, not only listeners.** `HoursWithdrawn`, `BookingCancelled`,
  `SkillWithdrawn` and `SessionRated` had listeners while nothing published them; until
  `HoursWithdrawn` was published, the search kept offering hours a tutor had paused. When you add a
  listener, say in the pull request who publishes the event and whether that is done. When you
  finish a story, check that every event it promises is published:
  `grep -rn "new YourEvent(" backend/src/main`.
- **A published interface must be implemented before anyone injects it.** `SessionsApi` existed
  with no implementation, and the first module to inject it would have stopped the application from
  starting. Whoever first needs an `*Api` implements it in the same pull request, or asks its owner
  first.
- **A projection is proven from the source event.** The first matching branch only had listeners
  that delete offers, so the search was always empty. Test a projection by publishing the real
  events, never by inserting rows by hand.
- **Do not change an `*Api` or an event in `shared.events` on your own.** Other modules compile
  against them. Propose the change in the pull request and wait for the lead.

**Database**

- **A migration is never edited once it is on `develop`**, and its timestamp is later than every
  migration already there. One branch named its migration with an older timestamp; Flyway runs
  without `outOfOrder`, so it would have broken every local database. Name it
  `V<yyyyMMddHHmm>__<module>_<what>.sql` with the current date and time.
- **The table is the one in `database/data-model.md`.** If it has to differ, change the data model
  in the same pull request and say why. A table with other column names, a surrogate key the model
  does not have, or a `UNIQUE` that forbids a valid case all came up in review.
- **Never depend on the JVM time zone.** Store instants in UTC (`timestamptz`), and read a tutor's
  local times with the university's zone, never `ZoneId.systemDefault()`. Declared availability
  once depended on the zone of the machine that ran the backend.

**Concurrency and transactions**

- **If two requests can race, prove the fix with a test that fails without it.** Two clicks on an
  access link could consume it twice. Without a lock, two participants joining at once would publish
  `SessionStarted` twice, and two ending at once would never close the session. The fixes were a
  pessimistic lock (`@Lock(PESSIMISTIC_WRITE)` on a `lockBy...` query) or a conditional
  `UPDATE ... WHERE` that returns how many rows it changed. The test starts both requests behind a
  `CyclicBarrier`, and the pull request says it fails when the lock is removed.
- **A refusal that must be remembered cannot roll back.** A wrong presence code counts an attempt
  and answers an error; a normal `@Transactional` would roll the attempt back and the cap would
  never be reached. Use `@Transactional(noRollbackFor = TheException.class)` and test that the
  counter moved.
- **Money moves exactly once.** Every listener that grants, charges or refunds must be idempotent:
  the same event delivered twice changes nothing the second time. Test it by publishing it twice.

**Listeners and jobs**

- **Listeners run after the publisher commits, on the same thread.** Publish the event inside the
  use case's transaction; a rolled back use case then publishes nothing. In tests, publish inside a
  `TransactionTemplate`, or nothing reaches the listener. Bind the university with
  `TenantContext.runAs(event.tenantId(), ...)` before doing anything.
- **Other modules' listeners run in your tests too.** When your test publishes an event, every
  listener of every module reacts. Stub the published interfaces those listeners call
  (`IdentityApi.requireUser` was the usual one), or the log fills with swallowed exceptions that
  hide a real failure.
- **A scheduled job runs one university at a time and one item per transaction.** Loop over
  `IdentityApi.activeTenantCodes()`, bind each with `TenantContext.runAs`, and give each item its
  own transaction so one failure does not hold back the rest. Its delay is a property
  (`ayni.<module>.<job>-delay`), set to `PT24H` in `src/test/resources/config/application.properties`
  so it never races a test; the test calls the use case the job calls.

**Tests**

- **Do not mock what holds the rule.** A test that mocked `Session` stopped testing anything once
  the booked hours rule moved into the entity. Build real entities; mock only other modules' `*Api`.
- **Each scenario of the `.feature` names the test that runs it**, with the same display name.
  A scenario that is not built yet stays in the file tagged `@pending` and is listed in the pull
  request under "Still pending", so nothing disappears silently.
- **Integration tests use the module's own Testcontainers database** (`<Module>TestDatabase`, one
  static container), and the time is read from the injected `Clock`.

**Secrets and personal data**

- An access link or a presence code is stored only as a hash and travels only by email. Neither it
  nor an email address ever goes to a log, an event that is persisted, or a notice's payload.

**Tooling**

- Spring Modulith builds documentation from the Javadoc, and a quoted sentence that contains a comma
  breaks its parser, which fails `ModularityTest`. Paraphrase instead of quoting.
- Run `cd backend && ./mvnw verify` with Docker running before opening the pull request. A red CI is
  not reviewed.

---

## Iteration 2

The first iteration built the path a tutoring session follows: search, book, meet, prove presence,
close and pay. This one completes the product around it and adds the two quality attributes
[ADR 0004](adr/0004-start-simple-add-quality-attributes-per-iteration.md) deferred: access control
and tenant isolation in the database.

### Who owns what

Riskier work, money and changes that cross modules, goes to whoever has shipped the most so far.
New, isolated modules and read only endpoints go to the rest, so a mistake there cannot break a
booking or a balance.

| Owner | Modules | Stories |
|---|---|---|
| Rodrigo López | cross-cutting, sessions (real time) | TS03 access control, US08 video room (Jitsi), US10 + TS08 whiteboard, US12 review a past session, TS01 row level security, TS09, review of every pull request |
| Daniel Aquino | booking, sessions (no-shows), payments | US05 cancel, US06 + US09 no-shows and `ABANDONED`, the rest of US22 and US19, US26 credits about to expire, presence code resend. Second stage: US35, US36, US37, US31, TS06 |
| Juan Sánchez | identity, academic system mock | ADR 0006 and the mock academic system, activation (US38 scenario 3) publishing `StudentActivated`, US25 initial credits, TS10 universities, coordinator invitations, US50, US52, US39, US41 |
| Diego Bautista | skills, recognition | US18, US16, US42, US43, US44, US51, and the recognition module: US27, US28, US29, US30 |
| Francisco Uribe | reputation, notifications | US32, US34, US33, the remaining notices and `GET /api/v1/notifications` |
| Luis Pillaca | audit, analytics, read only queries | audit module (US48, US47, closing US11 scenario 5 and US54 scenario 3), US53, US04, US21, US07, TS07 |

The Trello card of each story says what is left of it.

### Order and dependencies

1. **TS03 first**, because every endpoint changes who it trusts. `CurrentUser.require()` keeps its
   signature: controllers do not change, only where the identity comes from. **Built:** see
   "Access control" below. Tests keep sending `X-User-Id`, accepted only by a test filter.
2. **ADR 0006 before activation.** US25 and US38 scenario 3 depend on the mock academic system, and
   wallet's initial grant depends on `StudentActivated`.
3. **US05 before the notices of cancellation**, US26 before the notice of expiring credits, US32
   before anything that reads real ratings.
4. **Audit listens to what exists.** `SessionUnverified` and `SessionCompleted` are already
   published; audit can start today.
5. **Recognition reads what exists.** `SessionsApi.completedSessionsOf` and `WalletApi.earnedTotal`
   are implemented; recognition can start today.

### Decisions still open

- How access works (TS03): **decided**, no longer open. See
  `adr/0007-access-control-and-tenant-isolation.md`.
- The mock academic system (ADR 0006): **decided**, and amended. One service for several
  universities that finds a student by university and institutional email and answers the student
  code with the profile; Ayni neither derives the code from the email nor checks its format. See
  "The academic system" under identity.

### Guardrails

- Stay inside your module. Touching another module's code, tables or published contracts is agreed
  with its owner first.

### Access control (TS03)

ADR 0007, its part about requests. Every request proves who it is with the session token that
`POST /api/v1/access/confirm` returns, sent as `Authorization: Bearer <token>`. Identity's
`AccessControlFilter` looks the session up by the hash of the token in `identity.user_sessions`,
one row by index, and binds its university to `TenantContext` and its person to `CurrentUser`.
**`X-Tenant-Id` and `X-User-Id` are no longer read by the application**: the university of a request
is the one of its session, and nobody can name another. Wherever a table below says that a missing
`X-Tenant-Id` or `X-User-Id` answers `400`, the application now answers `401` before the endpoint
runs, because there is no session; the `400` of the use case stays for the tests.

**Who may call what, by route group** (`RouteAccess`):

| Routes | Who |
|---|---|
| `/api/v1/access/request`, `/api/v1/access/confirm`, `GET /api/v1/access/university`, Swagger (`/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`), `/actuator/health` | anyone, without a session |
| `/api/v1/admin/**` | `PLATFORM_ADMIN` |
| `/api/v1/coordinator/**` | `COORDINATOR` |
| `GET /api/v1/me`, `GET /api/v1/university`, `/api/v1/notifications/**` | a student or a coordinator: whoever is signed in into a university |
| `POST /api/v1/access/logout`, anything outside `/api` | any session |
| every other `/api` route | `STUDENT` |

The role of a student or a coordinator is the `role` of their `identity.users` row; a platform
administrator is a row of `identity.platform_admins`. The filter decides by role only: ownership,
and whether the person is active or restricted, stay in the use cases, which are the ones that know
whether to write a refusal down (US48). A `.` or `..` segment in the path is refused with `400`
before it is matched.

| Status | When |
|---|---|
| 401 | No `Authorization: Bearer` header, a token nobody issued, a session that expired (its `expires_at` instant included) or was revoked, or a session whose person no longer exists or whose administrator was disabled. `WWW-Authenticate: Bearer` |
| 403 | A session of a role the route does not accept. The message names the roles it does |

Either way the controller is never reached, so nothing is executed and nothing is read on behalf of
the request. Both answers have the shape of every error. A CORS preflight (`OPTIONS`) passes.

**The platform administrator** signs in by link like everybody else. `POST /api/v1/access/request`
with the address of an active `identity.platform_admins` row issues a `LOGIN` link with no
university (`AccessRequested.tenantId` is `null`), and confirming it opens a session with `admin_id`
and no `tenant_id` nor `user_id`. Nothing binds a university for them, so they see no tenant scoped
rows; `CurrentUser` is the administrator's id. `DemoPlatformAdminData` seeds one,
`admin@ayni.local`, under the `dev` profile only.

**Signing out.** `POST /api/v1/access/logout` revokes the session of the token it carries
(`revoked_at`) and answers `204`; from then on that token is answered `401`.

`POST /api/v1/access/confirm` answers `role` too: `STUDENT`, `COORDINATOR` or `PLATFORM_ADMIN`,
and `tenantId` is absent for an administrator. Swagger UI declares the session token as the
`bearer` scheme on every route but the two public access ones, and no longer lists the identity
headers some controllers still document by hand.

**In tests.** The scenarios written before TS03 send `X-Tenant-Id` and `X-User-Id`. A filter that
exists only in `src/test` (`pe.ayni.config.IdentityHeadersTestFilter`) binds them when a request
carries no `Authorization` header and marks the request as already seen by `AccessControlFilter`,
so the scenarios keep proving their use case. New tests of access control set
`ayni.test.identity-headers=false` and use real tokens, and so does every test written since:
`pe.ayni.config.SignedInPeople` (in `src/test`) creates a university and the people of it who signed
in, each with a real session token for `Authorization: Bearer`.

**Not built.** A deliberately public resource, the verification of a certificate, has no route
because Ayni issues no certificates yet; its scenario is `@pending` in
`TS03-access-control.feature`.

### Extracting a module (TS09)

Extracting a module must be a migration, not a redesign (ADR 0002). The four scenarios of
`TS09-module-extraction.feature` are verified like this:

| Scenario | Verified by |
|---|---|
| Isolation in the code | `ModuleExtractionTest` (and `ModularityTest`): Spring Modulith finds no module using another's internal classes, only published interfaces and the events of `shared.events` |
| No foreign key crosses between the schemas of two modules | `SchemaBoundaryTest`: migrates an empty PostgreSQL and reads `pg_constraint`; a second test adds such a key inside a transaction and proves the check names it |
| Verification on every integration | CI runs `./mvnw -B verify` on every pull request to `develop` and `main`; `ModuleExtractionTest` analyses a fixture in the test sources where one module reaches into another and asserts the violation names both and the class |
| The scope of a change is bounded | `ModuleExtractionTest`: every dependency from one module to another targets the root package of the other (its `*Api` and views), so nothing below that root can break another module |

### Row level security (TS01)

ADR 0007, its part about the database. Every table with `tenant_id` has row level security and one
policy, `tenant_isolation`, which compares `tenant_id` with `app.tenant_id`, the university of the
transaction. Four tables are exempt, as the ADR says: `identity.tenants`,
`identity.platform_admins`, `identity.access_links` and `identity.user_sessions`. Two policies
accept a `NULL` `tenant_id`: the global items of `skills.catalog_items`, which are every
university's, and the notices of `notifications.notifications` that belong to no university (an
administrator's access link), seen only from a transaction without a university.

**Per transaction, never per connection.** `config.TenantTransactionManager` is the application's
transaction manager: every transaction begins with `set_config('app.tenant_id', <university>, true)`,
the function form of `SET LOCAL`, from `TenantContext`. When `TenantContext` changes while a
transaction is open (a listener binding the university of its event, a job binding one university
after another), what the transaction wrote so far is flushed and the value is set again. A
transaction without a university sets it empty, and every tenant scoped table answers nothing.

**Every repository call runs in a transaction** (`config.TransactionalRepositoriesConfig`), read only
unless it joins one. Spring Data alone leaves query methods declared on an interface without one,
and a query on a connection with no university returns nothing even with the university bound.

**Two roles.** Flyway migrates as the owner (`AYNI_DB_USER`, `ayni`); the application connects as
`AYNI_DB_APP_USER` (`ayni_app`), which owns no table, is no superuser and has no `BYPASSRLS`.
`V202610150020__tenancy_enable_row_level_security.sql` creates that role when it does not exist,
with the password of the Flyway placeholder `app_password` (`AYNI_DB_APP_PASSWORD`), grants it the
tables of the twelve schemas and the ones created later, and does not let it `UPDATE` or `DELETE`
`wallet.ledger_entries` and `audit.activity_log`. `docker compose` sets both; the values are for
development only.

**What this means for your code.**

- **A new table with `tenant_id` needs, in its own migration, `ALTER TABLE ... ENABLE ROW LEVEL
  SECURITY` and its `tenant_isolation` policy**, like the ones of the migration above.
  `RowLevelSecurityPolicyTest` fails the build otherwise.
- **Keep filtering by `tenant_id` anyway.** The policy is the net, the filter is the query: it uses
  the indexes and says what the code means.
- **Bind the university before you read or write**, whenever the work does not come from a request.
  A job loops over `IdentityApi.activeTenantCodes()` and binds each one with `TenantContext.runAs`
  or `TenantContext.callAs`, also to list what it will process: a query over every university at
  once answers nothing. The administrator has no university, so whatever it writes for one, the
  baseline policy of a new university or an invited coordinator, is written inside `runAs` of that
  university, and its listing counts each university as itself. `IdentityApi.currentPolicy` names its
  university and reads as that one.
- **What crosses universities on purpose and was adapted**: the payments reconciliation lists the
  pending purchases of each university as itself, and the wallet expiry job finds the universities
  with work by asking each active one, instead of one query over all of them.
- A listener with `@ApplicationModuleListener` keeps binding the university of its event with
  `TenantContext.runAs(event.tenantId(), ...)`, which now also reaches the database.

**In tests.** Every integration test connects as `ayni_app` too
(`pe.ayni.config.ApplicationRoleTestConnection` replaces the user of the `@ServiceConnection`), and
Flyway runs as the container's user (`spring.flyway.user=test` in
`src/test/resources/config/application.properties`). The fixtures written before TS01 prepare and
check rows straight in the tables without naming a university, so
`pe.ayni.config.FixtureOwnerTestDataSource` hands the owner's connection to a test class and the
application's role to every class of the application, even one a test calls directly. A test about
isolation itself reads as the application with `FixtureOwnerTestDataSource.asApplication`. The
scenarios are in `TS01-row-level-security.feature`.
- A story that moves credits (charge, grant, refund) is reviewed with extra care and needs an
  idempotency test.
- One pull request per story, small enough to review in one sitting. A story too big for that is
  split into tasks with the same `US` number.

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
| POST | `/api/v1/access/logout` | signed in | revokes the session of the token it carries (TS03) |
| GET | `/api/v1/access/university?email=` | anyone | name, logo and colours of the university that claims the domain of the email (US39) |
| GET | `/api/v1/university` | student, coordinator | the university of the session, with its email domains and time zone (US39) |
| GET | `/api/v1/me` | signed in | own profile |
| PUT | `/api/v1/me/profile` | student | photo and description only. Name, career and term come from the academic system and are not editable |
| POST | `/api/v1/me/academic-record/import` | student | refreshes the approved courses from the academic system; an onboarding step (US40) |
| POST | `/api/v1/admin/universities` | admin | registers a university |
| GET | `/api/v1/admin/universities` | admin | list with counts, never with people |
| PUT | `/api/v1/admin/universities/{code}/email-domains` | admin | replaces the whole list of email domains of a university (US50) |
| POST | `/api/v1/admin/universities/{code}/coordinators` | admin | invites a coordinator by email: creates them as pending and sends a single use link; opening it activates them (TS10) |
| PUT | `/api/v1/coordinator/credit-policy` | coordinator | supersedes the baseline credit policy of their university: new students are granted the new one (US52) |
| PUT | `/api/v1/coordinator/university-identity` | coordinator | sets the logo and the colours of their own university (US50) |

The administrator endpoints must never return a student's name, email or courses. That restriction
is the reason `platform_admins` is a separate table.

**The academic system (ADR 0006).** Identity asks `AcademicSystemPort` for the profile of a
student by the university and the institutional email, at activation (US38) and when the record is
imported (US40). The profile carries the student code the university assigned, the name, the
career, the term and the approved courses with their grades; the code is copied into
`identity.users.student_code` as it comes. The simulated adapter, `MockAcademicSystemAdapter`,
reads one file per university, `<CODE>.json`, from the folder `ayni.identity.academic-directory`
(`AYNI_ACADEMIC_DIRECTORY`, `academic-directory` relative to where the backend runs by default),
**each time it looks a student up, never at startup**. `backend/academic-directory/` holds the
four demonstration students (Ana, Bruno and Carla of UPC, Lucia of PUCP) and a `README.md` with the
format, and `docker-compose.yml` mounts it into the backend at `/app/academic-directory`. Bringing
a university in is registering it and putting its file in the folder: no restart, no code.

| What happens | Answer of the activation |
|---|---|
| The university has no file in the folder | `400`, the university has no academic system configured; the link stays unused |
| The file has no student with that email, whatever its case | `400`, the student was not found in the academic system; the link stays unused |
| The file cannot be read | `500`: a mistake of the platform, written to the log with the university's code and nothing else |

**Registering a university (TS10).** `POST /api/v1/admin/universities` takes the code, the name, the
colours, the email domains, the minimum teaching grade, the time zone, and the initial credits with
their validity in days. In one transaction it creates the university as `ACTIVE`, creates its
initial `BASELINE` credit policy with those credits, authored by the administrator and valid from
the registration date in the university's own zone, and publishes `UniversityRegistered`. The code
is kept in upper case and the domains in lower case. Nothing is published when it is refused.

| Status | When |
|---|---|
| 201 | Registered. The body is the university with `studentCount` and `coordinatorCount` at 0 |
| 400 | A field is missing or wrong: no domain, a grade outside 0 to 20, a time zone that does not exist, credits or validity below 1, a missing `X-User-Id` |
| 409 | The code or one of the email domains already belongs to a university, whatever its case or status |

Registering takes a database lock before it looks for the code and the domains, so two requests for
the same code, or the same domain, end with one `201` and one `409`, never a `500`.

`GET /api/v1/admin/universities` answers `200` with each university's configuration, whether it is
active and how many students and coordinators it has, and nothing else about its people.

**Inviting a coordinator (TS10).** `POST /api/v1/admin/universities/{code}/coordinators` takes the
`email` and the `fullName`. In one transaction it creates the coordinator as `PENDING`, with no
student code and no onboarding, stores a single use `COORDINATOR_INVITE` link (only its hash) and
publishes `AccessRequested`, which notifications turns into the email. The address must belong to
one of the university's domains. Nothing is published when it is refused.

| Status | When |
|---|---|
| 201 | Invited. The coordinator exists as `PENDING` and the link is on its way |
| 400 | A field is missing, the email is not one, it is not of a domain of the university, or `X-User-Id` is missing |
| 404 | The university does not exist |
| 409 | The university is suspended, or the address already has a user in it, pending or active |

Inviting takes the university's row first, so two invitations to the same address at once end with
one `201` and one `409`, never a `500`.

**Accepting the invitation.** The link goes to `POST /api/v1/access/confirm`, as every other one.
For a `COORDINATOR_INVITE` link it consumes the link, activates the coordinator, publishes
`CoordinatorActivated` and opens their first session; the student activation of US38 is not
touched.

| Status | When |
|---|---|
| 200 | Activated, with the session |
| 400 | The token is unknown, or the link expired |
| 403 | The account the link points to is not a coordinator |
| 404 | The account the link points to no longer exists |
| 409 | The invitation was already used, also by a confirmation that arrived at the same time |

The link is consumed with a conditional update, so two confirmations at once activate the
coordinator once, publish `CoordinatorActivated` once and open one session: the other one is
answered `409`.

**Credit policy (US52).** `PUT /api/v1/coordinator/credit-policy` takes `creditsAmount` and
`validityDays`, both of at least 1. In one transaction it closes the current `BASELINE` policy of
the university of the request (`superseded_at`) and creates the next one, authored by the
coordinator and valid from today in the university's own zone. The previous version stays stored:
policies are never edited. `IdentityApi.currentPolicy` answers the new one from then on, so the
students activated afterwards are granted it; what was granted before keeps the policy that granted
it. The body is the new policy: `id`, `kind`, `creditsAmount`, `validityDays` and `validFrom`.

| Status | When |
|---|---|
| 200 | Replaced |
| 400 | `creditsAmount` or `validityDays` below 1, or a missing `X-Tenant-Id` or `X-User-Id` |
| 403 | The person is not an active coordinator of that university: a student, a coordinator who is restricted, or someone who does not belong to it |
| 409 | The university is suspended, or it has no baseline policy to replace |

The university's row is locked before the current policy is read, so two coordinators replacing it
at once are answered `200` both, one after the other, and exactly one `BASELINE` policy stays
current, never two and never a `500`.

**University identity (US50).** `PUT /api/v1/coordinator/university-identity` takes `logoUrl`,
`primaryColor` and `secondaryColor`, each optional and replaced as a whole: a field that is missing
or blank leaves the university with none. The university is the one of the session; the body has
no field for it and a code that appears there is ignored. It answers the university's `code`,
`name` and the three values as they are stored.

| Status | When |
|---|---|
| 200 | Updated |
| 400 | A value is longer than its limit (512 characters for the logo, 16 for a colour) |
| 403 | The person is not an active coordinator of that university: a student, a coordinator who is restricted, or someone who does not belong to it |

**Seeing the university before signing in (US39).** `GET /api/v1/access/university?email=<address>`
is public. It looks at the domain of the address only, the way `POST /api/v1/access/request` does
(`IdentityApi.findTenantByEmailDomain` resolves the same university: the active one that claims the
domain), and answers `code`, `name`, `logoUrl`, `primaryColor` and `secondaryColor` of that
university, so a person knows it is the right one before asking for the link. It says nothing
about whether the address has an account and nothing about people: an address with an account and
one without answer alike.

| Status | When |
|---|---|
| 200 | An active university claims the domain |
| 400 | `email` is missing, blank or not an address: it needs exactly one `@` with something on each side and no spaces |
| 404 | No active university claims the domain. A suspended university is answered exactly as one that is not affiliated |

**Reading my university (US39).** `GET /api/v1/university` answers a student or a coordinator the
same as above plus `emailDomains` and `timezone`. The university is the one of the session and the
request cannot name another. A platform administrator belongs to none: the access filter answers
`403` before the controller runs, and `401` answers a request without a session.

**Editing the email domains (US50).** `PUT /api/v1/admin/universities/{code}/email-domains` takes
`emailDomains`, the complete list the university will be recognised by from then on, and answers
the university's `code`, `name` and `emailDomains`. Only a platform administrator edits it (the
route is under `/api/v1/admin`): the coordinator of the university cannot, by decision. The domains
are trimmed, kept in lower case and without repeats, in the order they came.

| Status | When |
|---|---|
| 200 | Replaced. Keeping a domain the university already has is not a conflict |
| 400 | The list is empty or missing, or a domain is blank or has an `@` or spaces |
| 404 | The university does not exist |
| 409 | A domain already belongs to another university, active or suspended |

It takes the same database lock as registering a university before it reads the domains of the
others, so two requests for the same domain end with one `200` and one `409`, never a `500`. The
audit event of a change of domains or identity is not published yet (audit, W12).

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
**Listens to:** `StudentActivated` — to enable the courses their grades already justify;
`SessionCompleted` — to count the sessions taught on each catalogue item, which a moderator reviews the
catalogue by (US44). Skills keeps its own count because it cannot ask reputation for it: reputation
already depends on skills, and the modules would depend on each other.

| Method | Path | Who | Does |
|---|---|---|---|
| GET | `/api/v1/catalog` | student | global items plus their university's, filtered by category or text, paged |
| GET | `/api/v1/catalog/similar?name=` | student | up to five catalogue items that look like a name, before proposing a new one (US42) |
| POST | `/api/v1/tutor/skills/proposals` | student | proposes a tool the catalogue does not have. 409 with the similar items until `confirmDistinct` is sent (US42) |
| GET | `/api/v1/tutor/skills/proposals` | student | own proposals with their status and decision (US42) |
| GET | `/api/v1/tutor/skills` | student | own skills with their status |
| GET | `/api/v1/tutor/skills/suggestions` | student | approved courses whose grade clears the threshold and are not offered yet |
| POST | `/api/v1/tutor/skills` | student | offers a skill. A course enables itself if the grade allows |
| DELETE | `/api/v1/tutor/skills/{id}` | student | withdraws it. Confirmed bookings stand |
| POST | `/api/v1/tutor/skills/{id}/validation` | student | submits evidence for a global tool |
| GET | `/api/v1/coordinator/skill-proposals?status=` | coordinator | proposals of the university: the queue by default, the history with APPROVED, MERGED or REJECTED (US43) |
| GET | `/api/v1/coordinator/skill-proposals/{id}` | coordinator | one proposal with the catalogue items it looks like (US43) |
| POST | `/api/v1/coordinator/skill-proposals/{id}/decision` | coordinator | approves (adds a global item), joins to an existing item, or rejects with a reason (US43) |
| GET | `/api/v1/coordinator/catalog/{id}/usage` | coordinator | tutors offering an item and sessions taught on it, counted in the coordinator's university only, for a global tool too; counts, never people (US44) |
| POST | `/api/v1/coordinator/catalog/{id}/retire` | coordinator | retires a course of the university and withdraws every offer of it; the body confirms the number of tutors affected. A global tool answers 403: it is left to the ADMIN role, which does not exist yet (TS03) (US44) |
| POST | `/api/v1/coordinator/catalog/{id}/merge` | coordinator | joins a duplicate course to the one that stays, both of the university: offers, interests, proposals and counted sessions follow, the duplicate is retired. If either is a global tool it answers 403, and nothing changes (US44) |
| GET | `/api/v1/coordinator/academic-catalog/courses` | coordinator | the courses of the university, by course code, retired ones marked (US51) |
| POST | `/api/v1/coordinator/academic-catalog/courses` | coordinator | loads the curriculum: adds new courses, updates names, brings back retired ones, never removes; all or nothing (US51) |
| POST | `/api/v1/coordinator/academic-catalog/courses/{id}/retire` | coordinator | takes a course of the university out: same effect and confirmation as retiring an item; a global tool is not found here (US51) |
| GET | `/api/v1/coordinator/academic-catalog/minimum-grade` | coordinator | the grade a student needs in a course to teach it; the registered one until a coordinator sets it (US51) |
| PUT | `/api/v1/coordinator/academic-catalog/minimum-grade` | coordinator | sets that grade (0 to 20, two decimals); applies to new enablements, the granted ones stay (US51) |
| GET | `/api/v1/coordinator/validations` | coordinator | pending queue |
| GET | `/api/v1/coordinator/validations/{id}/files/{fileId}` | coordinator | opens a file of a submission, always as a download (US16) |
| POST | `/api/v1/coordinator/validations/{id}/decision` | coordinator | approves or rejects with a reason |

A university course is enabled only by grade. A global tool is enabled only by reviewed evidence.

**Evidence for a global tool (US16).** `POST /api/v1/tutor/skills/{id}/validation` is a
`multipart/form-data` request with `files`, the portfolio or the certificate, and an optional
`note` for the reviewer. The first submission creates the offer `PENDING` with a `SUBMITTED`
request; after a rejection the same offer goes back to `PENDING` with a new request, and the
rejected one stays with its reason. Everything that can be refused is decided before a byte is
stored, and what was stored is removed if a later step fails, so a refused submission leaves
neither a row nor a file.

| Limit of one submission | Value | Where it is set |
|---|---|---|
| Files | 1 to 3 | `ayni.skills.evidence.max-files` |
| Size of a file | more than 0 bytes and at most 5 MB | `ayni.skills.evidence.max-file-bytes` (5242880) |
| Kinds | PDF, PNG or JPEG: the declared type, parameters and case aside, **and** the first bytes of the file must say the same | `EvidenceRules` |
| Name of a file | 1 to 255 characters | the column |
| `note` | at most 1000 characters, blank means none | the column |
| The whole request | 5 MB per part, 16 MB in all; beyond that Spring refuses it before the rules are asked | `spring.servlet.multipart` |

| Status | When |
|---|---|
| 201 | Received; it waits in the queue of the university's coordinators |
| 400 | No files, more than the limit, an empty one, one too big, of another kind or whose content is not what it says, a name or a note too long, or the item is a course |
| 404 | The item does not exist or is not visible to the university |
| 409 | Evidence already waits for that tool, or the tool is already enabled |

**Where the files are kept.** `EvidenceStoragePort` with one adapter today, `LocalDiskEvidenceStorageAdapter`,
under `ayni.skills.evidence-dir` (`AYNI_SKILLS_EVIDENCE_DIR`; the temporary folder by default). The
key is `<university>/<random uuid>`: nothing of it comes from the student, and every key is checked
to stay inside the university's folder before it is used. `docker-compose.yml` mounts the named
volume `ayni-skills-evidence` at `/var/lib/ayni/evidence`, so the files survive the container being
replaced; a deployment with several instances needs an object store behind the same port.

**Opening a file.** `GET /api/v1/coordinator/validations/{id}/files/{fileId}` answers the content
with its stored type and length, always as `Content-Disposition: attachment` with the original
name and `X-Content-Type-Options: nosniff`, since a student uploaded it. The file is found through
its submission in the coordinator's university: the identifier of a file alone opens nothing, and
another submission's file or another university's answers `404`, as a file whose content is gone
from the storage does. A student is answered `403`.

**Deciding.** `POST .../{id}/decision` takes `APPROVE` or `REJECT` and a `reason` of at most 500
characters, required to reject because the student reads it. The request is locked while it is
decided (`ResolveValidationConcurrencyTest`), approving enables the offer with `REVIEWED_EVIDENCE`
and publishes `SkillEnabled`, and both publish `ValidationResolved`. Evidence for a tool retired
while it waited cannot be approved (`409`), only rejected.

**Every lookup of a catalogue item or an offer by its identifier names the university.** An item is
read with `findByIdAndTenantVisibility` or `findAllByIdInAndTenantVisibility` (global, or of that
university) and an offer with `findByTenantIdAndIdIn`, in the queries of the tutor's skills, the
pending validations and the moderation, in deciding a validation and in declaring interests. Row
level security is the net; the filter is the query.

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
`BookingCancelled` (refunds the booking, unless the student cancelled it late),
`SessionUnverified` (refunds the booking), `SessionAbandoned` (refunds the booking, but only when
the student was present, which means the tutor was the one missing) and `PurchaseConfirmed`.

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/wallet` | student — balance broken down by origin with expiry dates |
| GET | `/api/v1/wallet/movements` | student — history, filterable by period and type |
| GET | `/api/v1/wallet/benefits` | student — active benefits offered by their university |
| POST | `/api/v1/coordinator/benefits` | coordinator — create a benefit and its earned-credit cost |
| PUT | `/api/v1/coordinator/benefits/{benefitId}` | coordinator — update or deactivate a benefit |
| POST | `/api/v1/wallet/benefits/{benefitId}/redemptions` | student — redeem earned credits |
| POST | `/api/v1/wallet/donations` | student — donate earned credits to the incoming-student pool |
| GET | `/api/v1/wallet/donation-pool` | student — total donated credits and contribution count |
| GET | `/api/v1/wallet/donations` | student — their own donation history |

Redemptions and donations first return an irreversible-operation warning with a confirmation
identifier. The student must send that identifier in a second request within ten minutes. Confirmed
operations are idempotent and debit only `EARNED` credits; receipts and donations are recorded as
immutable wallet-ledger movements. The incoming-student pool is the sum of the donation records for
the university. Spending earned credits does not erase the teaching from recognition progress.
Confirmation challenges are kept for ten minutes and can be used once; update
`ayni.wallet.credit-use-confirmation-validity` to change that window.

**Rules that live here and nowhere else:** spend the group closest to expiring first; a refund
returns credits to the group they came from with the expiry they had; the ledger is append only and
a correction is a new entry; only `SEED` and `ALLOCATED` expire.

The daily expiry job publishes one `CreditsExpiring` event per unspent credit group within the
configured `ayni.wallet.expiry-notice-days` window (seven days by default). Wallet stores the
publication time on the group, so subsequent runs do not publish a duplicate notice. This job only
publishes the event; email delivery in `notifications` is a separate responsibility.

A refund is idempotent, and it has to be. `booking` calls `WalletApi.refund` inside its own
transaction, and the `BookingCancelled` listener above is a net behind that call, not a second one.
The reasoning is in ADR 0005.

The listener of `BookingCancelled` refunds every cancellation except one: it does nothing when the
event is late **and** the student cancelled it themselves. A tutor's cancellation refunds whatever
the hour, and the same event delivered twice refunds once.

The listener of `SessionAbandoned` refunds the booking only when `studentCheckedIn` is true: a
student who was in the room and whose tutor never came gets the credit back. When the student was
the one missing, whether the tutor came or not, there is nothing to return: the hour was theirs to
use. Delivered twice it refunds once, as every
listener that moves credits does, and a session is never both abandoned and unverified, so the
student cannot be refunded by the two events.

---

## booking

Owns availability and reservations.

```java
public interface BookingApi {
  BookingView requireBooking(UUID bookingId);
  boolean isConfirmed(UUID bookingId);   // used by sessions, before it schedules a session
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
**Listens to:** `SessionAbandoned` — marks the booking `NO_SHOW`, and when the student was present
records a `NO_SHOW` incident against the tutor in `booking.tutor_reliability`, which means the tutor
was the one missing.

| Method | Path | Who | Does |
|---|---|---|---|
| POST | `/api/v1/bookings/holds` | student | tutor, start, hours: holds them for five minutes while the need is written |
| DELETE | `/api/v1/bookings/holds` | student | tutor, start, hours as parameters: gives back what the student holds there |
| POST | `/api/v1/bookings` | student | tutor, skill, start, hours, need description |
| GET | `/api/v1/bookings/mine` | student | upcoming and history, a page at a time (US04) |
| DELETE | `/api/v1/bookings/{id}` | student or tutor of the booking | cancels. The student is refunded unless they cancel inside twelve hours of the start; the tutor's cancellation always refunds. `confirmLate=true` confirms a cancellation inside twelve hours. 204, 400, 403, 404 and 409 |
| GET | `/api/v1/tutor/agenda` | tutor | what is booked and what is still free, in days of the university (US21) |
| GET | `/api/v1/tutor/agenda/history` | tutor | the sessions already over or called off, a page at a time (US21) |
| POST | `/api/v1/tutor/availability` | tutor | weekly ranges |
| DELETE | `/api/v1/tutor/availability/{id}` | tutor | removes a weekly range; its free hours are withdrawn, the booked ones stand (US19) |
| POST | `/api/v1/tutor/availability/exceptions` | tutor | add or remove a specific date |
| POST | `/api/v1/tutor/availability/pauses` | tutor | pause a period |
| GET | `/api/v1/tutor/availability/pauses/current` | tutor | whether the tutor is paused today and until when (US22) |
| DELETE | `/api/v1/tutor/availability/pauses/{id}` | tutor | ends a pause early and restores the hours the weekly rules still give (US22) |

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

**Cancelling.** Only the booking's student or tutor may cancel it, before the session starts; the
booking is locked while it is cancelled, so two cancellations at once end with one refund and one
`BookingCancelled`, and cancelling again answers `204` and changes nothing. "Late" is strictly less
than twelve hours before the start; exactly twelve is on time.

| Who cancels | Twelve hours or more ahead | Less than twelve hours ahead |
|---|---|---|
| Student | credits returned, hours `AVAILABLE` | warned first that nothing is returned; once confirmed, cancelled with no refund, hours `AVAILABLE` |
| Tutor | credits returned, hours `RELEASED` | asked to confirm and warned it will be recorded; once confirmed, credits returned, hours `RELEASED` and a `LATE_CANCELLATION` incident in `booking.tutor_reliability` |

The warning is the answer to the first request and cancels nothing: `409` with `late`,
`refundWillBeIssued`, `incidentWillBeRecorded` and `confirmationRequired`. The same `DELETE` with
`confirmLate=true` carries it out. Without the lateness, `confirmLate` changes nothing.

| Status | When |
|---|---|
| 204 | Cancelled, or it already was |
| 400 | A missing `X-Tenant-Id` or `X-User-Id`, or a booking id that is not a UUID |
| 403 | The caller is neither the student nor the tutor of the booking |
| 404 | The booking does not exist in this university. Another university's booking answers the same |
| 409 | The confirmation of a late cancellation is needed, or the session has already started |

Booking asks wallet to refund inside its own transaction (`WalletApi.refund`) and publishes
`BookingCancelled`, which keeps its shape; `late` and `cancelledBy` tell the listeners what
happened. A tutor removing availability over a confirmed booking is cancelling that session.

**A student's tutorings (US04).** `GET /api/v1/bookings/mine` takes `view` (`UPCOMING`, the
default, or `HISTORY`, in any case), `page` and `size` (20 by default, 1 to 100). `UPCOMING` is the
student's `CONFIRMED` bookings whose last hour has not ended, the nearest first, so a session in
progress is still there; `HISTORY` is the rest, over, cancelled or `NO_SHOW`, the most recent first.
It answers `view`, `timezone` (the university's, to show the UTC instants in), `page`, `size`,
`total` and `items`; each item carries `bookingId`, `startsAt`, `endsAt`, `hours`, `status`, `tutor`
and `catalogItem` (each an `id` and a `name`, read from identity and skills, `null` when the other
module no longer knows it) and `joinable`. A booking whose session took place stays `CONFIRMED`:
booking does not hear how a session ended, which is what US12's review answers.

**A tutor's agenda (US21).** `GET /api/v1/tutor/agenda` takes `from` and `to`, ISO 8601 dates of
the university's zone, both included; without `from` it starts today in that zone, and without
`to` it covers a week. It answers `from`, `to`, `timezone` and `lines`, in the order they come: a
`BOOKED` line for each confirmed booking with the tutor that touches the period and has not ended,
once however many hours it covers, with `booking` (`bookingId`, `hours`, `status`, `student`,
`catalogItem`, `needDescription`, `joinable`), and a `FREE` line for each hour nobody booked that
has not started, held ones included as in `openHoursOf`. Withdrawn and released hours are neither
and are not shown. Every line carries its UTC `startsAt` and `endsAt` and the same moment in the
university's zone: `date`, `startTime` and `endTime`, so an hour at 22:00 in Lima is on its own day
although it is the next one in UTC. `GET /api/v1/tutor/agenda/history` is the tutor's half of
`HISTORY`, a page at a time with the same items as `BOOKED` lines. The path is new: the contract
listed only the agenda, while the story asks for what was already taught apart from it.

`joinable` is true from fifteen minutes before the start until the end of the last hour, the window
in which sessions lets the participants in. Both lists only point it out: entering takes the
session's identifier, which sessions keeps and booking cannot ask for, since sessions already
depends on booking. So the screen takes the `bookingId` of the highlighted item to sessions'
`GET /api/v1/sessions?bookingId=` (see "Finding the session of a booking" under sessions), and
joins with the `sessionId` it answers. The scenario of each story, "The highlighted tutoring opens
its session", is no longer `@pending`; its test is in sessions.

| Status | When |
|---|---|
| 200 | A page, or the agenda of the period, possibly empty |
| 400 | `view` is not `UPCOMING` or `HISTORY`, `page` below 0, `size` outside 1 to 100, a date that is not ISO 8601, `to` before `from`, or a period longer than 31 days |
| 401 | No session (TS03) |

Both read only the bookings of the person who asks, in the university of their session: there is no
way to name another student or tutor.

**A tutor no-show (US06).** When sessions abandons a session because a participant did not come,
the booking becomes `NO_SHOW` in every case, and only when the student was present, so the tutor was
the one missing, is a `NO_SHOW` row written to `booking.tutor_reliability`, next to the
`LATE_CANCELLATION` ones, so a tutor's record lives in one table. Nothing is held against the tutor
when both were absent, nor when only the student was. The booking is locked
while it is marked and `UNIQUE (booking_id, kind)` stands behind it, so the event delivered twice
records one incident. A booking cancelled or completed in the meantime is left as it is. Reputation
does not hear about this: standing is built from ratings, not from absences.

**Changing availability reaches the hours that exist.** Hours are generated weeks ahead, so a pause
or a date exception usually arrives after its hours exist. Saving one adjusts them in the same
transaction: every future hour the rules no longer give, free or held, becomes `WITHDRAWN` and
`HoursWithdrawn` takes it out of the search; an `ADD` generates its hours at once. A withdrawn hour
comes back to `AVAILABLE` when the rules give it again, a pause ended early or a range declared
again, and `HoursGenerated` announces it. `RELEASED` is kept for an hour freed by cancelling its
booking, which generation never restores. Which hours the rules give is decided by the same
`BlockGenerator` that creates them. A booked hour stands, as US19
scenario 4 asks: until cancelling exists (US05) removing availability does not cancel, and the
answer counts the booked hours that stand so the tutor knows.

---

## matching

Owns nothing but the search. **No published interface:** it is read only.

**Publishes:** `DemandUncovered`, when a search finds no hour inside the window it asked for (see
"Uncovered demand" below).

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

**Response time (TS07).** Measured on the real search over HTTP, with fifty students at once and a
projection of five universities of 320 courses and 120 tutors each (144,000 offers): p95 about 90 ms
and the slowest about 140 ms on a laptop, 290 ms and 490 ms on two CPUs, against 500 ms and 2 s.
Every query of the search reads `ix_available_offers_search` and none the whole table, so no index
was added. `SearchResponseTimeTest` repeats the measurement in every build and fails it beyond the
thresholds; the method and the figures are in `performance/TS07-search-response-time.md`.

**Uncovered demand (US53).** When nothing falls inside the window, the first page of the search
publishes `DemandUncovered` with the university, the course, the start and the end of the window
and the moment. It names no one: the student is not in it. It is published inside the transaction of
the search, which is read only, and heard once it commits; a search with an exact match publishes
nothing, and neither does a later page of the same empty search. The answer of the search is the
same with or without it. Analytics counts it; matching does not know who listens.

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

`completedSessionsOf` returns only `COMPLETED` sessions, the earliest scheduled first: an
`UNVERIFIED` one earns the tutor nothing and backs no recognition. Its `hours` are the hours that
were booked, one credit each, not the minutes the call lasted.

**Publishes:** `SessionStarted`, `SessionCompleted`, `SessionUnverified`, `SessionAbandoned`,
`PresenceCodeIssued`, `WhiteboardClosed`.
**Calls:** `BookingApi.requireBooking` — for the need description the tutor reads before the session,
for the support material prepared from it (US07), and for the skill that `SessionAbandoned` names; `IdentityApi.activeTenantCodes` — for the jobs
that look at every university.
**Listens to:** `BookingConfirmed` — that is what creates the session.

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/sessions?bookingId={uuid}` | participant — the session of a booking (US04, US21) |
| GET | `/api/v1/sessions/{id}` | participant |
| POST | `/api/v1/sessions/{id}/join` | participant — only from fifteen minutes before |
| POST | `/api/v1/sessions/{id}/presence` | participant — submits the six digit code |
| POST | `/api/v1/sessions/{id}/end` | participant |
| GET/PUT | `/api/v1/sessions/{id}/whiteboard` | participant |
| GET | `/api/v1/sessions/{id}/review` | participant — a session of their history, as it was left (US12) |
| WebSocket | `/api/v1/sessions/{id}/whiteboard/live` | participant, while the session is in progress |

**Joining.** `GET /sessions/{id}` shows a participant the session and the student's need, read
through `BookingApi.requireBooking`, and never the room name. `POST /sessions/{id}/join` hands the
room name over, from fifteen minutes before the start until the scheduled end, and records the
participant's first arrival in `sessions.participations`. Entering does not start the session
(US09): it moves to `IN_PROGRESS` and publishes `SessionStarted`, once, when both participants have
entered **before ten minutes have passed since the scheduled start**, and its `startedAt` is the
moment the second of them arrived. Somebody who arrives at the ten minute mark or later is late: the
arrival is recorded, nothing starts, and the session is abandoned as the next section says. The
session is locked while it is joined, so two participants arriving at once start it once. Joining
again changes nothing, the first arrival included. A stranger gets 403, a room that is not open 409.

**Finding the session of a booking (US04, US21).** The lists of tutorings, a student's
(`GET /bookings/mine`) and a tutor's (`GET /tutor/agenda`), mark a booking as `joinable` but cannot
name its session. `GET /api/v1/sessions?bookingId={uuid}` answers it: `sessionId`, `bookingId`,
`status`, `joinOpensAt` and `scheduledEnd` (UTC, named as in `GET /sessions/{id}`, except that the
session's own identifier is `sessionId`, as in the answer of joining), and never the room name,
which only joining hands over. The session is looked up by university and booking
(`sessions.sessions` has `UNIQUE (booking_id)`) and the same rule as reading a session decides who
may ask: the student and the tutor of it, `Session.roleOf`. It is `FindSessionOfBookingQuery`; no
module depends on another for it.

| Status | When |
|---|---|
| 200 | The session of the booking |
| 400 | `bookingId` is missing or is not a UUID |
| 401 | No session token (the access filter) |
| 403 | The person is neither the student nor the tutor |
| 404 | The booking has no session in this university, another university's included |

**The video call (US08, scenario 2).** Joining also answers `video`: a pass to the session's call
in the self hosted Jitsi, `url` (the room, `ayni.sessions.video.url` plus the room name), `token`
and `expiresAt`. The token is a JWT signed with HS256 and `AYNI_JITSI_JWT_SECRET`, the secret Jitsi
shares (`AUTH_TYPE=jwt`): `room` is the session's room and no other, `iss` `ayni`, `aud` `jitsi`,
`sub` `meet.jitsi`, `context.user` the participant, moderator for the tutor. It lasts
`ayni.sessions.video.token-ttl` (five minutes) and never past the scheduled end; Jitsi checks it
when somebody enters, and every join, a reconnection included, issues a new one. Only a participant
who is let in gets one. The secret has no default in `application.yml`: `docker-compose.yml`
carries the development one, a deployment sets its own (at least 32 characters), and without it
joining answers `video: null`, logged once at startup. Jitsi runs under a compose profile, so the
normal start stays light:

```bash
docker compose --profile video up
```

Seeing and hearing each other is checked by hand there and is `@pending` in
`US08-join-session.feature`.

**What each participant sees of the arrivals.** Both answers, `GET /sessions/{id}` and `POST
/sessions/{id}/join`, carry `studentJoinedAt` and `tutorJoinedAt`: the first arrival of each, UTC,
`null` for whoever has not come. Both participants see both, so whoever enters knows whether the
other is already there. `startedAt` is `null` until both have checked in. `GET /sessions/{id}` also
carries `absentParticipantIds`: the participants who had not entered before the ten minute mark. It
is filled only once the session is `ABANDONED` and is empty before. Nothing writes an arrival but
joining, and joining records only the first one: there is no endpoint to correct it, and since
`V202610150752__sessions_refuse_changing_an_arrival.sql` the database refuses an `UPDATE` that
changes an arrival already recorded, made by the application or straight on the table (the trigger
`tr_participations_arrival_immutable`).

**The presence check.** Five minutes after the scheduled start, a code is issued to each
participant of a session that has started and sent by email. Whoever does not confirm makes the
session end as `UNVERIFIED`: the tutor earns nothing, the student is refunded and the audit is
told. The code is stored hashed, expires and caps attempts.

**Built so far (US54):** a job runs every minute (`ayni.sessions.presence-check-delay`) and issues
the codes of every session in progress at least five minutes past its scheduled start and not over,
one `PresenceCodeIssued` per participant. A session in progress is one both of them entered, so a
session that never started is issued none. The code is six
digits, lasts fifteen minutes and dies after five wrong attempts; a wrong one answers 422 and still
counts, so the transaction commits on it. `POST /sessions/{id}/presence` only accepts a participant
who joined, while the session is in progress: with only one of them in the room it is `SCHEDULED`
and the answer is 409. `GET /sessions/{id}` tells the reader when the codes
go out and the state of their own code, never the code.

**Closing (US11).** `POST /sessions/{id}/end` records that a participant who joined confirms the end.
The second confirmation closes the session; with only one, or none, a job that runs every minute
(`ayni.sessions.closing-delay`) closes it fifteen minutes after the booked hour, and
`participations.end_confirmed_at` keeps who confirmed. Either way the outcome is the presence
check's: `COMPLETED` when both confirmed their code, with `SessionCompleted` paying the tutor the
booked hours; `UNVERIFIED` otherwise, including a session closed before any code was sent, with
`SessionUnverified` naming who did not confirm. Only a session that started is closed this way: one
that never started, because somebody did not come, is not ended by a participant and is not closed
by the job, whatever time it is, and it is `ABANDONED` by the section below instead. The session is locked while it is closed, and while
a code is confirmed, so it closes once and its outcome is not changed after it was decided.

**An absence (US06, US09).** A session is abandoned when ten minutes have passed since its
scheduled start and either participant had not entered before then, the tutor, the student or both.
The session becomes `ABANDONED`, `ended_at` is set, `startedAt` stays `null` and `SessionAbandoned`
is published, naming the booking, the tutor, the student, the skill and `studentCheckedIn`, which
says whether the student had entered by that same mark. The event keeps its shape: when the student
had entered the tutor was the one missing, and when they had not, the student was, whether or not
the tutor came. Who pays, and who is told, depends on that flag:

| The student | The tutor | The session | The booking | Credits | The student is told | The tutor's record |
|---|---|---|---|---|---|---|
| entered | did not | `ABANDONED` | `NO_SHOW` | returned to the student | yes | a `NO_SHOW` incident |
| did not | entered | `ABANDONED` | `NO_SHOW` | not returned | no | nothing, and the tutor earns no credits |
| did not | did not | `ABANDONED` | `NO_SHOW` | not returned | no | nothing |

When the student is the one who does not come, the hour was held for them and taken from other
students, so the credit stays spent, which is also what a student cancelling late pays; the tutor
who waited is not blamed and is not paid either, because nothing was taught and no `SessionCompleted`
exists. When nobody attends nobody is refunded and nobody is blamed.

A job runs every minute (`ayni.sessions.no-show-delay`, `PT24H` in the tests so it never races
one), one university at a time and one session per transaction, and calls
`AbandonTutorNoShowUseCase`; the tests call that same use case. The session is locked while it is
abandoned, so two runs at once abandon it once and publish `SessionAbandoned` once. A session that
never started is never issued presence codes and is never closed `UNVERIFIED` by the closing job,
and an abandoned one refuses to be joined or confirmed: both jobs only look at sessions in
progress, and the status says it is over.

Only participants may read a session. Anyone else gets a refusal, link or no link.

**Support material (US07).** When a confirmed booking creates its session, and once that is
committed, sessions prepares the session's support material from the need the student wrote: the
need is read through `BookingApi.requireBooking` and handed to the `SupportMaterialGenerator` port,
in a transaction of its own. Today a simulated adapter answers it (the first sentence as the
`summary`, and `suggestions` from the main words of the need, in Spanish);
`ayni.sessions.support-material.simulated-outcome=UNAVAILABLE` makes it fail. When it fails nothing
is stored and the session shows only the need: a degraded answer, noted at `INFO`, never an error,
and the session is scheduled all the same. `GET /api/v1/sessions/{id}` carries it as
`supportMaterial` (`summary`, `suggestions`, `generatedAt`), `null` when there is none. One per
session (`UNIQUE (session_id)`); the same confirmation heard twice creates one session and prepares
one material. A material that failed is not retried. Neither the need nor the material goes to a
log.

**Reviewing a past session (US12).** `GET /api/v1/sessions/{id}/review` answers a participant what
is left of a session of their history: `status`, a `statusNote` saying what happened to it, the
scheduled and real times, `bookedMinutes`, `realDurationMinutes`, the `needDescription`, the
`supportMaterial` and the `whiteboard` as it was left. The real duration runs from `startedAt`, both
participants in, to `endedAt`, its close, the same measure the audit compares with the booked hours;
it is `null` for a session that never took place. A session that did not take place answers its
state instead of an empty workspace: `CANCELLED` and `ABANDONED` (with `absentParticipantIds`) carry
their note and no `whiteboard`, because they never had one; one that took place with nothing drawn
answers an empty board with `hasActivity` false. Somebody who did not take part is refused with
`403`, and another university's session answers `404`. The real duration is that of the room,
not of each person's connection: `participations.connected_seconds` is still not measured.

**The shared whiteboard (US10, TS08).** The model and the order of the operations are ADR 0009. A
board is a set of elements (`id`, `type`, opaque `data`) stored in `sessions.whiteboards.content` as
`{"version": n, "elements": [...]}`. Both participants draw on it at once over the live channel,
a WebSocket at `/api/v1/sessions/{id}/whiteboard/live`:

- **Opening it** needs the session token, as `Authorization: Bearer` or, from a browser, as the
  subprotocol pair `bearer, <token>` (the server answers the `bearer` subprotocol). Only the two
  participants of a session `IN_PROGRESS` get in; the handshake answers 401, 403, another
  university's session 404, and a session not in progress 409. The first message is the whole board
  with its version: `{"type": "board", ...}`.
- **Drawing** is sending `{"type": "operation", "operation": {"id", "action", "element" |
  "elementId"}}` with `PUT_ELEMENT`, `REMOVE_ELEMENT` or `CLEAR`. Every action is idempotent. The
  backend locks the session, applies it, gives the board its next version, commits, and only then
  sends `{"type": "applied", "version", "by", "operation"}` to both participants, the sender
  included. One lock per board in memory keeps them leaving in version order. A refused operation
  answers `{"type": "error", "status", "message"}` (400 for a malformed element, 409 once the
  session is over) and the channel stays open.
- **Reconnecting** is opening it again: the board comes whole, never a replay.
- **Closing the session** (both ending it, or the closing job) closes every channel with code 4409.
  Nothing is left to save: each operation was stored as it was applied. A session where nobody drew
  gets its empty board stored, with `has_activity` false, and `WhiteboardClosed` is published with
  `hadActivity` either way.

`GET /api/v1/sessions/{id}/whiteboard` answers the board in any state of the session: `sessionId`,
`version`, `elements`, `hasActivity`, `live` and `updatedAt`. `PUT` replaces every element of a
session in progress, with the `version` last read, and both participants connected receive the new
board.

| Status | When |
|---|---|
| 200 | The board, or the board replaced |
| 400 | An element without a valid `id` (1 to 64 letters, digits, `-`, `_`) or a `type`, more than 2000 elements, or one over 16 KB |
| 403 | The caller is neither the student nor the tutor of the session |
| 404 | The session does not exist in this university |
| 409 | `PUT` on a session that is not in progress, or with a `version` that is not the current one |

The live channels are kept in the memory of the instance, which is enough for the monolith (ADR
0002); a second instance would need a broker in between. The token is checked when a channel opens;
a token revoked during a session keeps its open channel until the session ends. The screen of the web
application is not built yet.

---

## recognition

Owns the request and the university's decision. **Ayni certifies nothing by itself.**

**Publishes:** `RecognitionRequested`, `RecognitionResolved`.
**Calls:** `SessionsApi.completedSessionsOf`, `WalletApi.earnedTotal`, `ReputationApi.standingOf`,
`AuditApi.openAlertsOf` (through the `SessionAlerts` port), `IdentityApi.requireTenant` for the zone
of the university's day.

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/coordinator/recognition/rule` | coordinator — the rule in force of the university: `minimumHours` and `validFrom`; 404 until one is set (US27, US28) |
| PUT | `/api/v1/coordinator/recognition/rule` | coordinator — sets the rule: `minimumHours` (at least 1) and an optional `validFrom`, today by default; closes the one in force and adds the next (US27, US28) |
| GET | `/api/v1/recognition/progress` | student — hours taught against what the university asks for (US27) |
| POST | `/api/v1/recognition/requests` | student — submits the request with the sessions that back it (US28) |
| GET | `/api/v1/recognition/requests/mine` | student — own requests with state, decision and reason (US28) |
| GET | `/api/v1/coordinator/recognition/requests` | coordinator — what waits, oldest first, with the student and the hours presented; `status` shows the history (US29) |
| GET | `/api/v1/coordinator/recognition/requests/{id}` | coordinator — the case with its evidence, frozen as submitted, and the audit alerts (US29) |
| GET | `/api/v1/coordinator/recognition/requests/{id}/sessions` | coordinator — the sessions behind it: date, duration, course or skill taught; their hours add up to the total (US30) |
| GET | `/api/v1/coordinator/recognition/requests/{id}/sessions/{sessionId}` | coordinator — one session's evidence: attendance of both parties, presence verified, the tutor's rating (US30) |
| POST | `/api/v1/coordinator/recognition/requests/{id}/decision` | coordinator — approve or reject with a reason; decided once (US29) |

**Setting the rule (US27, US28).** `PUT /coordinator/recognition/rule` is what opens recognition in a
university: until a coordinator sets a rule (outside the `dev` profile nothing else writes one) the
student has no requirement and `POST /recognition/requests` answers 409. The caller must be an
active coordinator of the university (`IdentityApi.requireUser` and `isActive`): a student, or a
coordinator who is restricted, gets a 403. `minimumHours` is a whole number of at least 1 and
`validFrom` a date of the university's calendar, today (in the university's zone) when omitted;
a day that already passed is a 400. A rule is never edited: in one transaction the rules it replaces
get `superseded_at` and the new row is added, and the answer is the new rule. A rule that starts today
replaces every open rule; one that starts later leaves the rule in force running until its day and
replaces only the ones scheduled for that day or after. `GET` answers the rule in force today, and
404 when there is none; the rule of another university is never seen. Requests already submitted keep
the hours, count and ratings they were submitted with, so setting a rule changes none of them nor
their reading. Two coordinators at once wait for each other under a `pg_advisory_xact_lock` per
university (`RecognitionRuleRepository.lockTenant`), taken before reading the open rules: both are
answered and one rule stays in force. `minimum_rating` is not set by this endpoint, since nothing
reads it yet.

**Progress (US27).** `GET /recognition/progress` adds up the booked hours of the student's
`COMPLETED` sessions, read from `SessionsApi` at the moment of asking, and compares them with the
`minimum_hours` of the rule in force of the university (`recognition.rules`, the latest one started
and not superseded, on the university's day: see below). It does not call `WalletApi.earnedTotal`: wallet credits the tutor from a
listener, after the session, and keep counting after a request used the session, while the sessions
are what the request is made of. Credits assigned by the university or bought are never in the figure
since no session stands behind them. A university with no rule has not opened recognition: the
required and missing hours are absent and `canRequest` is false.

**Submitting (US28).** `POST /recognition/requests` takes the student's verified sessions that no
request has used, the oldest first, until the hours of the rule are reached, and registers the request
with their total, their count and the mean of the ratings they carry. Each session keeps the skill
(read through `BookingApi.requireBooking`), the period and the rating it had. The student keeps the
sessions beyond the requirement for the next request. With fewer hours nothing is saved and the
answer is a 409 with `missingHours`; with no rule in force it is a 409 too, without it. Submitting
waits for its turn per student (`pg_advisory_xact_lock`), so two requests at once cannot take the
same sessions; `RecognitionRequested` is published. The ratings come from the `SessionRatings` port:
reputation has no rating per session to give yet, and the adapter in place says there are none.

**Reviewing (US29).** The coordinator (checked in the use case through `IdentityApi.requireUser`, as
in skills) reads the queue and then a case: the figures and the sessions of the request exactly as they
were submitted, so reading it days later shows the same. Each session carries its date, how long it
really lasted, `presenceVerified` (always true: only `COMPLETED` sessions, where both confirmed their
code, back a request) and the rating copied. The alerts of the audit come from the `SessionAlerts`
port and are put next to the session they are about: the adapter asks `AuditApi.openAlertsOf` and
carries only the alerts that are still open, so one a coordinator dismissed is not shown again.

**Supporting sessions (US30).** `GET .../{id}/sessions` lists the sessions behind a request with the date,
the duration and the course or skill taught (read through `SkillsApi.requireItem`); the hours listed
add up to the total because both were copied at submission. `GET .../{id}/sessions/{sessionId}` opens
one of them: the register of both participants (names through `IdentityApi`, presence verified when
`SessionsApi.requireSession` says the session is `COMPLETED`, which is what both confirming their code
means) and the tutor's rating as copied. A session is only reached through the request it backs. The
per-participant arrival and confirmation times are not exposed by `SessionsApi` and are not shown.

**Deciding (US29).** `POST .../decision` takes `APPROVE` or `REJECT` and a reason, required for both.
It records who decided, when and why, publishes `RecognitionResolved` so notifications can tell the
student, and the student reads the decision and the reason in `requests/mine`. A request is decided
once: the request is locked (`PESSIMISTIC_WRITE`) while it is decided, so two coordinators at once end
with one decision and one 409. A resolved request without reviewer, date and reason is refused by the
table too.

**Credits are not consumed.** What a request consumes are the sessions: each one backs one request
at a time, enforced by a unique index on `(tenant_id, session_id)` over the rows that were not
released. The figures are copied at submission so the file does not move while it is being reviewed.

**A rejection gives the sessions back.** Rejecting a request releases its sessions in the same
transaction (`request_sessions.released_at`), so the student's next request can be made of them,
oldest first as always. The rejected file stays exactly as it was presented: its rows, its hours,
its count and its ratings are kept and read as before by the coordinator and by the student. An
approved request never releases its sessions. The release is decided under the lock of the
request, and the next submission under the student's lock, so two submissions at once after a
rejection take the freed sessions once: one `201` and one `409` with `missingHours`.

**The rule in force** is the latest `recognition.rules` row that started and was not superseded,
looked for on **the university's own day**, in the zone `IdentityApi.requireTenant` answers: a
rule that starts on the 16th is not in force at 21:00 of the 15th in Lima, though it is already the
16th in UTC. Progress (US27) and submission (US28) read it the same way.

**Every query of `RequestedSessionRepository` takes the university**, the policy of row level
security being the net.

---

## reputation

```java
public interface ReputationApi {
  Optional<TutorStandingView> standingOf(UUID tutorId, UUID catalogItemId);
}
```

**Publishes:** `SessionRated`.
**Calls:** `SkillsApi.isTutorEnabledFor` — to tell a new tutor from one who does not teach the skill.
**Listens to:** `SessionCompleted` — counts the session and opens the rating window.

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

**`POST /api/v1/sessions/{id}/rating`** (US32 and US34). One endpoint, two directions: the caller is
the person in `X-User-Id`, and who they are in the session decides what they send.

- A **student** sends `stars` (1 to 5) and optionally `tags` and `comment`. It becomes a
  `STUDENT_TO_TUTOR` rating and counts in the tutor's standing in the skill of the session.
- A **tutor** sends `wasPunctual`, `connectionOk` and `sessionFlowed`, all three, and optionally
  `comment`. It becomes a `TUTOR_TO_STUDENT` rating and does not touch any standing: it is three
  facts about the session, not a score.

**Who opens the window.** reputation does, when it hears `SessionCompleted`: it writes
`reputation.rating_windows` (see `database/data-model.md`) and counts the session in the tutor's
`sessions_taught`, once, however many times the event arrives. A session that never completed has no
window and cannot be rated. A successful rating publishes `SessionRated`, which matching listens to.

| Status | When |
|---|---|
| 201 | Registered. The body carries the id of the rating |
| 400 | A student without `stars`, a tutor missing any of the three facts, `stars` outside 1 to 5, a missing `X-Tenant-Id` or `X-User-Id`, or a session id that is not a UUID |
| 403 | The caller is neither the tutor nor the student of the session |
| 404 | There is no completed session with that id in this university. Another university's session answers the same |
| 409 | That direction of the session was already rated |

**Races.** The window of the session is locked while someone rates, so two ratings of the same
session and direction at once end with one `201` and one `409`, never a `500`. The standing is
locked too, because the count and the average are read and written back: two students rating the same
tutor in the same skill at once are both counted.

---

## notifications

**No published interface, and nothing depends on it.** It only listens.

**Calls:** `IdentityApi.requireUser` — for the address of the person an event names.

Reacts to `AccessRequested`, `BookingConfirmed`, `BookingCancelled`, `PresenceCodeIssued`,
`SessionAbandoned`, `CreditsExpiring`, `RecognitionResolved`, `ValidationResolved`, and whatever
is added later.

| Method | Path | Who | Does |
|---|---|---|---|
| GET | `/api/v1/notifications?page=&size=&unread=` | student or coordinator | own notices, newest first, with `unreadCount` |
| POST | `/api/v1/notifications/{id}/read` | student or coordinator | marks one of their own as read (204) |
| POST | `/api/v1/notifications/read-all` | student or coordinator | marks every unread one of theirs as read (204) |

The row is written before the email leaves, and a failure is recorded. Two things depend on delivery
working: getting in, and confirming presence.

**Built so far:** `AccessRequested`, the email with the access link (US38). The notice is committed
before the email leaves and then marked sent or failed. Its payload records what the link was for and
when it expires, never the link: identity keeps only the token's hash and the email is the only
copy. A failed access link is not retried, because there is nothing stored to retry it with and it
expires in minutes; asking again issues a new one. Email goes over SMTP (`spring.mail.*`): to Mailpit
under `docker compose`, which shows it at http://localhost:8025, and to a real relay in a deployment.
`PresenceCodeIssued`, the email with the presence code (US54), is delivered the same way: the
notice keeps the session and the expiry, never the code, and the address is read from identity.
`SessionAbandoned` tells the student that their tutor did not come and the credit was returned
(US06), only when `studentCheckedIn` is true: nobody is told anything when the student was the one
missing, or when nobody came. The notice
is a `TUTOR_NO_SHOW`, committed before the email leaves like the others, and keeps the session and
nothing else; neither the address nor the email goes to a log.
`PurchaseConfirmed` sends the student a receipt of the confirmed credit amount (US36); the notice
stores the purchase identifier and amount, not payment credentials. The other events above are
still to do.

**The inbox.** The person reads their own notices, those with their `recipient_id` in the university
of their session; no endpoint takes a recipient. `GET /api/v1/notifications` answers `items`
(`id`, `kind`, `payload`, `createdAt`, `readAt`), `page`, `size` (20 by default, 100 at most),
`total` and `unreadCount`, newest first; `unread=true` keeps the ones not read yet, and
`unreadCount` counts all of them whatever the filter. What it leaves out:

- `ACCESS_LINK` and `PRESENCE_CODE` (`NotificationKind.isListed`): they travel by email and carry a
  secret, so they are not notices of the application. They cannot be marked as read either.
- Rows without a `recipient_id`, which have nobody to show them to.
- `recipient_email` and `failed_reason`. The list belongs to the application, not to the delivery:
  a notice whose email failed is listed all the same.

Marking is idempotent: `Notification.markRead` keeps the date of the first time, which comes from
the `Clock`. The notice is read with a pessimistic lock (`lockOwned`, `lockUnread`), so two requests
at once take turns and the second finds the date the first set (`NotificationsInboxAcceptanceTest`
holds the lock from one transaction and fails when it is removed). The scenarios are in
`notifications-inbox.feature`, which has no story number because the inbox is no story of the
backlog.

| Status | When |
|---|---|
| 204 | Marked, or it already was; `read-all` also answers 204 when nothing was unread |
| 400 | A `page` below 0, a `size` outside 1 to 100, or an identifier that is not a UUID |
| 401 | No session (the access filter) |
| 403 | A platform administrator, who belongs to no university (the access filter) |
| 404 | The notice is somebody else's, another university's, of a kind that is not listed, or does not exist |

---

## audit

Records what happened and detects what does not add up.

```java
public interface AuditApi {
  Map<UUID, List<AlertView>> openAlertsOf(Collection<UUID> sessionIds);   // used by recognition
}
```

`openAlertsOf` answers, for each of the sessions of the current university that has any, the alerts
that are still `OPEN` about it: `id`, `kind`, `severity`, `description` and `detectedAt`. A session
with none is not in the map, and neither is a dismissed alert. It is the only thing audit offers: the
rest is an endpoint.

**Calls:** `IdentityApi.requireUser` and `IdentityApi.isActive`, to know who asks for the log or the
alerts, and `IdentityApi.requireUser` again for the name of the people an alert involves;
`IdentityApi.activeTenantCodes`, for the job that looks at every university; `SessionsApi` for what
an alert shows about a session, for how long it really lasted and for how it ended.

**Listens to**, to raise alerts (US47): `SessionUnverified`, `SessionCompleted` and `WhiteboardClosed`,
described under "Alerts of anomalous usage" below. The listeners that write the log
call nobody: who acted is what the event says.

**Listens to** (US48), one entry per event in `audit.activity_log`:

| Event | Action | Who acted | Resource |
|---|---|---|---|
| `BookingConfirmed` | `BOOKING_CONFIRMED` | the student | `BOOKING` |
| `BookingCancelled` | `BOOKING_CANCELLED` | whoever `cancelledBy` says, `SYSTEM` for the system | `BOOKING` |
| `SessionStarted` | `SESSION_STARTED` | `SYSTEM` | `SESSION` |
| `SessionCompleted` | `SESSION_COMPLETED` | `SYSTEM` | `SESSION` |
| `SessionUnverified` | `SESSION_UNVERIFIED` | `SYSTEM` | `SESSION` |
| `SessionAbandoned` | `SESSION_ABANDONED` | `SYSTEM` | `SESSION` |
| `CreditsGranted` | `CREDITS_GRANTED` | `SYSTEM` | `WALLET`, the person |
| `CreditsExpired` | `CREDITS_EXPIRED` | `SYSTEM` | `WALLET`, the person |
| `SkillEnabled` | `SKILL_ENABLED` | `SYSTEM` | `CATALOG_ITEM` |
| `SkillWithdrawn` | `SKILL_WITHDRAWN` | `SYSTEM` | `CATALOG_ITEM` |
| `ValidationResolved` | `VALIDATION_APPROVED` or `VALIDATION_REJECTED` | a coordinator, not named | `VALIDATION_REQUEST` |
| `RecognitionRequested` | `RECOGNITION_REQUESTED` | the student | `RECOGNITION_REQUEST` |
| `RecognitionResolved` | `RECOGNITION_APPROVED` or `RECOGNITION_REJECTED` | a coordinator, not named | `RECOGNITION_REQUEST` |

Audit also writes one entry of its own, which no event causes: `ANOMALY_DISMISSED`, with the
coordinator who dismissed an alert as actor, `COORDINATOR` as role and the alert (`ANOMALY`) as
resource, in the same transaction as the dismissal. Its details are the kind, the severity and the
subject of the alert, never the reason.

**Not listened to, on purpose.** `AccessRequested` and `PresenceCodeIssued` carry the access link
and the presence code in clear, and `StudentActivated` carries an email address: no entry holds an
email, a link or a code. `CreditsExpiring` is a reminder of something that has not happened yet,
`HoursGenerated`, `HoursWithdrawn` and `AvailabilityPublished` are availability and not a
reservation, `PurchaseConfirmed` is already told by the `CreditsGranted` it causes, `SessionRated`
belongs to reputation, and the university events say nothing about credits or recognition.

The listeners use `@ApplicationModuleListener` and bind the university with
`TenantContext.runAs(event.tenantId(), ...)`. An entry is written in a transaction of its own, and a
failure is logged without the data of the event and swallowed: **recording is never the reason an
operation fails.** The reason of a decision is not copied, since it is free text and stays with the
decision.

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/coordinator/audit-log` | coordinator: reads the log of their university (US48) |
| GET | `/api/v1/coordinator/anomalies` | coordinator: the alerts of anomalous usage, paged, by status (US47) |
| GET | `/api/v1/coordinator/anomalies/{id}` | coordinator: one alert with its pattern, its people and its sessions (US47) |
| POST | `/api/v1/coordinator/anomalies/{id}/dismiss` | coordinator: closes an alert with a reason (US47) |

**Reading the log (US48).** `GET /api/v1/coordinator/audit-log` takes the optional filters
`actorId` (the person who acted), `resourceType` and `resourceId` (the resource), and `from` and
`to` (ISO 8601 UTC, `[from, to)`), and `page` and `size` (20 by default, at most 100). It answers
`items`, `page`, `size` and `total`, the most recent first. Every item carries `id`, `occurredAt`,
`actorId`, `actorRole`, `action`, `resourceType`, `resourceId` and `details`. It always filters by
the university of the request: another university's entries are not reachable by any filter.

| Status | When |
|---|---|
| 200 | A page of entries, possibly empty |
| 400 | A missing `X-Tenant-Id` or `X-User-Id`, an identifier that is not a UUID, a date that is not ISO 8601, or `to` before `from` |
| 403 | The person is not an active coordinator of that university: a student, a coordinator who is restricted, or somebody the university does not know. The attempt is recorded |

**A refusal that is remembered.** The use case writes an `AUDIT_LOG_ACCESS_DENIED` entry for the
person (`actorRole` is the one they have in the university, `UNKNOWN` when it does not know them,
and `details.reason` says why) and then throws; the transaction is declared with
`noRollbackFor = AuditAccessDenied.class`, so the entry stays. The acceptance test reads the row
after the `403`. A request with no `X-User-Id` is malformed, answers `400` and writes nothing.

**Append only.** The table refuses `UPDATE`, `DELETE` and `TRUNCATE` with triggers, described in
`database/data-model.md`. A correction is a new entry.

**Not built.** Pointing out an entry that does not fit the sequence needs the entries chained, and
`audit.activity_log` has no columns for that. It is a decision of its own, tagged `@pending` in
`US48-audit-log.feature`.

### Alerts of anomalous usage (US47)

An alert, an `audit.anomalies` row, is a pattern that suggests credits are being piled up
artificially. It is raised `OPEN`, names the tutor whose credits it puts in question (`subject_id`),
keeps the other people and the figures that made it an anomaly in `detail`, and points at the
sessions that support it in `audit.anomaly_sessions`. It carries no email, link, code or free text.

**What it detects**

| Kind | Raised by | When | Severity |
|---|---|---|---|
| `FAILED_PRESENCE` | `SessionUnverified` | always: a session ended without every participant confirming | `HIGH` when neither confirmed, `MEDIUM` when one did |
| `SHORT_DURATION` | `SessionCompleted` | the session lasted strictly less than `ayni.audit.short-session-ratio` (0.5 by default) of the hours booked; the real duration is `startedAt` to `endedAt` of `SessionsApi.completedSessionsOf`, so the interface did not change | `HIGH` under half of that fraction, `MEDIUM` otherwise |
| `NO_BOARD_ACTIVITY` | `WhiteboardClosed` | `hadActivity` is false and the session ended `COMPLETED`, which `SessionsApi.requireSession` says once the closing committed. A session that ended `UNVERIFIED` raises `FAILED_PRESENCE` and nothing about its board | `LOW`: teaching without drawing is possible, so it only weighs next to another alert |
| `REPEATED_PAIR` | the job | the same tutor and student completed more than `ayni.audit.repeated-pair-threshold` sessions (3) in the last `ayni.audit.repeated-pair-period` (`P30D`) | `HIGH` from twice the threshold, `MEDIUM` otherwise |

The three by event run after the publisher commits and in a transaction of their own, with the
university of the event bound. A failure is logged without the data of the event and swallowed, as
recording is: what was published has already happened, and the platform never waits for the analysis.
The alert of a kind and a session has an identifier derived from the university, the kind and the
session, so the same event delivered twice finds it already there, also when both arrive at the
same moment.

**The job.** `ayni.audit.analysis-delay` (`PT1H`; `PT24H` in
`src/test/resources/config/application.properties`, so it never races a test) runs
`DetectRepeatedPairsUseCase` for one university at a time, from `IdentityApi.activeTenantCodes()`
and `TenantContext.runAs`, and decides each pair in a transaction of its own. It reads what audit
recorded, the `SESSION_COMPLETED` entries of `audit.activity_log`, and asks no other module: those
entries already carry the session, the tutor, the student and the moment. A session counts once even
if its completion was written twice. A pair is pointed out once per period: while an alert about it,
open or dismissed, is younger than the period, it is not raised again. The decision of one pair takes
an advisory lock, so two runs at once raise it once. The tests call the use case the job calls.

`UNUSUAL_FREQUENCY` is in the model and nothing raises it yet: it needs a baseline of what is usual
for a tutor. `NO_BOARD_ACTIVITY` is raised as the session closes, so a session is judged once, when
its board can no longer change; the job does not look at boards.

**Reading and deciding.** All three are for an active coordinator of the university of the request.

| Method | Path | Does |
|---|---|---|
| GET | `/api/v1/coordinator/anomalies` | takes `status` (`OPEN`, `DISMISSED` or `CONFIRMED`, optional, any case), `page` and `size` (20 by default, at most 100). Answers `items`, `page`, `size` and `total`, the most recently detected first; each item has `id`, `kind`, `severity`, `status`, `subjectId`, `description`, `sessionCount` and `detectedAt` |
| GET | `/api/v1/coordinator/anomalies/{id}` | the alert with `pattern` (the figures, as they were when it was detected), `people` (id, role and name), `sessions` (id, scheduled start and end, status) and, once decided, `resolution` |
| POST | `/api/v1/coordinator/anomalies/{id}/dismiss` | takes `reason`, required, at most 500 characters; closes the alert as `DISMISSED` with who and when, and writes `ANOMALY_DISMISSED` in the log |

| Status | When |
|---|---|
| 200 | A page, the alert, or the alert dismissed |
| 400 | A missing `X-Tenant-Id` or `X-User-Id`, an id that is not a UUID, a `status` that is not one of the three, a body that is missing, or a `reason` that is blank or too long |
| 403 | The person is not an active coordinator of that university: a student, a coordinator who is restricted, or somebody the university does not know |
| 404 | The alert does not exist **in this university**. Another university's alert answers the same |
| 409 | Dismissing an alert that was already resolved |

The alert is read with `PESSIMISTIC_WRITE` while it is dismissed, so two coordinators at once end
with one `200` and one `409`, and one entry in the log. The decision is not reopened. Unlike the
refused attempt to read the log, a refused attempt on the alerts is not written down.

---

## analytics

Answers how much the service is used, from its own projection. **No published interface:** nothing
depends on it. It never asks another module to answer a question; it keeps what the events say.

**Listens to** (US53), counting each fact once:

| Event | Counts |
|---|---|
| `SessionCompleted` | one session completed, its hours (one credit is one hour) and its tutor and its student as active students of that day |
| `StudentActivated` | one new student. The email and the student code of the event are never read or kept |
| `RecognitionResolved` | one recognition granted, only when `approved`. A rejection counts nothing |
| `DemandUncovered` | one search of a course that found no hour inside its window, on the day of the university (US53 uncovered demand). Not the student: the event carries no one |

**Calls:** `IdentityApi.requireTenant`, only while projecting, for the time zone of the university;
`IdentityApi.requireUser` and `IdentityApi.isActive`, to know that whoever asks is an active
coordinator. The day of a fact is the one of the university's own zone, taken from the `occurredOn`
of the event, not the UTC one: a session that ends at 22:00 in Lima belongs to that day.

**Counted once.** The same event delivered twice changes nothing the second time, and a student who
took part in several sessions on a day is one active student of that day. Two auxiliary tables,
described in `database/data-model.md`, hold only identifiers for this, and no endpoint returns them.
Both are decided by a primary key, so two deliveries at the same moment count once. The one
exception is `DemandUncovered`: a search has no identity of its own, so every one that arrives is a
search, and the counter is one atomic `INSERT ... ON CONFLICT DO UPDATE` that two at the same moment
cannot corrupt.

**The search does not wait for this (TS07).** Of the listeners, only the one of `DemandUncovered`
hangs from the search, whose response time is measured. Counting on its thread wrote to the database
inside the search and, with the transaction that `@ApplicationModuleListener` opens, asked the pool
for a second connection while the search held the first: with fifty students at once on the ten
connections of the pool, every connection ended held by a search waiting for another one, and the
searches failed after the 30 seconds of the pool. So this listener is a plain after commit
listener that only leaves the event in `UncoveredDemandQueue`, two worker threads and a thousand
places, and answers. When the queue is full the event is dropped and a line says so; a count of
searches may lose a few under overload, a search must not wait. Tests wait for the queue with
`UncoveredDemandQueue.awaitIdle`, not with a sleep.

**Projecting never breaks the publisher.** The listeners bind the university with
`TenantContext.runAs(event.tenantId(), ...)` and run each fact in a transaction of its own. A
failure is caught and logged in one line with the name of the exception, without a stack and without
the data of the event; a university whose zone cannot be read (unknown, or an interface that is
simulated in a test) is noted at `DEBUG` only. An event lost this way is not recovered until the
publication registry of ADR 0005 exists.

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/coordinator/indicators?from=&to=` | coordinator: the totals of the period and the series, one entry per day (US53) |
| GET | `/api/v1/coordinator/indicators/export?from=&to=` | coordinator: the same figures as a CSV file (US53) |

`from` and `to` are ISO 8601 dates, both included, in the university's own days. The answer has
`from`, `to`, `totals`, `days` and `uncoveredDemand`; `totals` and each day's `figures` carry `sessionsCompleted`,
`hoursExchanged`, `activeStudents`, `newStudents` and `recognitionsGranted`. The `activeStudents` of
`totals` are distinct people over the whole period, not the sum of the days. Every day of the period
is in the series, with zeros when nothing happened. Only the university of the request is read, and
no name or identifier of a person is ever returned.

The export is `text/csv` with `Content-Disposition: attachment;
filename="usage-indicators-<from>-<to>.csv"`. Its header is
`day,sessions_completed,hours_exchanged,active_students,new_students,recognitions_granted`, then one
line per day and a last line whose day is `TOTAL`. The uncovered demand is not in the file.

`uncoveredDemand` lists the courses students searched for in the period without finding an hour
inside their window, as `{catalogItemId, searches}`, the most searched first (ties by identifier)
and at most 20. It carries identifiers only: the client resolves the names with `GET
/api/v1/catalog`. It is empty when there was nothing.

| Status | When |
|---|---|
| 200 | The totals and the series, or the file |
| 400 | A missing `X-Tenant-Id` or `X-User-Id`, a missing `from` or `to`, a date that is not ISO 8601, `to` before `from`, or a period of more than 366 days |
| 403 | The person is not an active coordinator of that university: a student, a coordinator who is restricted, one of another university, or somebody the university does not know |

**Not built.** The `/api/v1/admin/overview` endpoint waits for the administrator role (TS03).

---

## payments

`payments` owns credit purchases and exposes `POST /api/v1/payments/purchases`. The
student comes from `X-User-Id`, the university from `X-Tenant-Id`, and each attempt requires an
`Idempotency-Key`. A repeated key for the same student returns the original result; a new attempt
after a rejection needs a new key.

Until the universities' academic cycles are available through a public interface, the purchase
allowance is five confirmed credits per student per UTC calendar month. Failed payments do not
consume it. `AYNI_PAYMENTS_MONTHLY_CREDIT_LIMIT` and `AYNI_PAYMENTS_PRICE_PER_CREDIT` configure the
allowance and price (default S/ 5.00 per credit). The simulated provider confirms by default;
`AYNI_PAYMENTS_SIMULATED_OUTCOME=REJECTED` exercises the declined-payment path.

Once the provider confirms, `payments` publishes `PurchaseConfirmed`; `wallet` handles that existing
event and credits non-expiring `PURCHASED` credits. `notifications` also listens to that event and
sends the student a purchase confirmation email; payments does not call notifications directly.
`GET /api/v1/payments/purchases` lists the current student's purchase outcomes, and
`GET /api/v1/payments/purchases/{purchaseId}` returns the purchase details and receipt. A confirmed
receipt includes its purchase reference, which matches `referenceType=PURCHASE` and `referenceId` in
`GET /api/v1/wallet/movements?reason=PURCHASE`. A pending purchase reserves its monthly allowance,
tells the student not to retry, and is checked by the scheduled provider reconciliation. Pending
attempts expire after 24 hours by default
(`AYNI_PAYMENTS_PENDING_TIMEOUT`); the check interval defaults to one minute
(`AYNI_PAYMENTS_RECONCILIATION_DELAY`). A provider outage leaves the purchase pending and does not
block other modules. The simulator can return `PENDING` or `UNAVAILABLE` for an initial charge and
`CONFIRMED`, `REJECTED`, `PENDING`, or `UNAVAILABLE` during reconciliation using
`AYNI_PAYMENTS_SIMULATED_OUTCOME` and `AYNI_PAYMENTS_SIMULATED_RESOLUTION_OUTCOME`.

The provider boundary is wrapped by an in-process timeout, circuit breaker, and bounded bulkhead;
it adds no external service. Defaults are 2 seconds per provider call, three consecutive failures,
30 seconds open, and two concurrent provider calls. Configure them with
`AYNI_PAYMENTS_TIMEOUT`, `AYNI_PAYMENTS_FAILURE_THRESHOLD`,
`AYNI_PAYMENTS_CIRCUIT_OPEN_DURATION`, and `AYNI_PAYMENTS_MAX_CONCURRENT_CALLS`. On timeout,
provider failure, an open circuit, or saturation, the purchase stays pending and the response
explicitly says purchases are temporarily unavailable and must not be retried. The existing
reconciliation checks the provider later; after the open period one half-open request probes for
recovery. Search, booking, sessions, and wallet do not depend on the payment provider.

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
- [ ] Every event the story promises is published, not only listened to
- [ ] Two requests that can race have a test that fails without the fix
- [ ] Every listener that moves credits is idempotent, with a test that delivers the event twice
- [ ] Each scenario of the `.feature` names its test; what is not built is tagged `@pending` and
      listed in the pull request
- [ ] This guide and `database/data-model.md` say what the pull request changed
