# Frontend guide

What every screen shows, which endpoint it calls, which states it handles, and who builds it. **This
is the contract. Everything inside your screen is yours; everything in this document is not.**

The backend guide ([`backend-guide.md`](backend-guide.md)) says what each endpoint does; the OpenAPI
snapshot in `docs/api/openapi.json` says its exact shapes. This guide says what the web does with
them. If something here is wrong or missing, say so in the pull request and it gets decided there:
do not work around it in your screen.

---

## How we work in this sprint

1. **Design first, then build.** Each screen has a frame in Figma named after its id (`FE-12 My
   tutorings`). A screen is not started until its frame is marked ready. Until then you work on what
   does not depend on it (the data layer of your screen, its tests).
2. **One screen, one pull request.** Small enough to review in one sitting (about 400 changed lines
   at most). A screen that is bigger is split by the owner with the lead before starting.
3. **Open a draft pull request the day you start.** An empty skeleton with the route and the
   states is enough. It is how the lead sees drift early, not a judgement.
4. **Say what you did every working day**, in five lines in the team chat: done, doing, blocked.
5. **Nobody merges their own pull request.** A red CI is not reviewed.

The rest of the rules are the same as the backend's: English in code, Conventional Commits, branch
per work item, the pull request template, no secrets or personal data in logs or URLs.

---

## Design

The design lives in Figma (page `Ayni · Sprint 2`) and `docs/design/field-map.md` says, for every
frame, **where each datum on it comes from**: the endpoint and the schema field. That file is part
of this contract: a datum that is not in it is not on a screen. If a screen needs something that is
not there, it is a request to the lead, never a field invented in the web.

- Frames are named after the screen id (`FE-12 Mis tutorías / desktop`, `/ mobile`) and draw the
  states of the contract: loading, empty, error and ready, plus the refusals of the screen.
- **Two universities, one design.** The colour of the university is a Figma variable with two modes
  (UPC and USIL) and a CSS variable in the code: `--brand-primary`, `--brand-secondary` (the colour
  of text on the primary), `--brand-primary-soft` (a tint of about 10 %), `--brand-primary-pressed`
  (darker) and `--brand-text` (the primary, darkened where it would not reach a 4.5 contrast on
  grey). They come from `GET /api/v1/university` and, before signing in, from
  `GET /api/v1/access/university?domain=`. Use them only where the design does: the logo, the
  active tab, the primary buttons, links, progress bars, selected badges, the focus of the fields,
  the selected day and the counter of the bell. Neutrals, type and spacing never change.
- **There is no "Inicio" screen.** The dashboard in Figma is a visual reference with no route: `/`
  sends each role to where the table of routes says.
- **Search** is a weekly grid on desktop and a list by day on a phone.
- Frames still to be drawn (FE-02, FE-04, FE-24, FE-32, FE-33, FE-34, FE-42, FE-43, FE-52): build
  them with `shared/ui` following the section of this guide; the lead adjusts them to the frame
  when it exists.

### Things the contract is silent about, decided

- **Rating tags.** The server takes up to 40 characters per tag and does not publish a list, so the
  web owns it: `CLEAR` (Explica con claridad), `HELPFUL` (Me ayudó), `PATIENT` (Paciente),
  `PUNCTUAL` (Puntual), `PREPARED` (Preparado). One constant in `features/reputation`. A tag the
  list does not know (an older rating) is shown as it comes.
- **Texts that come from the server are English and are never shown.** `guidance`, `warning` and the
  `message` of an error are for logs and developers; every screen writes its own Spanish from the
  status, the `kind` or the state it has (the tables of this guide say which). The exception is a
  value the person typed.
- **Names.** The name of a course and of the other person in a session come from the session itself
  once the backend adds them (`catalogItem`, `counterpart`), and from `GET /api/v1/catalog/{id}`
  anywhere else. `HeldHours` carries the tutor's name.
- **Cycle.** `MyProfileView.currentCycle` exists; the header shows "Ing. de Software · 4.° ciclo".

## Stack

Fixed. Adding a dependency is a decision of the lead, taken in a pull request.

| What | Choice |
|---|---|
| Language and build | TypeScript (strict), Vite, React 18 |
| Routing | `react-router-dom` 6 |
| Server data | `@tanstack/react-query` |
| Forms | `react-hook-form`, with the rules written in the component |
| Styles | Tailwind CSS, with the university's colours as CSS variables |
| API types | `openapi-typescript` over `docs/api/openapi.json`, generated into `src/shared/api/schema.d.ts` |
| Tests | Vitest and Testing Library |

Everything else the platform already gives you: `fetch`, `WebSocket`, `Intl`, `crypto.randomUUID()`.
No date library: the server sends ISO 8601 instants and the zone to show them in.

---

## How the web is organised

```
web/src/
├── main.tsx
├── App.tsx                    the routes of every feature, composed. Nobody edits it per screen
├── shared/
│   ├── api/                   client.ts, errors.ts, schema.d.ts (generated), hooks.ts
│   ├── session/               the session, the role guards, the onboarding guard
│   ├── ui/                    the components every screen reuses (list below)
│   ├── time/                  formatInZone, relative time
│   └── brand/                 the university's name, logo and colours
└── features/
    ├── identity/              one folder per backend module that serves the screen
    ├── skills/
    ├── booking/  matching/  sessions/  wallet/  payments/  recognition/
    ├── reputation/  notifications/  analytics/
    └── <module>/routes.tsx    the routes and navigation entries the feature contributes
```

**One folder per backend module**, as in the backend: a screen about reservations lives in
`features/booking/`. Inside a feature: its pages, the components only it uses, its hooks and its
tests. When a second feature needs a component, it moves to `shared/ui` through a pull request to
the lead, not before.

**Routes are declared by each feature** in `routes.tsx`, and `App.tsx` composes them. The
foundation creates every `routes.tsx` with stubs, so adding a screen never touches `App.tsx` and two
people never conflict there.

---

## The foundation (built before the sprint starts)

Everything below exists on day one. It is code, so it is the contract in its strictest form.

### `shared/api`

- `api.get/post/put/delete`, typed by the generated `schema.d.ts`. **Nothing calls `fetch`
  outside this folder.** Sends `Authorization: Bearer`. A `401` forgets the session and goes to
  `/ingresar`.
- `ApiError { status, message, path }`: every error the server answers has `timestamp, status,
  error, message, path`. `message` is in English and is for logs, not for people (see "Errors").
- `useApiQuery(key, path, options)` and `useApiMutation(method, path, options)` over React Query.
  Query keys are `[module, resource, params]`, for example `['booking', 'mine', { view, page }]`.
- `uploadFiles(path, files, fields)` for `multipart/form-data`, and `downloadFile(path, filename)`
  for the CSV and the evidence files (they arrive as attachments).
- `newIdempotencyKey()`: a `crypto.randomUUID()`. See "Money".

### `shared/session`

- The session is `{ sessionToken, tenantId, userId, role, expiresAt }`, kept in `localStorage`.
- `useSession()`, `<RequireRole roles={[...]}>`, and the onboarding guard: a `STUDENT` whose
  `onboardingStep` is not `COMPLETED` is sent to `/onboarding` from every route but that one.
- Where each role lands: `STUDENT` → `/buscar`, `COORDINATOR` → `/coordinador/reconocimiento`,
  `PLATFORM_ADMIN` → `/admin`.

### `shared/ui`

The only components for the things below. Their props are fixed; a variant that is missing is a
request to the lead.

`Button` (`variant`: primary, secondary, danger, ghost; `loading`), `TextField`, `TextArea`,
`Select`, `Checkbox`, `Field` (label, hint and error around any input), `Card`, `Table`, `Tabs`,
`Badge`, `Dialog`, `useToast()`, `Spinner`, `EmptyState`, `ErrorState` (with a retry), `Pagination`,
`PageHeader`, and the two formatters `<DateTime value zone />` and `<Credits amount />`.

### Branding

`useBrand()` reads `GET /api/v1/university` (signed in) or `GET /api/v1/access/university?domain=`
(before signing in) and sets `--brand-primary` and `--brand-secondary` on the root. Tailwind's
`brand` colour reads them. A university that sets no colours gets Ayni's.

---

## Rules every screen follows

**The generated types mark almost every field as optional** (the OpenAPI the backend publishes does
not say which ones are always there). Every screen treats the fields it reads as possibly absent
and does not assume: it has a text or a fallback for each one, and it never lets `undefined` reach
the screen as "undefined" or as a crash.

### States

Every screen that loads data draws, with `shared/ui`, these four, always:

| State | Draw |
|---|---|
| Loading | `Spinner`, or a skeleton of the final shape. Never a blank page |
| Empty | `EmptyState` that says what it is and what to do next |
| Error | `ErrorState` with a retry. The message is the one of the table below |
| Ready | The data |

A button that sends something is disabled and shows `loading` until the answer arrives. The same
action cannot be sent twice.

### Errors

The server's `message` is English and technical. People see this:

| Status | What it means | What the screen does |
|---|---|---|
| 400 | The request is malformed | Under the field if the form can say which; otherwise "Revisa los datos e inténtalo de nuevo" |
| 401 | The session ended | The foundation redirects to `/ingresar`; the screen does nothing |
| 403 | Not allowed (a role, a restriction) | "No tienes permiso para esto" |
| 404 | Does not exist in this university | "No encontramos lo que buscas" |
| 409 | The state refuses it | **Read `message` and the body**: most screens have a specific 409 (a late cancellation, a taken hour). The screen's section says which |
| 422 | A wrong value that counts | The specific text of the screen (a wrong presence code) |
| 5xx / no network | The platform failed | `ErrorState` with a retry; never lose what the person typed |

### Time

- Every instant arrives in UTC. **Show it in the university's zone**: lists carry `timezone`; the
  rest read it from `GET /api/v1/university`. `<DateTime value zone />` does it, never the
  browser's zone.
- Dates without time (`from`, `to` of the agenda and the indicators) are days of the university.
- "Today" is the university's today.

### Money

- A credit is an hour. Show it with `<Credits amount />`: "1 crédito", "3 créditos".
- Operations that move credits send a header **`Idempotency-Key`** (a UUID **per attempt**): the same
  key returns the same result, a new attempt after a rejection takes a new key. Generate it when the
  person starts the action and keep it while the same action is retried.
- Buying and spending are two-step where the backend says so. Never turn a two-step into one.

### Language

Everything the person reads is Spanish. Code, branches and commits are English. Error and empty
texts are short and say what to do ("Todavía no tienes tutorías. Busca una en Buscar").

### Accessibility, the minimum

Every input has a `Field` label. Buttons are buttons and links are links. Everything reachable by
keyboard. A dialog traps the focus and closes with Escape. Colour is never the only signal.

### Tests

At least one test per screen, of the **logic**: a form's validation, the mapping of an error to a
text, a state machine (the hold and its countdown). No snapshots. The server is faked at the
`fetch` level in tests, never by mocking the screen's own hooks.

---

## Routes and who may enter

| Route | Role | Screen |
|---|---|---|
| `/ingresar` | public | FE-01 |
| `/access/confirm` | public | FE-02 (fixed: it is where the emailed link points) |
| `/onboarding` | student | FE-03 |
| `/perfil` | student | FE-04 |
| `/buscar` | student | FE-10 |
| `/reservar` | student | FE-11 |
| `/mis-tutorias` | student | FE-12 |
| `/sesion/:id` | student, tutor | FE-20 to FE-23 |
| `/sesion/:id/repaso` | student, tutor | FE-24 |
| `/creditos` | student | FE-30 |
| `/creditos/comprar` | student | FE-31 |
| `/creditos/compras`, `/creditos/compras/:id` | student | FE-32 |
| `/creditos/beneficios` | student | FE-33 |
| `/reconocimiento` | student | FE-34 |
| `/tutor/disponibilidad` | student (as tutor) | FE-40 |
| `/tutor/agenda` | student (as tutor) | FE-41 |
| `/tutor/habilidades` | student (as tutor) | FE-42 |
| `/tutor/reputacion` | student (as tutor) | FE-43 |
| `/avisos` | student, coordinator | FE-60 |
| `/coordinador/reconocimiento`, `/coordinador/reconocimiento/:id` | coordinator | FE-50, FE-51 |
| `/coordinador/regla` | coordinator | FE-52 |
| `/coordinador/indicadores` | coordinator | FE-53 |
| `/admin` | platform admin | stub: sign-out only in this sprint |

A person is a student and a tutor at once: the navigation of a student shows both sides, and the
tutor routes need no other account.

---

## Screens

Each screen says: its route, who enters, the endpoints (shapes are the OpenAPI schemas named in
`code`), what it does, what it handles, and when it is done. **Design:** every screen has its Figma
frame; the link is added by the lead.

### Access and profile · identity

#### FE-01 · Sign in · `/ingresar` · public

- **Endpoints:** `GET /api/v1/access/university?domain=` (`UniversityIdentityView`), `POST
  /api/v1/access/request` (`RequestAccessRequest`, answers `202` with no body).
- **Does:** one field, the institutional email. Once it looks like an address, ask for the
  university of its domain (debounce 400 ms) and paint its name, logo and colours before signing
  in. Only the domain travels (`?domain=upc.edu.pe`), never the address. Submit asks for the link and shows "Te enviamos un enlace a {correo}. Vence en 10 minutos."
  with a "Reenviar" button that waits 30 seconds.
- **Handles:** `404` on the preview → a hint under the field, not an error ("No encontramos una
  universidad afiliada a Ayni con ese dominio. Si administras Ayni, igual puedes pedir tu enlace."),
  and the page keeps Ayni's colours. The submit stays enabled: the platform administrator's address
  belongs to no university and still gets a link, so the backend decides. `400` on the request → a
  text of the screen's own, in Spanish, under the field, never the server's `message`: "Tu
  universidad todavía no está afiliada a Ayni" when the preview found no university, "No pudimos
  enviar el enlace a ese correo. Revisa que sea el institucional" otherwise.
- **Done when:** a student of UPC sees UPC's colours and name before submitting, a made-up domain is
  refused by the backend, the administrator gets a link from the web with an address of any domain,
  and the email arrives in Mailpit.

#### FE-02 · Confirm the link · `/access/confirm?token=` · public

- **Endpoints:** `POST /api/v1/access/confirm` (`ConfirmAccessRequest` → `OpenedSession`), `GET
  /api/v1/me` (`MyProfileView`).
- **Does:** sends the token **once** (a link works once: React's development double effect must not
  spend it twice, as the existing page already guards), keeps the session and redirects: admin →
  `/admin`, coordinator → `/coordinador/reconocimiento`, student → `/onboarding` if `onboardingStep`
  is not `COMPLETED`, else `/buscar`.
- **Handles:** `400` (unknown, used or expired link) → "El enlace venció o ya se usó" and a button to
  `/ingresar`. `409` (used at the same moment) → the same.
- **Done when:** the three roles land where the table says, and opening the link twice does not
  break the first session.

#### FE-03 · Onboarding · `/onboarding` · student

- **Endpoints:** `GET /api/v1/me`, `PUT /api/v1/me/profile` (`UpdateMyProfileRequest`:
  `photoUrl`, `bio`), `POST /api/v1/me/academic-record/import` (`204`), `GET /api/v1/catalog`
  (`CatalogPage`, with `q` and `category`), `GET /api/v1/tutor/skills/suggestions`
  (`[SuggestedCourse]`), `POST /api/v1/me/onboarding/interests` (`OnboardingInterestsRequest`
  → `OnboardingInterestsResponse`).
- **Does:** a three-step wizard that **starts at the step in `onboardingStep`**: `PROFILE` (bio and
  photo URL, both optional), `ACADEMIC_RECORD` (a button imports the record from the university;
  show what was imported by listing the suggestions), `LEARNING_INTERESTS` (two lists: what I need
  help with, picked from the catalogue with search; what I can teach, picked from the suggestions).
  The last step sends **both lists, always** (either may be empty). At `COMPLETED` go to `/buscar`.
- **Handles:** the academic fields (name, career, term, student code) are shown and not editable.
  `409` on the last step (a step is missing) → go back to the step in `onboardingStep`. A course
  the person asked to teach that is not in `response.teaching` was not enabled: say so and why ("su
  nota no alcanza el mínimo de la universidad").
- **Done when:** a fresh student reaches `COMPLETED` using only the screen, and closing the browser
  halfway resumes at the same step.

#### FE-04 · Profile · `/perfil` · student

- **Endpoints:** `GET /api/v1/me`, `PUT /api/v1/me/profile`, `GET` and `PUT
  /api/v1/me/learning-interests` (`[CatalogItem]`, the whole set), `POST
  /api/v1/me/academic-record/import`.
- **Does:** shows the data that comes from the university as read-only; edits bio and photo; edits
  the needs (the `PUT` replaces the whole set, so send the final list); a "Actualizar mi récord"
  button re-imports.
- **Done when:** changing needs does not affect confirmed tutorings, and the re-import shows
  courses approved since.

### Search and booking · matching, booking

#### FE-10 · Search · `/buscar` · student

- **Endpoints:** `GET /api/v1/catalog` (course picker), `GET /api/v1/search/offers`
  (`catalogItemId`, `from`, `to`, `page`, `size` → `OfferSearch`), `GET
  /api/v1/tutors/{id}/standing` is not needed: each `Offer` already carries `averageStars`,
  `ratingsCount`, `sessionsTaught` and `newTutor`.
- **Does:** pick a course (search as you type) and a date range (default: the next 7 days of the
  university); list the offers grouped by day, each with the hour, the tutor's name and their
  standing (stars and count, or the "Tutor nuevo" badge when `newTutor`). Choosing one goes to
  `/reservar` carrying `tutorId`, `catalogItemId`, `start` and `hours: 1`. The person's own hours are
  never offered (the server leaves them out).
- **Handles:** `exactMatch: false` → a notice above the list: "No hay horas en esa franja. Estas son
  las más cercanas". No offers at all → `EmptyState` suggesting another range. Times in the
  response's `timezone`.
- **Done when:** a search for a course Bruno teaches shows his hours in Lima time, and a range with
  nothing shows the closest ones with the notice.

#### FE-11 · Book · `/reservar` · student

- **Endpoints:** `POST /api/v1/bookings/holds` (`HoldHoursRequest` → `HeldHours`), `DELETE
  /api/v1/bookings/holds?tutorId&start&hours`, `POST /api/v1/bookings` (`BookHoursRequest` →
  `Booking`).
- **Does:** on arrival, **hold the hour** (it stays out of circulation for five minutes) and draw a
  countdown to `heldUntil`, counted against the server's clock: read `HeldHours.serverNow` with
  `clockOffset(serverNow)` (`shared/time`) and pass the result to
  `useCountdown(heldUntil, { offsetMs })`, so a browser whose clock is out of step (or the demo
  profile's moved clock) still shows the real time left. Ask for the need ("¿En qué necesitas ayuda?", required) and confirm. On
  `201` show a success screen with the day, the hour and a link to `/mis-tutorias`. Leaving the
  screen without confirming releases the hold.
- **Handles (all `409`, read `message`):** the hour was taken → "Alguien tomó esa hora. Elige otra"
  with a link to `/buscar`; the hold ran out → hold again or go back; the tutor is no longer enabled;
  not enough credits → "Te faltan {n} créditos" with a link to `/creditos/comprar`. A countdown at
  zero disables the confirm button and offers to hold again. Anything unexpected says that **nothing
  was charged**.
- **Done when:** the hold, the countdown, a successful booking and each refusal above are
  demonstrable, and the balance goes down by the credits charged.

#### FE-12 · My tutorings · `/mis-tutorias` · student

- **Endpoints:** `GET /api/v1/bookings/mine?view=UPCOMING|HISTORY&page&size` (`StudentBookings`),
  `GET /api/v1/sessions?bookingId=` (`SessionOfBooking`), `DELETE /api/v1/bookings/{id}`.
- **Does:** two tabs, upcoming and history, a page at a time. Each item: date and hour, tutor,
  course, status badge (`CONFIRMED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`). When `joinable` is true,
  an "Entrar" button asks for the session of that booking and goes to `/sesion/:sessionId`. A
  "Cancelar" button on upcoming ones.
- **Cancelling:** the first `DELETE` for a booking **less than 12 hours from its start answers `409`
  with a warning body** (`late`, `refundWillBeIssued`, `confirmationRequired`) and cancels nothing.
  Show it in a dialog ("Faltan menos de 12 horas: no se te devolverán los créditos") and, if the
  person confirms, repeat the `DELETE` with `?confirmLate=true`. Otherwise it cancels at once (`204`)
  and the credits come back.
- **Done when:** both cancel paths work, and a booking in progress is still listed as upcoming.

### The session · sessions, reputation

These are the lead's. They are described in the backend guide (sessions and reputation sections)
and in ADR 0009; the screen ids are reserved here so the rest of the web can link to them.

| Id | Screen |
|---|---|
| FE-20 | Session detail and joining (video) |
| FE-21 | Presence code |
| FE-22 | Shared whiteboard (live) |
| FE-23 | Closing and rating |
| FE-24 | Reviewing a past session |

### Credits · wallet, payments, recognition

#### FE-30 · Wallet · `/creditos` · student

- **Endpoints:** `GET /api/v1/wallet` (`WalletBalance`), `GET /api/v1/wallet/movements?from&to&reason&page&size`
  (`MovementsPage`).
- **Does:** the available total and the breakdown by origin (`SEED`, `ALLOCATED`, `EARNED`,
  `PURCHASED`), each with its groups and **expiry date** where `expires` is true; a badge on the
  ones that count toward recognition (`countsTowardsRecognition`). Below, the history, newest first,
  with filters by period and reason, a page at a time. Each movement shows its direction, amount,
  reason (in Spanish) and a link by `referenceType` (booking → `/mis-tutorias`, purchase →
  `/creditos/compras/:id`).
- **Handles:** a zero balance (`available` is 0) shows a text of the web's own: "Aún no tienes
  créditos". `guidance` is not shown.
- **Done when:** a student with credits of two origins sees both with their expiry, and the filters
  narrow the history.

#### FE-31 · Buy credits · `/creditos/comprar` · student

- **Endpoints:** `POST /api/v1/payments/purchases` (`PurchaseRequest { credits }` →
  `PurchaseResponse`, header `Idempotency-Key`).
- **Does:** an amount of credits and its price (the response has it; show `credits × price` only
  after the first answer, the price lives on the server), a confirm button, and the result by
  `status`: `CONFIRMED` ("Listo: se acreditaron {n} créditos", link to `/creditos`), `PENDING`
  (**"Tu pago está en proceso. No vuelvas a intentarlo"**, the web's own text from the status, not
  `guidance`), `FAILED` (rejected: a
  new attempt takes a **new key**), `EXPIRED`.
- **Handles:** `providerUnavailable: true` → the explicit "compras temporalmente no disponibles"
  message; the rest of the platform keeps working. A monthly limit refusal (`409`, as the OpenAPI documents it)
  shows a text of the web's own in Spanish chosen by the status, never the server's `message`.
- **Done when:** a confirmed, a rejected and a pending purchase are each shown correctly (the
  lead switches the simulated provider with `/api/v1/admin/demo/payments`), and a double click sends
  one request.

#### FE-32 · Purchases · `/creditos/compras`, `/:id` · student

- **Endpoints:** `GET /api/v1/payments/purchases` (`[PurchaseResponse]`), `GET
  /api/v1/payments/purchases/{purchaseId}` (`PurchaseDetailResponse`).
- **Does:** the list (date, credits, amount, status) and a receipt page with the provider reference
  and, when confirmed, a link to the wallet movement it caused.

#### FE-33 · Campus benefits and donations · `/creditos/beneficios` · student

- **Endpoints:** `GET /api/v1/wallet/benefits` (`[CampusBenefitResponse]`), `POST
  /api/v1/wallet/benefits/{benefitId}/redemptions` and `POST /api/v1/wallet/donations`
  (`CreditUseRequest`, `DonationRequest` → `CreditUseResponse`; header `Idempotency-Key`), `GET
  /api/v1/wallet/donation-pool` (`DonationPoolResponse`), `GET /api/v1/wallet/donations`.
- **Does:** the active benefits with their cost, and a donation form. **Both are irreversible and
  two-step**: the first call (`confirmed: false`) answers `confirmationRequired: true`, a `warning`
  and a `confirmationId`; show the warning in a dialog and, if the person accepts, repeat the call
  with `confirmed: true` and that `confirmationId` and the **same** `Idempotency-Key`, within 10
  minutes. Only earned credits are spent.
- **Handles:** `409` for not enough **earned** credits → it says how many are missing and that bought
  credits do not count.
- **Done when:** a redemption and a donation complete in two steps, and a double click spends once.

#### FE-34 · Recognition (student) · `/reconocimiento` · student

- **Endpoints:** `GET /api/v1/recognition/progress` (`RecognitionProgress`), `POST
  /api/v1/recognition/requests` (`201` → `RecognitionRequest`), `GET
  /api/v1/recognition/requests/mine`.
- **Does:** a progress bar of `earnedHours` against `requiredHours`, how many are missing, and a
  "Solicitar reconocimiento" button enabled only when `canRequest`. Below, the requests with their
  status (`SUBMITTED`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`), the decision reason and the sessions
  that back each.
- **Handles:** `requiredHours` absent → "Tu universidad todavía no habilitó el reconocimiento". `409`
  with `missingHours` → "Te faltan {n} horas".
- **Done when:** a student under the requirement sees the bar and a disabled button, and one over it
  submits and sees the request pending.

### The tutor · booking, skills, reputation

#### FE-40 · Availability · `/tutor/disponibilidad` · student as tutor

- **Endpoints:** `POST /api/v1/tutor/availability` (`DeclareAvailabilityRequest`), `DELETE
  /api/v1/tutor/availability/{id}`, `POST /api/v1/tutor/availability/exceptions`
  (`AvailabilityExceptionRequest`), `POST /api/v1/tutor/availability/pauses`, `GET
  /api/v1/tutor/availability/pauses/current`, `DELETE /api/v1/tutor/availability/pauses/{id}`.
- **Reading back:** `GET /api/v1/tutor/availability` (`TutorAvailabilityResponse`) lists the weekly
  ranges, the date exceptions and the pauses that still govern, in the university's zone.
- **Does:** a weekly grid (day, from, to, valid from) to add ranges and remove them; a form for a
  one-day exception (`ADD` or `REMOVE` hours); and a pause (from, to) with its current state ("En
  pausa hasta el …") and a button to end it early. Hours are in the university's zone.
- **Handles:** the answers say how many hours were generated or withdrawn and how many **booked
  hours were kept**: tell the tutor.
- **Done when:** declaring a range makes the hours appear in a student's search, and a pause
  withdraws them.

#### FE-41 · Agenda · `/tutor/agenda` · student as tutor

- **Endpoints:** `GET /api/v1/tutor/agenda?from&to` (`TutorAgenda`), `GET
  /api/v1/tutor/agenda/history` (`TutorBookings`), `DELETE /api/v1/bookings/{id}` (tutor's
  cancellation), `GET /api/v1/sessions?bookingId=`.
- **Does:** a week by default (up to 31 days), in days of the university. Lines of kind `BOOKED` show
  the student, the course, the need description and the student's `absenceCount` when it is above
  zero; lines of kind `FREE` are the open hours. "Entrar" when `joinable`. A history tab. The tutor can
  cancel: **less than 12 hours before the start the first call warns that it will be recorded**;
  confirm with `?confirmLate=true`.
- **Done when:** booked and free hours are told apart, a late cancellation shows its warning, and
  the history pages.

#### FE-42 · My skills · `/tutor/habilidades` · student as tutor

- **Endpoints:** `GET /api/v1/tutor/skills` (`[TutorSkill]`), `GET /api/v1/tutor/skills/suggestions`,
  `POST /api/v1/tutor/skills` (`OfferSkillRequest`), `DELETE /api/v1/tutor/skills/{id}`, `POST
  /api/v1/tutor/skills/{id}/validation` (multipart: `files`, `note`), `GET
  /api/v1/tutor/skills/proposals`, `POST /api/v1/tutor/skills/proposals` (`ProposeSkillRequest`),
  `GET /api/v1/catalog/similar?name=`.
- **Categories:** `GET /api/v1/catalog/categories` (`[CategoryResponse]`) fills the `categoryId` of a
  proposal. The category of university courses is not in it.
- **Does:** my skills with their status and the date it changed; offer a course from the
  suggestions (it enables itself if the grade clears the minimum); withdraw one (confirmed bookings
  stand); for a global tool, upload evidence (1 to 3 files, PDF, PNG or JPEG, up to 5 MB each, and
  a note up to 1000 characters, sent as a field of the multipart form beside `files`; the OpenAPI
  documents `note` as a query parameter because of how Spring declares it, but the server accepts
  both) and follow its review (`review`: status and reason if rejected);
  propose a tool the catalogue lacks.
- **Handles:** proposing: the server answers `409` with similar items until `confirmDistinct` is
  sent: show them ("¿Es alguna de estas?") and let the person insist. Evidence refusals (`400`) name
  what is wrong with the file.
- **Done when:** a course with a good grade enables at once, evidence upload shows its pending state,
  and a similar proposal is challenged.

#### FE-43 · Reputation · `/tutor/reputacion` · student as tutor

- **Endpoints:** `GET /api/v1/tutor/reputation` (`TutorReputation`).
- **Does:** one card per skill: sessions taught, the average (or, below three ratings, the
  provisional average with "te faltan {n} calificaciones para el promedio"), the most given tags, and
  a warning when `atRisk` ("Tu promedio está por debajo de {minimumAverage}"). It is read-only.
- **Done when:** a tutor with fewer than three ratings sees the provisional average and how many are
  missing.

### Coordinator · recognition, analytics

#### FE-50 · Recognition requests · `/coordinador/reconocimiento` · coordinator

- **Endpoints:** `GET /api/v1/coordinator/recognition/requests?status&page&size`
  (`RecognitionQueue`).
- **Does:** the queue (default) with the oldest first, and the history by status. Each row: the
  student, hours, sessions, average rating and date.

#### FE-51 · Recognition case · `/coordinador/reconocimiento/:id` · coordinator

- **Endpoints:** `GET /api/v1/coordinator/recognition/requests/{id}` (`RecognitionCase`), `GET
  .../{id}/sessions`, `GET .../{id}/sessions/{sessionId}`, `POST .../{id}/decision`
  (`RecognitionDecisionRequest { decision: APPROVE | REJECT, reason }`).
- **Does:** the case as it was submitted (frozen), the sessions that back it with each one's
  evidence, and **the audit alerts next to the session they are about**. A decision requires a reason
  (up to 500 characters) and is final.
- **Handles:** `409` when someone already decided → show the decision that exists.
- **Done when:** approving and rejecting both work, and a decided case cannot be decided again.

#### FE-52 · Recognition rule · `/coordinador/regla` · coordinator

- **Endpoints:** `GET` and `PUT /api/v1/coordinator/recognition/rule` (`RecognitionRule`).
- **Does:** shows the hours the university asks for and from when; a form to set a new rule (hours
  at least 1, an optional start day that is not in the past). Setting a rule never changes the
  requests already submitted.
- **Handles:** `404` on the `GET` → "Todavía no definiste cuántas horas pide tu universidad".

#### FE-53 · Indicators · `/coordinador/indicadores` · coordinator

- **Endpoints:** `GET /api/v1/coordinator/indicators?from&to` (`UsageIndicators`), `GET
  .../indicators/export` (CSV).
- **Does:** the totals of a period (default: the last 30 days) as numbers, the daily series as a
  simple table or bars, the courses with the most searches nobody covered (`uncoveredDemand`, ids:
  resolve the names from `GET /api/v1/catalog`), and a download button for the CSV. No names or
  identifiers of people ever appear.
- **Handles:** a period over 366 days is refused (`400`): limit the picker.

### Notices · notifications

#### FE-60 · Notices · bell in the header and `/avisos` · student, coordinator

- **Endpoints:** `GET /api/v1/notifications?unread&page&size` (`NotificationsPage`), `POST
  /api/v1/notifications/{id}/read`, `POST /api/v1/notifications/read-all`.
- **Does:** a bell with `unreadCount`, refreshed on load and every 60 seconds while the tab is
  visible (there is no real time). The page lists the notices, newest first. **The text is built by
  the web from `kind`**; the payload only carries ids:

| `kind` | Text |
|---|---|
| `BOOKING_CONFIRMED` | "Tu tutoría quedó confirmada" |
| `BOOKING_CANCELLED` | "Se canceló una tutoría" |
| `CREDITS_EXPIRING` | "Tienes créditos por vencer" |
| `SESSION_CLOSED` | "Tu sesión se cerró" (link to the session; for the student, "Califícala") |
| `TUTOR_NO_SHOW` | "Tu tutor no llegó: te devolvimos el crédito" |
| `PURCHASE_CONFIRMED` | "Tu compra se acreditó" |
| `REQUEST_RESOLVED` | "Se resolvió tu solicitud de reconocimiento" |
| `VALIDATION_RESOLVED` | "Revisamos la evidencia de tu habilidad" |

Clicking a notice marks it read and goes to the screen it is about.
- **Done when:** the bell counts, "marcar todas" clears it, and every kind above has its text.

---

## The sprint

### Who builds what

| Person | Screens |
|---|---|
| Rodrigo | FE-20 to FE-24 (the session and the whiteboard); review of every pull request |
| Daniel | FE-10, FE-11, FE-12 (search, book, my tutorings: the main flow) |
| Juan | FE-01, FE-02, FE-03, FE-04 (access and profile), FE-40, FE-41 (the tutor's availability and agenda) |
| Diego | FE-34, FE-50, FE-51, FE-52 (recognition, student and coordinator), FE-60 (notices) |
| Francisco | FE-31, FE-33, FE-42 (buying, benefits, my skills) |
| Luis | FE-30, FE-32, FE-43, FE-53 (wallet, purchases, reputation, indicators: all read-only) |

The foundation (the skeleton, `shared/` and the frame) is built before the sprint by the lead, and
`shared/` has no other owner.

### Definition of done

A screen is finished when all of this is true:

- [ ] It follows its Figma frame, on desktop and on a phone width
- [ ] It handles loading, empty, error and ready, and every refusal its section lists
- [ ] It uses only `shared/ui` and `shared/api`, and no new dependency
- [ ] It has its test of the logic
- [ ] `npm run build` passes (type check included) and the CI is green
- [ ] It was run against the real backend with `docker compose up` (not only mocked)
- [ ] The pull request has a screenshot of each state it draws
- [ ] Nothing outside its feature folder changed, except `shared/ui` through the lead

### What not to do

- Do not call `fetch` outside `shared/api`.
- Do not hand-write a response type: the generated one is the contract. If it is wrong, the backend is.
- Do not add dependencies, and do not copy a component into your feature that `shared/ui` has.
- Do not change another person's screen, nor `App.tsx`, `shared/` or `routes.tsx` of a feature that is not yours.
- Do not store tokens anywhere but the session, nor put the email or the token in a URL (the access link's `token` is the one exception, and it is spent at once). The email never travels in a URL, so the sign-in preview asks for the university by its domain (`?domain=`).
- Do not decide the contract with the backend yourself. Ask in the pull request.
