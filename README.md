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

The home page shows whether the API is answering. If it says `UP`, everything works.

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
```

If you prefer running the backend from IntelliJ, start only the database with
`docker compose up postgres` and run `AyniApplication` from the IDE. You need Java 21 for that.

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

Every request carries two headers:

| Header | Holds | Read it in code with |
|---|---|---|
| `X-Tenant-Id` | the university code, for example `UPC` | `TenantContext.require()` |
| `X-User-Id` | who is asking, as a UUID | `CurrentUser.require()` |

In Postman, add both to the collection once and forget about them. Filter every query by the
university.

This is temporary: when sign in is added both values come from the access token, and only
`TenantFilter` and `CurrentUserFilter` change. Until then anyone can claim to be anybody, which is
why an endpoint that answers about a person reads them from `CurrentUser` instead of taking them as
a parameter: an endpoint with no way to name another student cannot be pointed at one.

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
