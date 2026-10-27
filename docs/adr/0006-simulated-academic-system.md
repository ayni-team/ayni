# 6. Simulate one academic-system boundary for several universities

- **Status:** Accepted
- **Date:** 2026-10-02
- **Amended:** 2026-10-15, the student found by email and the directory read from files (TS10)
- **Amended:** 2026-10-23, the academic system serves the university, its curriculum and its
  students

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

## Amendment, 2026-10-23: the academic system serves the university, its curriculum and its students

The amendment above made the directory configuration, but it still held only students, each with
the names of their courses repeated. Who a university is was typed by the administrator when
registering it, its curriculum was typed by its coordinator (US51), and the demonstration data
invented a UPC with English course names written in the code. Three sources for what one
university knows about itself. The text above stays as written, as ADR 0001 asks.

**The decision.** Each university's source of truth is one document of its academic system, version
1 of the schema in `tools/academic-data/README.md`:

- `university`: code, name, logo, colours, email domains, time zone, minimum teaching grade and the
  current term;
- `careers` and `courses`: the curriculum, each course once with the careers it belongs to and its
  cycle in each;
- `students`: email, student code, name, career, cycle, current term, `status` (`ACTIVE`,
  `GRADUATED`, `INACTIVE`) and the approved courses, named by their code only.

`AcademicSystemPort` keeps `fetchProfile(tenantId, email)`, whose profile now carries the cycle and
the status, and adds `fetchUniversity(code)`, the university and its curriculum. The simulated
adapter still reads the document of a university from `ayni.identity.academic-directory` each time
it is asked, never at startup.

**What reads it.**

| Who | What it takes from the document |
|---|---|
| Registering a university from its academic system (`POST /api/v1/admin/universities/from-academic-system`, the administrator) | name, logo, colours, domains, zone and minimum grade; the administrator gives the initial credits. It reuses the manual registration, which still works |
| Synchronising the catalogue (`POST /api/v1/coordinator/academic-catalog/sync`, a coordinator) | the courses, through `IdentityApi.academicCurriculum`, loaded as US51 loads a list: added, renamed, brought back, never removed. The minimum grade is not synchronised: the registration sets it and the coordinator changes it |
| Activating a student (US38) | the profile; a student whose `status` is not `ACTIVE` is refused and the link stays unused |
| Importing the record (US40, US41) | the approved courses, and the career, the cycle and the term, which are the university's and read only in Ayni |

**A document must agree with itself.** Every time it is read: course codes are unique; emails and
student codes are unique; every email is of a domain of the university; every approved course is a
course of the curriculum of the student's career; and the document is of the university of its file
name. A document that breaks a rule, like one that cannot be read, is a mistake of the platform: the
request fails as unexpected and the log names the university and the rule, never a student.

**A local addition for real people.** `<CODE>.local.json` next to a document adds its students to
the document's, and wins on an email that is in both. Git ignores it, so the lead can sign in with a
real address without personal data reaching the repository.

**The documents are generated.** `tools/academic-data/generate.py`, with the standard library only,
writes `UPC.json` and `USIL.json` and validates them; UPC has the codes of its real curricula, USIL's
are made up in a stable way, and the students are invented. The test university PUCP and its file
are gone, and with them the four demonstration students the amendment above mentions: the
demonstration is told with Ana, Bruno and Carla of UPC and Lucía and Mateo of USIL, and only four of
them are activated at the start (`docs/demo.md`). The logos are in `web/public/logos/`, and
`logoUrl` is a path relative to the origin of the frontend, which the API answers as it is.

**Consequences of the amendment**

- Bringing a university in is putting its document in the folder, registering it from the document
  and synchronising its curriculum: no typing of what the university already says, and no code.
- `IdentityApi` gains `academicCurriculum(tenantCode)`, so skills reads the curriculum without
  knowing identity's classes. No event changed.
- `identity.users.current_cycle` holds the cycle; it is written at activation and at every import.
- The document is read and checked on every lookup, as before: about 100 KB each time a student is
  activated or imports their record, a university is registered or its curriculum synchronised,
  none of which is frequent.
- This is the first of two phases. The second serves the same document over HTTP from a small
  service behind the same port, and nothing in the use cases changes.
