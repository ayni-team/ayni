# Domain flows, from the simplified event storming session

This is the product as its owner described it, written down flow by flow. It is the source for the
data model in `../database/data-model.md`, for the module map and for the ViewPoints diagrams of
chapter IV.

**Notation.** Each flow lists who acts, what they ask for, what became true afterwards, and what the
system decides on its own.

| Symbol | Meaning |
|---|---|
| **Actor** | who triggers the action |
| `Command` | what is asked for |
| **Event** | a fact, in past tense: it already happened |
| *Policy* | "whenever X, then Y", decided by the system, nobody asks for it |
| ▸ Read model | a view built to be read, not the source of truth |
| ⇗ External | something outside Ayni |

---

## 0. Actors and boundaries

| Actor | Scope | Must not |
|---|---|---|
| **Ayni administrator** | the platform, across universities | see academic or personal data of any student |
| **Coordinator** | one university | act on another university |
| **Student** | one university, both roles at once | — |
| ⇗ **Academic system (mock)** | one university | — |
| ⇗ **Jitsi** | the live session | — |

The administrator's restriction is a real architectural constraint, not a screen decision. The
administration portal answers with counts and aggregates, never with rows describing a person. It is
worth writing as a quality attribute scenario: *an administrator queries any endpoint of the
platform; no response contains the name, email, course or session of an identifiable student.*

There is no single account type for students. Everyone can learn and teach; the two roles are
interests configured on one profile, not two kinds of user.

---

## 1. Opening a university

**Ayni administrator** `RegisterUniversity` (name, logo, colours, identifying data, institutional
email domains, initial credit grant and how long it lasts)
→ **UniversityRegistered** → **TenantProvisioned**

*Policy: whenever a university is registered, its space is created and its email domains become the
way its students are recognised.*

**Ayni administrator** `InviteCoordinator` (email only)
→ **CoordinatorInvited** → *Policy: an access link is sent* → **CoordinatorActivated**

At least one coordinator is required when the university is created; more can be added later. A
university can also be suspended.

**Who owns the credit policy.** The administrator sets the first one when opening the university.
The coordinator adjusts it afterwards. Neither overwrites the other: a new policy supersedes the
previous one, which stays readable.

---

## 2. Getting in

**There are no passwords in Ayni.** Identity is the institutional mailbox, so proving access to that
mailbox is the authentication. This applies to students, coordinators and the administrator alike:
one flow, no exceptions, nothing to reset or leak.

**Anyone** `RequestAccess` (institutional email)

*Policy: the domain decides the university.* If no university claims that domain, the answer is that
the institution is not affiliated, and **no account is created**.

→ **AccessRequested** → *Policy: a single use link is sent, valid for minutes* → **AccessGranted**

*Policy: following the link opens a long lived session.* If the session were short, every day would
begin by waiting for an email.

*Policy: on a first entry, the academic system is queried using the student code.*

⇗ **Academic system (mock)**, keyed by student code, returns name, career, current term, approved
courses and the grade of each. → **AcademicProfileImported**

*Policy: whenever a profile is imported, the university's grant is placed in the student's wallet,
with an expiry date computed from the policy's validity.* → **InitialCreditsGranted**

**Consequence to design for:** every entry depends on email delivery. In development the link is
written to the log or returned by an endpoint enabled only in that profile, so that testing and
demonstrating the system never depend on a real message arriving on time.

---

## 3. Setting up the profile

**Student** `CompleteProfile` (photo, description)
**Student** `DeclareLearningInterests` (courses and skills they want help with)
**Student** `DeclareTeachingInterests` (courses and skills they could teach)

*Policy: the current term suggests what to learn.* A first year student is offered first year
courses rather than an empty search box.

*Policy: an approved course whose grade reaches the university's threshold enables that skill
immediately.* → **SkillEnabled** (path: academic record)

**The catalogue has two scopes.** Tools such as Excel, Power BI, Photoshop or English are **global**:
they ship with Ayni and are the same for everyone. Courses belong to **the university** that teaches
them. Nothing is free text.

That split decides how something gets enabled: a course can be enabled by the grade the academic
system reports, while a global tool never can, and always goes through reviewed evidence.

---

## 4. Finding and booking a tutoring session

▸ **Read model: the offers.** Given a skill and a date range, which hours have an enabled tutor
free, and who each tutor is with their standing in that skill.

**Student** `SearchAvailability` (skill, dates)
**Student** `ChooseOffer` (day, hour, tutor, one or more consecutive hours)
**Student** `ConfirmBooking` (need description, required)

→ **BookingConfirmed** → **CreditsCharged** → **HoursTaken**

*Policies that apply here:*
- one credit per hour; two consecutive hours cost two credits;
- a booking covers **contiguous** hours with the same tutor. Monday at five and Wednesday at seven
  are two separate bookings;
- credits closest to expiring are spent first;
- an hour already taken cannot be taken again;
- the whole confirmation is one transaction: either the credits move and the hours are reserved, or
  nothing happens.

**When several tutors are free at the same hour, all of them are shown and the student picks**,
seeing the rating of each. An earlier proposal assigned the best rated tutor automatically; it was
rejected, and rightly: it would have funnelled every booking to the same few people and left new
tutors with nothing.

The need description is mandatory because the tutor reads it before the session.

---

## 5. The session

**Student or tutor** `JoinSession` (available as the hour approaches)
→ **ParticipantJoined** → **SessionStarted**

⇗ **Jitsi** carries video, audio and screen sharing, embedded in a view of its own. Cameras need not
be on. A shared whiteboard runs alongside: both write at once, and it can be downloaded afterwards.

### The presence check

*Policy: five minutes after the session starts, each participant receives a six digit code by email
and must type it into the platform to continue.* → **PresenceConfirmed**

This is the strongest anti fraud mechanism in the product, and better than analysing behaviour
afterwards, because somebody who is not really in the session simply does not have the code. It
prevents rather than detects.

The code is single use, expires, and the number of attempts is capped.

> **Open, and it needs your decision.** What happens when somebody does not confirm. *Proposal:* the
> session ends as `UNVERIFIED`, the tutor earns **no** credits, the student is refunded and the audit
> is notified. Earned credits are what backs a recognition request, so handing them out without proof
> of presence would hollow out the mechanism. Treating it as a no show instead would punish somebody
> whose email merely did not arrive.

*Policy: as the end approaches, both participants are warned.*

**Either participant** `EndSession` → **SessionEnded** → **SessionCompleted**
→ **AttendanceRecorded** (who was present, and for how long)

*Policy: whenever a verified session completes, the tutor receives one credit per hour taught, as
earned credits.* → **CreditsEarned**

*Policy: whenever a session completes, both may rate it.* → **SessionRated**

The student rates the tutoring with stars and tags. The tutor answers three facts instead: whether
the other person was punctual, whether the connection held, and whether the session could flow. That
is deliberately not an opinion about the person, and it feeds the audit: whoever never turns up
leaves a trail.

---

## 6. When things do not go well

*Policy: cancelling always refunds the student*, and the credits return to the group they came from,
with the expiry they had.

*Policy: a cancellation within twelve hours of the start is recorded against whoever made it.* The
same twelve hours the backlog already gives the student, so there is one rule to explain and not
two.

**A tutor removing availability over a confirmed booking is cancelling that session.** It refunds
the student and is recorded on the tutor's record. Removing availability that nobody booked, or
withdrawing a skill nobody reserved, costs nothing.

Records are kept as incidents, one row per event, never as a counter: a counter loses the story and
cannot be audited.

---

## 7. Offering tutoring and publishing availability

**Student** `OfferSkill` (chosen from the catalogue, by category)

*Policy: a skill can only be offered if it is enabled*, by grade or by reviewed evidence.

**Tutor** `DeclareWeeklyAvailability` (ranges on a weekly timetable, for example Monday 17:00–19:00)
→ **AvailabilityPublished** → **HoursGenerated**

The ranges repeat every week. **Availability belongs to the tutor, not to a skill**: being free on
Monday at five means being free, and any enabled skill can be taught in that hour. Forcing one
timetable per course would be absurd.

*Policy: the hours of a tutor with no enabled skill are not published.*

---

## 8. Validating a new skill

For abilities the academic system cannot confirm: English, Photoshop, Power BI.

**Student** `RequestSkillValidation` (skill, evidence: certificates, records, documents)
→ **EvidenceSubmitted**

**Coordinator of the student's own university** `ReviewEvidence` → **SkillEnabled** (path: reviewed
evidence) or **ValidationRejected** with a reason.

The reviewer is the coordinator and not Ayni: they already review recognition requests, they know
their own institution's standards, and it keeps the administrator away from academic judgement,
which is exactly what the administrator is not allowed to touch.

---

## 9. Asking the university for recognition

**This is not a certificate.** Ayni does not certify anything by itself: it assembles a file and the
university decides.

*Policy: on reaching the university's criteria, the student may request recognition.*

**Student** `SubmitRecognitionRequest`
→ **RecognitionRequested**, carrying hours taught, the sessions with their dates and skills, the
attendance of each, ratings received, and whatever the audit has flagged. The figures are frozen at
submission: what the coordinator reads must not move while they read it.

**Coordinator** `ReviewRecognitionRequest` → **RecognitionApproved** or **RecognitionRejected**,
always with a reason.

**The credits are not consumed.** Asking to be recognised must not cost the tutor what they earned.
What the request consumes are the **sessions**: each one can back exactly one request, ever. That is
what stops the same hours from being presented twice, and it leaves the balance untouched and
spendable.

---

## 10. Audit and fraud

*Policy: the system analyses behaviour and flags what looks wrong,* without stopping anyone while it
runs.

Signals: how many sessions someone gives, the same pair meeting again and again, unusual frequency,
real connected time against booked time, a whiteboard nobody drew on, and failed presence checks.

→ **AnomalyFlagged**, with the sessions that support it.

The coordinator sees these flags **when reviewing a recognition request**, which is the moment they
matter: the point is to stop a reward that has nothing behind it.

There is no user-facing reporting of sessions. Detection is automatic.

---

## Decisions taken in these sessions

1. **One credit equals one hour**, and blocks are one hour long.
2. **Purchased credits do not expire.** Only what the university grants does, and it expires on a
   date computed from a validity in days, not at the end of an academic term.
3. **Two accreditation paths only**: automatic by grade for courses, reviewed evidence for global
   tools. Professor endorsement, in-platform assessment and peer endorsement leave the scope.
4. **No user reports.** Audit is automatic, and `trust` merged into `audit`. Twelve modules.
5. **Recognition is a request reviewed by the university**, not a certificate issued by Ayni, and it
   consumes sessions rather than credits.
6. **Administration of the platform does not see academic or personal data.**
7. **No passwords.** A single use link by email, for every role, and a long session afterwards.
8. **All available tutors are shown** for an hour, and the student chooses.
9. **A presence code five minutes into the session**, by email, entered in the platform.
10. **Twelve hours** is the cancellation window, and a refund is always issued.
11. **The catalogue is global for tools and per university for courses.**

## Still open

- What happens when a presence check is not confirmed. Proposal above.
- Which notices exist exactly, and when each is sent.
- What happens to the data of a university that is suspended.
- Whether a student may ever receive tutoring from another university. Isolation forbids it today;
  it is a product question, not a technical one.

## What these decisions contradict in the report

- The report states one credit equals thirty minutes (hypothesis H2).
- US35 grants purchased credits with an expiry date.
- US28, US29, US30 and US49 describe an issued certificate, a public verification code and its
  revocation. They become a request, a review and an outcome.
- US38 describes authentication against the university's own system.
- US14, US15, US17, US45 and US46 leave the scope and must be withdrawn with the reason written
  down.
