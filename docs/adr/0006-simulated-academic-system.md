# 6. Simulate one academic-system boundary for several universities

- **Status:** Accepted
- **Date:** 2026-10-02
- **Amended:** 2026-10-15, the student found by email and the directory read from files (TS10)

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

## Amendment, 2026-10-15: the student is found by email, and the directory is configuration

Two parts of the decision above did not survive the first university registered by configuration
(TS10). The original text stays as written, as ADR 0001 asks.

**The port looks a student up by university and institutional email**, and the profile it answers
carries the student code. The decision above derived the code from the local part of the email and
had the adapter validate it with a rule per university. Both put in Ayni something that belongs to
the university: not every university builds its addresses from its codes (`nora.quispe@...` is as
institutional as `u202400001@...`), and a rule of format per university was one more thing to
configure in Ayni and get wrong. The academic system already knows its students by their address,
so it is asked by it, and Ayni copies the code it answers as it comes, **without checking its
format**. The pair `(tenantId, email)` identifies the profile; `(tenantId, studentCode)` stays unique
in `identity.users` because it is still the university's identifier.

**The simulated adapter reads one file per university**, `<CODE>.json`, from a folder named by the
property `ayni.identity.academic-directory`, **when it looks a student up and never at startup**.
The universities and their students were written in the adapter's code, so a university registered
over HTTP could not have a single student activated until somebody changed that code and deployed it
again, which is exactly what TS10 forbids. With the folder, registering the university and putting
its file there is the whole onboarding; `docker compose` mounts `backend/academic-directory`, which
keeps the four demonstration students.

**Consequences of the amendment**

- Activation and the import of the record call the port with the email; a real HTTP adapter answers
  the same question.
- Ayni has no rule about student codes anywhere. A code the university changes reaches Ayni only
  when the student is activated again, which today does not happen.
- A file that cannot be read is a mistake of the platform and fails the activation as unexpected; a
  university without a file, or an address its file does not have, is refused and the link stays
  unused, as before.
- Reading the file on every lookup costs a few milliseconds per activation, which happens once per
  student.

