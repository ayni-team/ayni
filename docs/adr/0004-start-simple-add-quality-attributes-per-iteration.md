# 4. Start from a simple base and add quality attributes one iteration at a time

- **Status:** Accepted
- **Date:** 2026-09-16

## Context

An earlier version of this repository enforced, from the first commit, tenant isolation through
Postgres row level security with two database roles, authentication through Keycloak, a separate
Maven library, an event registry used as an outbox, and twenty architecture rules.

Every one of those decisions was defensible on its own. Together they produced a starting point
that the team could not work in. Two facts decided this record:

- The reviewer of every pull request did not yet fully understand the machinery he was reviewing.
  A design that its own reviewer cannot explain is not being enforced; it is being trusted.
- The team is six people who had not worked together before, for whom this is the first project of
  this size, and whose first task is to map entities and expose endpoints. The cost of that
  machinery was paid on every story, while its benefit only appears in production, which this
  product does not have yet.

There is also an academic reason. The course applies ADD across several iterations, and an
iteration is supposed to take a driver and improve the design against it. Starting with every
quality attribute already satisfied leaves the later iterations with nothing to do but describe what
already exists.

## Decision

The starting point keeps what makes this a modular monolith and defers what protects it in
production.

**Kept, because removing it would change what the system is:**

- thirteen application modules, each owning one database schema;
- no imports between modules: a published interface, or a domain event;
- `ModularityTest`, a single test that fails the build when that rule is broken;
- schema created by Flyway migrations, with Hibernate validating rather than generating;
- a `tenant_id` column on tenant scoped tables, filtered by the application.

**Deferred, each one to its own ADD iteration, each tied to a driver already written in the
backlog:**

| Deferred | Driver | Comes back as |
|---|---|---|
| Authentication and roles | US38, TS03 | Sign in with institutional email, then the access token replaces the `X-Tenant-Id` header |
| Isolation enforced by the database | TS01 | Row level security, an application role without `BYPASSRLS`, and the tenant bound to the connection |
| Reliable delivery between modules | TS04 | Event publication registry used as an outbox, and compensation for the reservation saga |
| Resilience of the payment provider | TS06 | Circuit breaker and explicit degradation behind a port |
| Deployment | — | Production image, cloud architecture and pipeline |

Until each of those arrives, the corresponding property is **not** satisfied, and the report says so
rather than claiming it.

## Consequences

- The team can implement a story end to end on day one: a migration, an entity, a use case, an
  endpoint, a screen. Nothing else stands in the way.
- Each ADD iteration has a real driver and a real change to show, which is what the method asks for.
- **The API has no authentication.** It must not be exposed on a public address until that iteration
  lands.
- **The `X-Tenant-Id` header is trusted.** Anyone can send another university's code, and the
  application will believe it. That is acceptable while the system runs on a developer's machine and
  unacceptable anywhere else, which is what TS01 exists to fix.
- **A forgotten filter leaks data between universities.** Today the only thing preventing it is
  review. This is the strongest argument for bringing row level security back early, and the reason
  it is the first iteration rather than the last.
- Documentation that described the removed mechanisms was not carried over. Records describing a
  system that does not exist are worse than no records.
