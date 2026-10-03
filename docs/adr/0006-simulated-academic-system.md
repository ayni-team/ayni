# 6. Simulate one academic-system boundary for several universities

- **Status:** Accepted
- **Date:** 2026-10-02

## Context

Ayni needs academic information that it does not own.
On a student's first access, Identity needs the student's
name, career, current term and approved courses with grades.

During development there is no integration with a real
university academic system.

The simulation must support several universities without
making UPC a special case in the application layer.

## Decision

Identity exposes an `AcademicSystemPort`.

The port receives a `tenantId` and a `studentCode` and
returns an `AcademicProfile` containing:

- full name
- career
- current term
- approved courses
- grade and academic term of each course

A single simulated adapter supports several universities.

Each university has its own student-code validation rule and
its own academic profiles. The pair `(tenantId, studentCode)`
identifies the academic profile.

During activation, the student code is derived from the local
part of the institutional email. The academic adapter validates
that code using the rule configured for the university.

Academic information is read-only in Ayni. The university
academic system remains the source of truth.

## Consequences

- Activation depends on `AcademicSystemPort`, not on the mock.
- Academic record refresh uses the same port.
- Several universities can be simulated by one adapter.
- Student data cannot cross university boundaries.
- A real HTTP academic-system adapter can replace the mock
  without changing the application use cases.
- Unit tests do not require Docker or an external university
  service.