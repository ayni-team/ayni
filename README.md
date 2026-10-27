# Ayni

Academic time bank where university students exchange tutoring hours as credits. One credit equals
one hour. Credits earned by teaching are the only ones that count towards the recognition a
university may grant.

**Course:** 1ASI0657 Fundamentos de Arquitectura de Software · NRC 9206 · UPC

---

## Running it

You only need Docker. Not Java, not Maven, not Node, not Postgres.

```bash
docker compose up
```

The first run downloads images and dependencies and takes a few minutes. After that it is fast.

| What | Where |
|---|---|
| Web application | http://localhost:5173 |
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Database | `localhost:5432`, database `ayni`, user `ayni`, password `ayni` |
| Emails sent by the backend | http://localhost:8025 (Mailpit: nothing leaves your machine) |
| Video calls of the sessions | http://localhost:8000, only with `docker compose --profile video up` |

The web application opens at the sign-in screen: typing `u202400001@upc.edu.pe` paints UPC's name
and colours, which means the web reaches the API. `web/README.md` explains the web on its own.

### While you work

The sources are mounted into the containers, so you edit on your machine as usual.

- **Web:** the browser reloads by itself when you save.
- **Backend:** run `docker compose restart backend` after changing Java code. Restarting takes a few
  seconds, and it is the price of not having to install anything.

Other commands you will need:

```bash
docker compose up -d          # run in the background
docker compose logs -f backend
docker compose down           # stop everything
docker compose down -v        # stop and erase the database
docker compose --profile video up -d   # also the self hosted Jitsi of the video calls (US08)
```

If you prefer running the backend from IntelliJ, start only the database and the mail catcher with
`docker compose up postgres mailpit` and run `AyniApplication` from the IDE. You need Java 21 for
that.

---

## Production

The deployable image is `backend/Dockerfile.prod`; it runs the `prod` profile as an unprivileged
user on port 8080:

```bash
docker build -f backend/Dockerfile.prod -t ayni-backend backend
```

`prod` has no development defaults. It refuses to start, naming what is missing, without these
**required** variables (secrets in bold):

`AYNI_DB_URL`, `AYNI_DB_USER`, **`AYNI_DB_PASSWORD`**, `AYNI_DB_APP_USER`,
**`AYNI_DB_APP_PASSWORD`**, **`AYNI_JITSI_JWT_SECRET`** (32 characters or more),
`AYNI_IDENTITY_ACCESS_LINK_BASE_URL`.

**Set them too** in a real deployment, though they have defaults: `AYNI_ALLOWED_ORIGINS`,
`AYNI_MAIL_HOST`, `AYNI_MAIL_PORT`, `AYNI_JITSI_URL`, and `AYNI_BOOTSTRAP_ADMIN_EMAIL` on the first
start. **Optional:** `AYNI_BOOTSTRAP_ADMIN_FULL_NAME`, `AYNI_MAIL_USER`, **`AYNI_MAIL_PASSWORD`**,
`AYNI_MAIL_FROM`, `AYNI_JITSI_APP_ID`, `AYNI_ACADEMIC_DIRECTORY`, `AYNI_SKILLS_EVIDENCE_DIR`,
`AYNI_DB_POOL_SIZE`, `AYNI_API_DOCS_ENABLED`, `AYNI_PORT`, the ten `AYNI_PAYMENTS_*` settings
(`MONTHLY_CREDIT_LIMIT`, `PRICE_PER_CREDIT`, `PENDING_TIMEOUT`, `RECONCILIATION_DELAY`,
`SIMULATED_OUTCOME`, `SIMULATED_RESOLUTION_OUTCOME`, `FAILURE_THRESHOLD`, `CIRCUIT_OPEN_DURATION`,
`TIMEOUT`, `MAX_CONCURRENT_CALLS`), `SPRING_PROFILES_ACTIVE` and `JAVA_TOOL_OPTIONS`.

- **Two database roles.** Flyway migrates first as the owner (`AYNI_DB_USER`), which creates the
  schemas and, if it does not exist, the application's role with `AYNI_DB_APP_PASSWORD`; then the
  application connects as `AYNI_DB_APP_USER`, which has no `BYPASSRLS` and owns nothing.
- **The first administrator.** With `AYNI_BOOTSTRAP_ADMIN_EMAIL` set, the first start creates that
  platform administrator. They ask for an access link with that address and register the first
  university.
- **Health.** `/actuator/health/liveness` and `/actuator/health/readiness` (the database) are public.

Defaults, descriptions and permissions of every variable: "Deployment (prod profile)" in
`docs/backend-guide.md`. Never commit a real value of any of them.

The whole production stack (proxy with TLS, Jitsi, the managed database) and the steps to put it on
a virtual machine are in [`deploy/README.md`](deploy/README.md).

---

## How the project is organised

```
ayni/
├── backend/     API. Spring Boot, one modular monolith
├── web/         React and TypeScript application
└── docs/        Architecture decisions, database design, diagrams
```

Inside `backend`, every package under `pe.ayni` is a **module**: an area of the product that owns
its data and its rules.

| Module | Owns |
|---|---|
| `identity` | Universities, students, academic profile |
| `skills` | Course and skill catalogue, accreditations |
| `booking` | Availability, slots, reservations |
| `matching` | Tutor search |
| `sessions` | The live session, attendance, whiteboard |
| `wallet` | Credits: balance, expiry, history |
| `recognition` | Recognition requests and the university's decision |
| `reputation` | Ratings and tutor scores |
| `payments` | Credit purchases |
| `audit` | Activity log and detection of suspicious behaviour |
| `analytics` | Usage indicators |
| `notifications` | Notices |

`config` and `shared` are not modules: they hold wiring and building blocks that everyone may use.

Each module is organised the same way, so that anyone can open one they did not write:

```
booking/
├── domain/model/       entities and value objects, with their rules
├── domain/services/    rules that do not belong to a single entity
├── application/        use cases
├── infrastructure/     repositories
└── interfaces/rest/    controllers
```

### The one rule between modules

**Modules do not import one another.** If `booking` needs something from `wallet`, there are two
ways across:

- **Call an interface** that the other module publishes at its root package, when you need the
  answer to continue. Booking asks wallet to charge credits and handles the refusal.
- **Publish an event** when you do not. Sessions announces `SessionCompleted` and does not care who
  listens; wallet, reputation and notifications each react on their own.

`ModularityTest` fails the build if a module reaches into another one's internals. That test is the
evidence that this is a modular monolith and not a monolith with folders.

---

## Adding a feature

A story is implemented end to end by one person: database, backend and screen.

1. **The table.** A new file in `backend/src/main/resources/db/migration`, named
   `V<yyyyMMddHHmm>__<module>_<what>.sql`, for example `V202609161030__booking_create_slots.sql`.
   The timestamp avoids two people choosing the same number. Never edit an applied migration: to
   change something, write another file.
2. **The entity**, in `domain/model`, mapped to that table:

   ```java
   @Entity
   @Table(schema = "booking", name = "slots")
   public class Slot { ... }
   ```

   Hibernate checks the two agree and refuses to start if they do not, so a wrong mapping is caught
   immediately rather than in production.
3. **The use case** in `application`, **the repository** in `infrastructure`, **the controller** in
   `interfaces/rest`.
4. Try it in Swagger UI, then connect the screen.

The database design, table by table, is in `docs/database`. It is already decided: follow it rather
than inventing columns.

### The university and the person of each request

Every request carries the session token that opening an access link returns (TS03, ADR 0007):

```
Authorization: Bearer <sessionToken>
```

The backend looks the session up by the hash of the token and binds its university and its person
for the rest of the request:

| What | Read it in code with |
|---|---|
| the university code, for example `UPC` | `TenantContext.require()` |
| who is asking, as a UUID | `CurrentUser.require()` |

To get a token locally: `POST /api/v1/access/request` with an institutional email (the demo
student is `u202400001@upc.edu.pe`, the platform administrator `admin@ayni.local`), open the link
that arrives at Mailpit (http://localhost:8025), or send its `token` to `POST
/api/v1/access/confirm`, and paste the `sessionToken` into Swagger UI's **Authorize** button. Filter
every query by the university anyway.

The database enforces the same separation (TS01): the application connects as `ayni_app`, a role
row level security applies to, and every transaction tells PostgreSQL its university. A query that
forgets the filter answers nothing. A new table with `tenant_id` needs its policy in the same
migration; the backend guide says how.

`X-Tenant-Id` and `X-User-Id` are no longer read by the application. The tests written before TS03
still send them: a filter that exists only in `src/test` accepts them there, so those scenarios keep
proving their use case without signing in first.

---

## Conventions

Everything is written in **English**: classes, variables, tables, branches and commits. The course
brief requires it.

**Branches.** GitFlow: `main` for deliveries, `develop` for integration, and one branch per work
item named `feature/US05-T2-cancel-booking`, so the branch points at the Trello card.

**Commits.** Conventional Commits, with the module as the scope:

```
feat(booking): add cancellation with credit refund
fix(wallet): consume credits in expiry order
```

**Pull requests.** Nobody merges their own. `develop` is protected: it needs a review.

---

## What is deliberately missing

This is the starting point, not the destination. Authentication, database enforced isolation between
universities, payments and deployment are planned improvements, each tied to a quality attribute of
the report, and each will arrive in its own ADD iteration.

Decisions already taken, and the reasoning behind them, are in `docs/adr`.
