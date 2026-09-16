# 2. Start as a modular monolith and extract services selectively

- **Status:** Accepted
- **Date:** 2026-09-01

## Context

The course requires a microservices oriented architecture. Ayni, however, is a system whose
expected load does not justify distributing it: the volume is a few thousand students per
university, and the modules are tightly related around a single credit ledger.

Distributing a domain that is not yet understood produces distributed transactions where a
local one would do, and makes every change require coordinated deployments.

## Decision

The system starts as a modular monolith with hard boundaries between modules, and services are
extracted from it for reasons other than scale.

Boundaries are enforced by two mechanisms rather than by convention:

- one database schema per module, with no foreign keys crossing schemas;
- Spring Modulith, which declares what each module is, and `ModularityTest`, a single test that
  fails the build when a module reaches into another one's internals.

The extraction criteria are fault isolation, risk surface, compute profile and lifecycle. Payments
is the first candidate: the platform is mostly free, so a failing payment gateway must not take
down tutoring, and the compliance surface is smaller when isolated.

## Consequences

- Extracting a module later is a data migration rather than a redesign.
- The team pays the cost of the boundaries from day one: no convenient cross module queries,
  and reporting that spans modules has to go through read projections.
- The claim that the system is ready for microservices is demonstrable, not asserted.
