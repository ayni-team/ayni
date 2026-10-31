# Presenting Ayni: the demo profile

The `demo` profile is the application of `dev` plus a clock the platform administrator moves over
HTTP while the application runs. It is how a presentation shows, in minutes, what takes hours or
weeks: the window of a session opening, the presence code being sent, the session closing and
paying its tutor, credits expiring and a recognition request. Nothing of it exists under `dev` or in
a deployment.

- [What the profile changes](#what-the-profile-changes)
- [Starting it](#starting-it)
- [The universities and their people](#the-universities-and-their-people)
- [The history](#the-history)
- [Starting over](#starting-over)
- [Signing in with each role](#signing-in-with-each-role)
- [The clock](#the-clock)
- [Switching persona](#switching-persona)
- [A real mailbox for a few addresses](#a-real-mailbox-for-a-few-addresses)
- [The script of the presentation](#the-script-of-the-presentation)
- [Traps](#traps)
- [Other demo switches](#other-demo-switches)

## What the profile changes

| What | Under `dev` and in a deployment | Under `demo` |
|---|---|---|
| The `Clock` every rule reads | `Clock.systemUTC()` (`config.TimeConfig`) | `config.demo.AdjustableClock`: the real time plus an offset the administrator moves |
| `/api/v1/admin/demo/clock` | does not exist (404) | read, move, set and reset the clock; platform administrator only |
| `/api/v1/admin/demo/personas` and `/login-as` | do not exist (404) | list the active students and coordinators and open a session as any of them; platform administrator only |
| Demonstration data | the six `Demo*Data` of `dev` | the same, through `spring.profiles.group.demo: dev`, plus an active coordinator of UPC and one of USIL (`DemoCoordinatorData`) and the last four weeks of each university (`DemoHistoryData`, see [The history](#the-history)) |
| Expiry of credits, generation of hours (`cron`) | 03:00 and 02:00 UTC of the real night | every minute |
| Jobs at an interval | every minute (audit: every hour) | every 5 seconds (audit: every 10) |
| Session token | 7 days | 365 days |
| Access link | 10 minutes | 10 minutes, by the moved clock |
| Video pass (Jitsi) | signed with the application's time | signed with the real time, same length |

Everything is in `backend/src/main/resources/application-demo.yml`.

**Why the demo includes `dev` through a group** and not by annotating the six `Demo*Data` classes
with `@Profile({"dev", "demo"})`: one line in `application.yml` instead of six classes in six
modules, and whatever `dev` seeds tomorrow is in the demo too, without anybody remembering to
annotate it. The classes that are `demo` and not `dev` are `DemoCoordinatorData`, `DemoHistoryData`
and the `DemoHistory*` adapters it plays the history through.

## Starting it

With Docker, everything at once (the backend, PostgreSQL, Mailpit and the web application):

```bash
SPRING_PROFILES_ACTIVE=demo AYNI_PAYMENTS_RECONCILIATION_DELAY=PT5S docker compose up
# add --profile video to have the Jitsi of the video calls too
```

`docker-compose.yml` passes `SPRING_PROFILES_ACTIVE` through, `dev` when it is not set. The second
variable is there because compose always sets `AYNI_PAYMENTS_RECONCILIATION_DELAY`, and an
environment variable wins over `application-demo.yml`.

From the IDE or Maven, with PostgreSQL and Mailpit from compose:

```bash
docker compose up -d postgres mailpit
cd backend && SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run
```

Start from an empty database when you can (`docker compose down -v` removes it). The demonstration
data is written once, with the dates of the day it was written: on a database seeded days ago, the
credits of Ana expire earlier than the script below says (read them first, see step 7).

The log says so whenever the clock is moved: `The demo clock was moved: the application no longer
reads the real time`.

## The universities and their people

Both universities come from the documents of their academic systems, in `backend/academic-directory`
(ADR 0006; `tools/academic-data/README.md` has the schema and how they are generated). At the first
start `DemoIdentityData` registers **UPC** and **USIL** with what their documents say (name, logo,
colours, domains, time zone and minimum teaching grade: 15 at UPC, 16 at USIL) and five initial
credits for thirty days; `DemoSkillsData` loads the curriculum of each from its document (57 courses
at UPC, 64 at USIL, with their Spanish names). Each document has 28 students; **only the thirteen
people of each university below are activated at the start**, all of them named, with their fixed
identifiers, in `pe.ayni.shared.demo.DemoCast`. Every other student of the documents activates their
account live: ask for a link with their address and open it from Mailpit, and the academic system
answers who they are. One student of each career is no longer enrolled (a graduate, somebody who
left) and is refused with `400`, the link left unused.

Every module seeds its own part through its own use cases, never with SQL: identity activates the
people and takes them as far into the onboarding as the table says, skills ends the onboarding of
the rest, wallet grants the credits and runs the real expiry and expiry warning once, booking
declares the tutors' weeks and books the tutorings ahead, and recognition sets a rule of **three
hours** in each university. The two universities have the same shape, so whatever is shown in one
can be shown in the other.

The balances are the ones under `demo`, right after the first start; under `dev` there is no
history, and the people who book have the credits of their history still in their wallet.

### UPC

| Address | Person | Role | Onboarding | Credits | What they show |
|---|---|---|---|---|---|
| `u202400001@upc.edu.pe` | Ana Torres, Software, cycle 4 | student | `PROFILE` | 10 (every origin, an expired grant and two refunds) | FE-03 from the first step (the web keeps her there, so the rest of hers is seen through the API or once she ends the onboarding): a tutoring ahead and a history with a whiteboard, read and unread notices, a tutor who did not come among them |
| `u202300002@upc.edu.pe` | Bruno Salas, Software, cycle 8 | tutor of 25 courses, every day 15:00-18:00 | `COMPLETED` | 3 (earned) | FE-41 with tutorings ahead, FE-43 (5 and 4 stars), FE-34 with a request **approved**, FE-60 |
| `u202100026@upc.edu.pe` | Mateo Cárdenas, Sistemas de Información, cycle 10 | tutor of 5 courses, weekdays 08:00-11:00, a weekday off in a week | `COMPLETED` | 3 (earned) | FE-41, FE-43, FE-34 with a request **pending** |
| `u202200018@upc.edu.pe` | Daniela Salas, Software, cycle 7 | tutor of 5 courses, weekends 09:00-13:00, a week of pause in two weeks; also books | `COMPLETED` | 5 | FE-43 with a three star rating, FE-34 with a request **pending** |
| `u202300027@upc.edu.pe` | Isabella Tapia, Sistemas de Información, cycle 6 | tutor of 5 courses, two hours a week (Tue and Thu 17:00-18:00); also books | `COMPLETED` | 3 | the tutor who did not come (a `NO_SHOW` incident), FE-12 as a student |
| `u202200019@upc.edu.pe` | Andrés Zegarra, Software, cycle 7 | student | `COMPLETED` | **0** (his grant expired) | FE-30 empty with a history, FE-11 refused for credits |
| `u202300012@upc.edu.pe` | Ana Flores, Software, cycle 5 | student | `COMPLETED` | **1** | FE-11 with the last credit |
| `u202300024@upc.edu.pe` | Julián Arce, Sistemas de Información, cycle 6 | student | `COMPLETED` | **5** | a student as the university leaves them |
| `u202300014@upc.edu.pe` | Valeria Medina, Software, cycle 6 | student | `COMPLETED` | **15** | FE-10 and FE-11 with room to book |
| `u202300033@upc.edu.pe` | Milagros Castillo, Sistemas de Información, cycle 6 | student | `COMPLETED` | 8 (seed, allocated, purchased) | FE-30 with three origins; FE-12 with two ahead, a history with a session she missed, and a cancelled one; FE-24 with a whiteboard |
| `u202400031@upc.edu.pe` | Alejandra Chávez, Sistemas de Información, cycle 4 | student | `COMPLETED` | 6, of which **2 expire in two days** | the expiry warning in FE-30 and FE-60 |
| `u202400028@upc.edu.pe` | Daniela Cornejo, Sistemas de Información, cycle 4 | student | `ACADEMIC_RECORD` | none | FE-03 at the academic record |
| `u202300016@upc.edu.pe` | Paula Palomino, Software, cycle 6 | student | `LEARNING_INTERESTS` | none | FE-03 at the interests |
| `coordinator@upc.edu.pe` | Carlos Rivera | coordinator | | | FE-50 with two requests pending and one approved, FE-51, FE-53 with sessions, ratings and demand nobody covers |

### USIL

| Address | Person | Role | Onboarding | Credits | What they show |
|---|---|---|---|---|---|
| `lucia.paredes@usil.edu.pe` | Lucía Paredes, Software, cycle 4 | student | `PROFILE` | none | FE-03 from the first step |
| `mateo.rios@usil.edu.pe` | Mateo Ríos, Sistemas de Información, cycle 8 | tutor of 30 courses, every day 15:00-18:00 | `COMPLETED` | 3 (earned) | FE-41, FE-43, FE-34 with a request **approved** |
| `joaquin.medina@usil.edu.pe` | Joaquín Medina, Software, cycle 6 | tutor of 5 courses, weekdays 08:00-11:00, a weekday off in a week | `COMPLETED` | 3 (earned) | FE-41, FE-34 with a request **pending** |
| `daniela.tapia@usil.edu.pe` | Daniela Tapia, Sistemas de Información, cycle 6 | tutor of 5 courses, weekends 09:00-13:00, a week of pause in two weeks; also books | `COMPLETED` | 5 | FE-43 with a three star rating, FE-34 with a request **pending** |
| `piero.delgado@usil.edu.pe` | Piero Delgado, Software, cycle 5 | tutor of 5 courses, Tue and Thu 17:00-18:00; also books | `COMPLETED` | 3 | the tutor who did not come |
| `bruno.castillo@usil.edu.pe` | Bruno Castillo, Software, cycle 7 | student | `COMPLETED` | **0** | FE-30 empty with a history |
| `sebastian.gutierrez@usil.edu.pe` | Sebastián Gutiérrez, Software, cycle 4 | student | `COMPLETED` | **1** | FE-11 with the last credit |
| `lucia.salazar@usil.edu.pe` | Lucía Salazar, Sistemas de Información, cycle 6 | student | `COMPLETED` | **5** | a student as the university leaves them |
| `diego.espinoza@usil.edu.pe` | Diego Espinoza, Software, cycle 6 | student | `COMPLETED` | **15** | FE-10 and FE-11 with room to book |
| `rocio.palomino@usil.edu.pe` | Rocío Palomino, Sistemas de Información, cycle 7 | student | `COMPLETED` | 3 (seed, allocated, purchased) | FE-12 with three ahead, history and a cancelled one; FE-24 with a whiteboard |
| `gabriel.navarro@usil.edu.pe` | Gabriel Navarro, Sistemas de Información, cycle 4 | student | `COMPLETED` | 6, of which **2 expire in two days** | the expiry warning |
| `bruno.vargas@usil.edu.pe` | Bruno Vargas, Software, cycle 4 | student | `ACADEMIC_RECORD` | none | FE-03 at the academic record |
| `alejandra.gutierrez@usil.edu.pe` | Alejandra Gutiérrez, Sistemas de Información, cycle 6 | student | `LEARNING_INTERESTS` | none | FE-03 at the interests |
| `coordinator@usil.edu.pe` | Rosa Huamán | coordinator | | | FE-50, FE-51, FE-53 |

**The courses.** Bruno and Mateo Ríos offer every course they approved, and teach those where their
grade reaches the threshold. The other three tutors of each university offer five courses each
(`DemoSkillsData.TEACHES`), so FE-10 finds 36 courses taught at UPC and 40 at USIL, by four tutors,
and leaves the rest with nobody: Arquitectura de Computadoras y Sistemas Operativos (SI643),
Arquitectura de Aplicaciones Web (SI705) and Gerencia de Proyectos de SI/TI (SI427) at UPC, Ingeniería
de Software II (USIL026), Sistemas Operativos (USIL034) and Análisis y Diseño de Algoritmos (USIL033)
at USIL, among others. A search of those answers no offer, which is the demand FE-53 counts; the
students who do not teach have them among their learning interests.

**The tutorings ahead.** Four in each university, confirmed, between two and seven days ahead, so
FE-12 and FE-41 are not empty: Ana with Bruno, Milagros with Mateo Cárdenas and with Isabella,
Isabella with Daniela Salas; at USIL Rocío with Mateo Ríos, with Joaquín and with Piero, and Piero
with Daniela Tapia. They are booked as a student books them: held, confirmed, charged. When a
university has no confirmed tutoring ahead at startup (a database seeded weeks ago), four more are
booked from that day, so the past never fills the whole screen; each time, the students pay the
credit of the hour again. One more of each university, four or five days ahead, is cancelled by its
student (Milagros, Rocío) with the credit back in FE-30.

**Free for a live onboarding.** These students have no account and a good record, so they make a
convincing tutor or student when they activate their account in front of the audience (ask for a
link, open it in Mailpit, walk FE-03):

| University | Address | Person |
|---|---|---|
| UPC | `u202500003@upc.edu.pe` | Carla Mendoza, Sistemas de Información, cycle 3 |
| UPC | `u202300015@upc.edu.pe` | Sofía Cornejo, Software, cycle 6, 22 courses to teach |
| UPC | `u202300011@upc.edu.pe` | Renzo Salas, Software, cycle 6, 21 courses to teach |
| UPC | `u202100021@upc.edu.pe` | Joaquín Palomino, Software, cycle 9, 33 courses to teach |
| UPC | `u202300030@upc.edu.pe` | Sebastián Cárdenas, Sistemas de Información, cycle 6 |
| USIL | `paula.vargas@usil.edu.pe` | Paula Vargas, Sistemas de Información, cycle 6, 25 courses to teach |
| USIL | `santiago.arce@usil.edu.pe` | Santiago Arce, Sistemas de Información, cycle 5 |
| USIL | `rodrigo.palomino@usil.edu.pe` | Rodrigo Palomino, Software, cycle 5 |

Who is refused on purpose: `u202300035@upc.edu.pe` and `piero.ortiz@usil.edu.pe` (no longer enrolled),
`u202300022@upc.edu.pe` and `piero.valdivia@usil.edu.pe` (graduated).

A student activated live gets the university's five initial credits.

**To sign in with your own address** without committing it, create
`backend/academic-directory/UPC.local.json` (ignored by git) with the students to add, in the schema
of the document; only its `students` are read, and an address in both files is the local one. See
`backend/academic-directory/README.md`.

## The history

Nobody enters the sessions the demonstration books, so without help the database would have no
past: nothing to review in FE-24, no rating for FE-43, no hours for FE-34, no requests for FE-50, no
indicators for FE-53. Under `demo` only, `config.demo.DemoHistoryData` plays the last four weeks of
each university when the application is ready, after the tutors have their hours and the
coordinators exist.

**The trick: the clock goes back and the real use cases do the rest.** For each tutoring it takes
the `AdjustableClock` to two days before it, and the student books the first free hours of the
tutor from that day (hold, confirm, charge). Then the clock goes to five minutes before the start
and both enter; to six minutes in, where the presence codes are issued and both confirm theirs; to
three minutes before the end, where both end it, which completes it and pays the tutor; and to half
an hour later, where each one rates the other. A tutor who does not come is abandoned at the ten
minute mark, and the student is refunded and told; a student who does not come pays the hour. Each
step is the use case of the endpoint a person calls, or of the job that would have run then, so the
chained ledger, the audit log, the event registry, the notices and the indicators record it as they
would record a real week, dated with the moved clock.

**Every module plays its own part.** `config` cannot call another module's use cases, so each module
implements a small port of `pe.ayni.shared.demo` (`DemoBookings`, `DemoSessions`, `DemoRatings`,
`DemoRecognition`, `DemoNotices`, `DemoSearches`, `DemoCourses`) with a `DemoHistory*` class of its
own `infrastructure`, under `demo` only; the history only says in what order and at what time.

**The presence code** travels only by email, and the history does not read Mailpit. The sessions
module reads it where the acceptance tests read it, from `PresenceCodeIssued` as it is published,
and only for the session the history is confirming at that moment; the code is then confirmed with
the real use case, which compares it with its hash. Nothing new is exposed over HTTP, and the class
that listens exists only under `demo`. The emails with the codes do reach Mailpit, with the rest.

What each university ends with (the same in both, with the people of its table):

- six sessions **completed** by three tutors, three hours each (one of two hours and one of one),
  with ratings of five, four and three stars, tags and comments, and the tutor's three answers about
  the student; two of them with drawings on the whiteboard;
- one session **abandoned** because the tutor did not come (Ana and Rocío are refunded and get the
  notice) and one because the student did not (Milagros and Rocío pay the hour);
- twelve searches of the courses nobody teaches, spread over the weeks;
- the three tutors ask for the recognition of their three hours four days ago; two days ago the
  coordinator approved the request of Bruno (Mateo Ríos at USIL) and left the other two pending;
- Ana and Bruno have opened their four oldest notices and left the rest unread.

**The clock ends exactly where it was**, the same offset from the real time, whatever happened, and
the log says so: `Demo history done in 9877 ms; the clock is back at PT0S`. A tutoring that cannot be
played is logged and the others are still played; a failure never keeps the application from
starting. While the history plays, the jobs of the profile keep running against the moved clock,
which is what they would have done at that time.

**It runs once.** A university that already has a tutoring that started before today has a past, and
is left alone; so is a database where people already have their own. The integrity checks of
`/api/v1/coordinator/ledger/integrity` and `/api/v1/coordinator/audit-log/integrity` answer `intact`
afterwards, which `DemoSeedAcceptanceTest` checks along with the rest of this section.

**What it costs.** The history plays in about ten seconds on a laptop, both universities together;
the rest of the seeding adds about five more to the first start. A second start plays nothing.

## Starting over

The data is dated by the day of the first start: the history is "the last four weeks" of that day.
To start over, from the root of the repository:

```bash
docker compose down -v
SPRING_PROFILES_ACTIVE=demo AYNI_PAYMENTS_RECONCILIATION_DELAY=PT5S docker compose up
```

`down -v` removes the volume of PostgreSQL, and with it every account, credit and tutoring; the next
start seeds everything again. Without it the people keep what they had, and only the tutorings ahead
are booked again when none are left.

## Signing in with each role

Everybody signs in by link, as always: ask for one, open Mailpit at <http://localhost:8025>, copy
the `token` of the link and confirm it.

```bash
curl -s -X POST localhost:8080/api/v1/access/request \
  -H 'Content-Type: application/json' -d '{"email": "admin@ayni.local"}'
# 202; the email with the link is in Mailpit
curl -s -X POST localhost:8080/api/v1/access/confirm \
  -H 'Content-Type: application/json' -d '{"token": "<the token of the link>"}'
# {"sessionToken": "...", "role": "PLATFORM_ADMIN", ...}
```

| Role | Address | Who they are |
|---|---|---|
| Platform administrator | `admin@ayni.local` | moves the clock |
| Coordinator of UPC | `coordinator@upc.edu.pe` (`AYNI_DEMO_COORDINATOR_EMAIL` to change it; it must be of a domain of UPC) | Carlos Rivera, active from the start, no invitation needed |
| Coordinator of USIL | `coordinator@usil.edu.pe` (`AYNI_DEMO_USIL_COORDINATOR_EMAIL` to change it; it must be of a domain of USIL) | Rosa Huamán, active from the start |
| Student | `u202400001@upc.edu.pe` | Ana Torres, with credits of every origin |
| Student and tutor | `u202300002@upc.edu.pe` | Bruno Salas, teaches Diseño de Base de Datos among others, free every day 15:00 to 18:00 Lima time |
| Students of USIL | `lucia.paredes@usil.edu.pe`, `mateo.rios@usil.edu.pe` | Lucía Paredes and Mateo Ríos, the student and the tutor of the second university |
| Everybody else | the tables of [The universities and their people](#the-universities-and-their-people) | `login-as` (below) is quicker than a link for them |

**Sign the four of them in before moving the clock**, and keep the four tokens:

```bash
export ADMIN=<token> COORD=<token> ANA=<token> BRUNO=<token>
```

## The clock

All four answer `{"now": ..., "realNow": ..., "offset": ...}`: the time every rule reads, the real
time, and the ISO 8601 duration between them. The clock keeps running from wherever it is put. It
lives in memory: restarting the backend takes it back to the real time.

```bash
# Read it
curl -s localhost:8080/api/v1/admin/demo/clock -H "Authorization: Bearer $ADMIN"

# Move it by a duration, forwards or backwards (days, hours, minutes, seconds: P1D, PT2H, -PT30M)
curl -s -X POST localhost:8080/api/v1/admin/demo/clock/advance -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"by": "PT2H"}'

# Put it at a UTC instant
curl -s -X PUT localhost:8080/api/v1/admin/demo/clock -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"at": "2026-10-22T19:50:00Z"}'

# Back to the real time
curl -s -X DELETE localhost:8080/api/v1/admin/demo/clock -H "Authorization: Bearer $ADMIN"
```

A value that is not ISO 8601 (`"two hours"`, `"P1W"`, `"2026-10-22"`), a missing one, or one that
takes the clock more than ten years from the real time answers `400` and leaves the clock where it
was. A student or a coordinator gets `403`; without a session, `401`.

## Switching persona

To change person without asking for a link and reading it in Mailpit each time, the administrator
lists the people and opens a session as any of them. Both routes answer only a platform
administrator, and only under `demo`.

```bash
# The active students and coordinators of every university, by university, then role, then name
curl -s localhost:8080/api/v1/admin/demo/personas -H "Authorization: Bearer $ADMIN"
# [{"email": "coordinator@upc.edu.pe", "fullName": "Carlos Rivera", "role": "COORDINATOR",
#   "universityCode": "UPC", "universityName": "...", "career": null, "onboardingStep": null}, ...]

# Act as one of them: the answer is the one of POST /api/v1/access/confirm
curl -s -X POST localhost:8080/api/v1/admin/demo/login-as -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"email": "u202400001@upc.edu.pe"}'
# {"sessionToken": "...", "tenantId": "UPC", "userId": "...", "role": "STUDENT", "expiresAt": "..."}

# Keep the token of each persona, then use it like any session
export ANA=<the sessionToken above>
curl -s localhost:8080/api/v1/me -H "Authorization: Bearer $ANA"
```

What to know:

- **It is the session a link opens**, with the same duration (a year here, see "Traps"), a token
  that travels only in this answer and a table that keeps only its hash. It consumes and creates no
  access link and sends no email, so Mailpit stays quiet.
- **Acting as someone again opens another session** for them; the earlier ones keep working until
  they expire, or until their holder signs out (`POST /api/v1/access/logout`).
- **Only active people.** A coordinator who has not accepted their invitation, or anyone restricted,
  is neither listed nor found: `login-as` answers `404` for them and for an email nobody has, and
  `400` for something that is not an email. A student or a coordinator calling either route gets
  `403`; without a session, `401`.
- The list is the one place where an administrator reads people. It exists so that a presentation
  can choose whom to be, and for that reason it is not there outside `demo`.
- The log has one line for each session opened this way, with the university and the role and
  without the address.

## A real mailbox for a few addresses

Everything the demonstration sends goes to Mailpit, which is right for the invented students: they
carry the domain of real universities, and nobody should receive their emails. To receive the link
in a real mailbox, for yourself, list that address and give a relay that delivers to it:

```bash
AYNI_REAL_RELAY_ALLOWED_RECIPIENTS=you@example.org \
AYNI_REAL_RELAY_HOST=smtp.example.org AYNI_REAL_RELAY_PORT=587 \
AYNI_REAL_RELAY_USERNAME=<smtp user> AYNI_REAL_RELAY_PASSWORD=<smtp password> \
AYNI_REAL_RELAY_STARTTLS=true \
SPRING_PROFILES_ACTIVE=demo docker compose up
```

Register with that address (a domain of a university, or one added to it with
`PUT /api/v1/admin/universities/{code}/email-domains`) and the link arrives in your mailbox; any
other address still arrives in Mailpit, whatever its domain. The list is compared address by
address, without regard to case. The list and the host go together: one without the other stops the
backend from starting. If the relay is down, the email of an allowed address fails (its notice says
so) and is not sent to Mailpit in its place. The log counts the emails that left through each server
and never prints an address. `deploy/README.md`, "Mail", explains it for a deployment.

## The script of the presentation

The steps below use tomorrow as `DAY` and Bruno's hour at 15:00 in Lima, which is 20:00 UTC. Set
`DAY` to tomorrow's date before starting:

```bash
export DAY=2026-10-22
```

### 1. Book

Ana looks for a tutor of Diseño de Base de Datos tomorrow, holds Bruno's hour and books it. The
courses come from the document of UPC, so their identifiers are new on every database: read it from
the catalogue first and keep it as `COURSE`.

```bash
curl -s "localhost:8080/api/v1/catalog?q=base%20de%20datos" -H "Authorization: Bearer $ANA"
export COURSE=<the id of Diseño de Base de Datos, course code SI400>
curl -s "localhost:8080/api/v1/search/offers?catalogItemId=$COURSE&from=${DAY}T13:00:00Z&to=${DAY}T23:59:00Z" \
  -H "Authorization: Bearer $ANA"
curl -s -X POST localhost:8080/api/v1/bookings/holds -H "Authorization: Bearer $ANA" \
  -H 'Content-Type: application/json' \
  -d "{\"tutorId\": \"22222222-2222-4222-8222-222222222222\", \"start\": \"${DAY}T20:00:00Z\", \"hours\": 1}"
curl -s -X POST localhost:8080/api/v1/bookings -H "Authorization: Bearer $ANA" \
  -H 'Content-Type: application/json' \
  -d "{\"tutorId\": \"22222222-2222-4222-8222-222222222222\", \"catalogItemId\": \"$COURSE\", \"start\": \"${DAY}T20:00:00Z\", \"hours\": 1, \"needDescription\": \"Normal forms before Friday's exam\"}"
```

**What to see:** the search lists Bruno at 20:00 UTC; the booking answers `201` with its
`id` (keep it as `BOOKING`); `GET /api/v1/wallet` as Ana shows one credit less, taken from
the group closest to expiring.

### 2. The window of the session opens

```bash
curl -s "localhost:8080/api/v1/bookings/mine" -H "Authorization: Bearer $ANA"   # joinable: false
curl -s -X PUT localhost:8080/api/v1/admin/demo/clock -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d "{\"at\": \"${DAY}T19:50:00Z\"}"
curl -s "localhost:8080/api/v1/bookings/mine" -H "Authorization: Bearer $ANA"   # joinable: true
curl -s "localhost:8080/api/v1/sessions?bookingId=$BOOKING" -H "Authorization: Bearer $ANA"
```

**What to see:** ten minutes before the start the booking turns `joinable` (the window opens fifteen
minutes before). The last call answers the `sessionId` (keep it as `SESSION`) with `joinOpensAt`.
Both join:

```bash
curl -s -X POST localhost:8080/api/v1/sessions/$SESSION/join -H "Authorization: Bearer $ANA"
curl -s -X POST localhost:8080/api/v1/sessions/$SESSION/join -H "Authorization: Bearer $BRUNO"
```

The second answer has `status: IN_PROGRESS`, both arrivals, and a `video` pass when Jitsi runs.

### 3. The presence code

```bash
curl -s -X POST localhost:8080/api/v1/admin/demo/clock/advance -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"by": "PT16M"}'
```

**What to see:** the clock is at 20:06 UTC, six minutes into the session. Within five seconds two
emails with a six digit code arrive in Mailpit, one for each. Each confirms their own:

```bash
curl -s -X POST localhost:8080/api/v1/sessions/$SESSION/presence -H "Authorization: Bearer $ANA" \
  -H 'Content-Type: application/json' -d '{"code": "<Ana'"'"'s code>"}'
curl -s -X POST localhost:8080/api/v1/sessions/$SESSION/presence -H "Authorization: Bearer $BRUNO" \
  -H 'Content-Type: application/json' -d '{"code": "<Bruno'"'"'s code>"}'
```

### 4. The session closes

Either both end it:

```bash
curl -s -X POST localhost:8080/api/v1/sessions/$SESSION/end -H "Authorization: Bearer $ANA"
curl -s -X POST localhost:8080/api/v1/sessions/$SESSION/end -H "Authorization: Bearer $BRUNO"
```

or the clock goes past fifteen minutes after the booked hour and the closing job closes it within
five seconds:

```bash
curl -s -X POST localhost:8080/api/v1/admin/demo/clock/advance -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"by": "PT70M"}'
```

**What to see:** `GET /api/v1/sessions/$SESSION` answers `status: COMPLETED`.

### 5. The tutor's credits

```bash
curl -s localhost:8080/api/v1/wallet -H "Authorization: Bearer $BRUNO"
```

**What to see:** an `EARNED` group of one credit that never expires, and `earnedTotal` one higher.

### 6. Credits expire

```bash
curl -s localhost:8080/api/v1/wallet -H "Authorization: Bearer $ANA"
```

Ana's `SEED` group expires thirty days after the data was written, at midnight UTC: read its
`expiresAt`. Move the clock past it, a little more than a month:

```bash
curl -s -X POST localhost:8080/api/v1/admin/demo/clock/advance -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"by": "P31D"}'
```

**What to see:** the balance stops counting the group at once (it only counts groups that are
alive). Within a minute, when the expiry job runs at the next full minute of the real time,
`GET /api/v1/wallet/movements` shows a `DEBIT` with reason `EXPIRY` for what was left of it, and the
log says `Expired N credits in UPC`. Her `ALLOCATED` group (ninety days) and her `EARNED` and
`PURCHASED` credits stay.

### 7. Recognition

```bash
curl -s localhost:8080/api/v1/recognition/progress -H "Authorization: Bearer $BRUNO"
curl -s localhost:8080/api/v1/coordinator/recognition/rule -H "Authorization: Bearer $COORD"
```

**What to see:** under `demo` Bruno's three hours of the history are already in an approved request,
so only the session of step 4 counts: `earnedHours: 1` of the `requiredHours: 3` of the rule `dev`
seeds, and `canRequest` is false (under `dev`, without history, the same). For the presentation the
coordinator lowers it to one hour, and Bruno asks:

```bash
curl -s -X PUT localhost:8080/api/v1/coordinator/recognition/rule -H "Authorization: Bearer $COORD" \
  -H 'Content-Type: application/json' -d '{"minimumHours": 1}'
curl -s -X POST localhost:8080/api/v1/recognition/requests -H "Authorization: Bearer $BRUNO"
curl -s localhost:8080/api/v1/coordinator/recognition/requests -H "Authorization: Bearer $COORD"
curl -s -X POST localhost:8080/api/v1/coordinator/recognition/requests/<id>/decision \
  -H "Authorization: Bearer $COORD" -H 'Content-Type: application/json' \
  -d '{"decision": "APPROVE", "reason": "One hour taught, as the rule of the presentation asks"}'
curl -s localhost:8080/api/v1/recognition/requests/mine -H "Authorization: Bearer $BRUNO"
```

**What to see:** the request is `201` with the session of step 4; the coordinator sees it in the
queue; after the decision Bruno reads `APPROVED` and the reason.

### 8. Back to the real time

```bash
curl -s -X DELETE localhost:8080/api/v1/admin/demo/clock -H "Authorization: Bearer $ADMIN"
```

What happened while the clock was ahead stays: the expired credits, the closed session, the
approved request. To start over, start from an empty database.

## Traps

- **Access links expire with the clock.** A link lives ten minutes of the application's time, so a
  link asked for before a jump of more than ten minutes is dead when it arrives (`400`). Sign
  everybody in before moving the clock, or ask for the link again after the jump.
- **Sessions last a year here, but not forever.** `ayni.identity.session-ttl` is `P365D` under
  `demo` so that jumping weeks ahead does not sign anybody out (with the seven days of `dev`, a jump
  of a week would sign the administrator out of the clock itself). A jump past a year after signing
  in does; sign in again, links work at any time of the clock.
- **A cron runs at the real time.** The expiry of credits and the generation of hours run at the
  second zero of every real minute, not at 03:00 and 02:00 of the clock: after a jump, wait up to a
  minute. The jobs at an interval (holds, presence codes, closing, absences, payments) react within
  five seconds, the audit within ten.
- **Jump past a deadline and it is gone.** A session both did not join before ten minutes after the
  start is abandoned within five seconds; a presence code lives fifteen minutes; a hold lives five.
  Move the clock in small steps around a session.
- **Going backwards does not undo anything.** Rows written with a moved clock keep their dates.
  Moving back is for showing a window again, not for repairing a run.
- **The data of `dev` is dated by its first start.** See step 6: read the dates before jumping.
- **The demonstration data is written once, not kept in sync with the code.** A volume seeded
  [Starting over](#starting-over).
- **Ana already has tutorings.** One with Bruno two or more days ahead and a cancelled one; the
  hour of tomorrow in step 1 is free. FE-03 still shows her at the first step of the onboarding.
  before a change to the curriculum, the academic documents or the catalogue (ADR 0006) keeps what
  it already wrote: `DemoIdentityData` and `DemoSkillsData` skip a university, a person or a
  curriculum that is already there, so a course the current code would seed differently, or a
  record that looks inconsistent with what the screens expect, is usually old data rather than a
  bug. If what you see looks wrong, `docker compose down -v` and start from an empty database before
  reporting it.
- **Video calls** check the pass against the real time, so the pass is signed with the real time
  under `demo` (`ayni.sessions.video.sign-with-real-time`), keeping its five minutes and never
  passing the end of the session by the application's clock. The `expiresAt` of the answer stays in
  the application's time, like every other instant of it.
- **Under compose, environment variables win** over `application-demo.yml`; see "Starting it".

## Other demo switches

Every switch of the demonstration lives under `/api/v1/admin/demo/`, is answered only under the
`demo` profile and only to a platform administrator, and adds its own section here.

### The payment provider (TS06)

The simulated payment provider answers what `ayni.payments.simulated-outcome` (the first charge of a
purchase) and `ayni.payments.simulated-resolution-outcome` (the later check of a pending purchase)
say. Under `demo` the administrator changes them while the application runs, from the next call on,
and reads or closes the circuit in front of the provider. The values are `CONFIRMED`, `REJECTED`,
`PENDING` and `UNAVAILABLE`; anything else answers `400` and changes nothing. Like the clock, the
state lives in memory: a restart takes it back to the properties.

```bash
# Read both outcomes and the circuit: CLOSED, OPEN or HALF_OPEN, the failures in a row, and until
# when it stays open
curl -s localhost:8080/api/v1/admin/demo/payments -H "Authorization: Bearer $ADMIN"

# Switch one outcome or both (at least one)
curl -s -X PUT localhost:8080/api/v1/admin/demo/payments -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"chargeOutcome": "REJECTED"}'

# Close the circuit without waiting for its 30 seconds
curl -s -X POST localhost:8080/api/v1/admin/demo/payments/circuit/reset -H "Authorization: Bearer $ADMIN"
```

A student or a coordinator gets `403`; without a session, `401`.

Ana buys one credit the same way in every step below, with a new `Idempotency-Key` each time:

```bash
buy() {
  curl -s -X POST localhost:8080/api/v1/payments/purchases -H "Authorization: Bearer $ANA" \
    -H "Idempotency-Key: $(uuidgen)" -H 'Content-Type: application/json' -d '{"credits": 1}'
}
```

Start with the provider confirming (`{"chargeOutcome": "CONFIRMED", "resolutionOutcome":
"CONFIRMED"}`, which is what the properties say by default) and `buy` once: `201`, `CONFIRMED`, and
`GET /api/v1/wallet` shows the credit.

#### A rejected purchase

```bash
curl -s -X PUT localhost:8080/api/v1/admin/demo/payments -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"chargeOutcome": "REJECTED"}'
buy
```

**What to see:** `201` with `status: FAILED` and no credit in the wallet. A rejected attempt does not
count against the monthly allowance of five credits, so Ana can try again after the next switch.

#### A pending purchase that is resolved later

The provider takes the charge without a final answer, and keeps saying so when it is asked again:

```bash
curl -s -X PUT localhost:8080/api/v1/admin/demo/payments -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"chargeOutcome": "PENDING", "resolutionOutcome": "PENDING"}'
buy                                                # 202, status PENDING, "do not retry"
curl -s localhost:8080/api/v1/payments/purchases -H "Authorization: Bearer $ANA"   # PENDING
```

Now the provider decides. The reconciliation job asks about every pending purchase (every five
seconds under `demo`, see "Starting it"):

```bash
curl -s -X PUT localhost:8080/api/v1/admin/demo/payments -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"resolutionOutcome": "CONFIRMED"}'
sleep 6
curl -s localhost:8080/api/v1/payments/purchases -H "Authorization: Bearer $ANA"   # CONFIRMED
curl -s localhost:8080/api/v1/wallet -H "Authorization: Bearer $ANA"
```

**What to see:** the purchase turns `CONFIRMED`, the wallet has one credit more (`PURCHASED`, never
expires) and Mailpit has the confirmation email. A pending purchase keeps its place in the monthly
allowance meanwhile. `{"resolutionOutcome": "REJECTED"}` instead shows the other ending: the
purchase turns `FAILED` and the allowance is released.

#### The provider goes down

Both outcomes `UNAVAILABLE`, so that neither the purchase nor the reconciliation reaches it:

```bash
curl -s -X PUT localhost:8080/api/v1/admin/demo/payments -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"chargeOutcome": "UNAVAILABLE", "resolutionOutcome": "UNAVAILABLE"}'
buy; buy; buy
curl -s localhost:8080/api/v1/admin/demo/payments -H "Authorization: Bearer $ADMIN"
```

**What to see:** each `buy` answers `202` with `providerUnavailable: true` and the guidance that
purchases are temporarily unavailable and must not be retried. After the third, the circuit reads
`"state": "OPEN"` with `consecutiveFailures: 3` and `openUntil` thirty seconds ahead: from then on a
purchase is refused without asking the provider.

Everything else keeps working, because nothing else depends on the provider:

```bash
curl -s "localhost:8080/api/v1/search/offers?catalogItemId=$COURSE&from=${DAY}T13:00:00Z&to=${DAY}T23:59:00Z" \
  -H "Authorization: Bearer $ANA"
curl -s localhost:8080/api/v1/bookings/mine -H "Authorization: Bearer $ANA"
curl -s localhost:8080/api/v1/wallet -H "Authorization: Bearer $ANA"
```

**What to see:** search, bookings and the wallet answer as usual. To recover, bring the provider
back and close the circuit instead of waiting for it:

```bash
curl -s -X PUT localhost:8080/api/v1/admin/demo/payments -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"chargeOutcome": "CONFIRMED", "resolutionOutcome": "CONFIRMED"}'
curl -s -X POST localhost:8080/api/v1/admin/demo/payments/circuit/reset -H "Authorization: Bearer $ADMIN"
sleep 6
curl -s localhost:8080/api/v1/payments/purchases -H "Authorization: Bearer $ANA"
```

**What to see:** `state: CLOSED`, and within five seconds the three pending purchases are
`CONFIRMED` by the reconciliation. Without the reset, the circuit lets one probe through when its
thirty seconds pass: it reads `OPEN` until the next call, and closes if that probe is answered.

Traps of this section:

- **Five credits a month.** Pending purchases count against the allowance of five confirmed credits
  per student and UTC month, rejected ones do not. The steps above add up to exactly five (the
  first purchase, the pending one and the three of the outage); for a second run use another
  student or start from an empty database.
- **Leave the provider confirming.** `PENDING` or `UNAVAILABLE` left on makes the next person's
  purchase stay pending. A restart also puts it back.
- **Do not leave only the first outcome `UNAVAILABLE`.** The reconciliation job would then confirm
  the pending purchases while the circuit is still closed, and the failures in a row would start
  over before reaching three.
