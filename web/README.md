# Ayni web

React 18, TypeScript (strict) and Vite, with React Router, TanStack Query and Tailwind CSS.

Two documents are the contract of every screen. Read them before starting one:

- [`docs/frontend-guide.md`](../docs/frontend-guide.md): what each screen shows, which endpoint it
  calls, which states and refusals it handles, its route, its role and who builds it.
- [`docs/design/field-map.md`](../docs/design/field-map.md): for every Figma frame, where each datum
  on it comes from (endpoint and schema field). A datum that is not there is not on a screen.

The exact shapes of the API are in [`docs/api/openapi.json`](../docs/api/openapi.json), and the
types the web compiles against are generated from it into `src/shared/api/schema.d.ts`.

## Running it

The backend with the demo profile (two universities, their people, a clock the administrator moves;
see [`docs/demo.md`](../docs/demo.md)):

```bash
SPRING_PROFILES_ACTIVE=demo docker compose up postgres mailpit backend
```

Then the web on your machine, which reloads on every save:

```bash
npm ci
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` to the backend (http://localhost:8080, or
`AYNI_API_URL` if it runs elsewhere), so the browser only talks to its own origin and there is no
CORS to configure.

A deployment serves the web from another host than the API (`app.` and `api.`), so it builds the
web with the origin of the API: `VITE_API_BASE_URL=https://api.example.org npm run build`. Every
call goes through `apiUrl` in `src/shared/api/client.ts`, which puts that origin in front of the
path; empty, as in development, the paths stay relative. It is read at build time.
`public/staticwebapp.config.json` gives Azure Static Web Apps the routes of the single page
application and the headers. `deploy/README.md` has the rest.

`docker compose up` without naming services also starts the web in a container on the same port. If
you add a dependency, rebuild it once: `docker compose up -d --build web`.

### Signing in

Write a demo address at `/ingresar` (`u202400001@upc.edu.pe` for Ana at UPC, `lucia.paredes@usil.edu.pe`
for Lucía at USIL): the page takes the university's colours before you submit. The link arrives at
Mailpit (http://localhost:8025); open it and you land where your role belongs. `docs/demo.md` lists
every person and role, and how the administrator opens a session as any of them without email.

| Script                 | What it does                                                                             |
| ---------------------- | ---------------------------------------------------------------------------------------- |
| `npm run dev`          | The development server on port 5173                                                      |
| `npm run format`       | Formats the web with Prettier. Run it before opening the pull request                    |
| `npm run format:check` | Checks the format without writing. CI runs it                                            |
| `npm run lint`         | ESLint: the rules of hooks, exhaustive effect dependencies and accessibility. CI runs it |
| `npm run build`        | Type checks (strict) and builds. CI runs it                                              |
| `npm test`             | Vitest and Testing Library, once. CI runs it                                             |
| `npm run test:watch`   | The same, again on every save                                                            |
| `npm run api:types`    | Regenerates `src/shared/api/schema.d.ts` from `docs/api/openapi.json`                    |
| `npm run api:snapshot` | Downloads `docs/api/openapi.json` from a running backend, then regenerates the types     |

## Structure

```
src/
├── main.tsx                 mounts the providers and the router
├── App.tsx                  composes the routes of every feature, once. Nobody edits it per screen
├── Providers.tsx            the query cache, the brand and the toasts
├── shared/                  the foundation: its only owner is the lead
│   ├── api/                 the typed client, ApiError, the query and mutation hooks, schema.d.ts
│   ├── session/             the session, the role and onboarding guards, sign out
│   ├── time/                instants in the university's zone, days, countdowns
│   ├── brand/               the university's name, logo and colours as CSS variables
│   ├── ui/                  the components every screen reuses
│   └── frame/               header, navigation, phone menu, footer, the route types
├── features/                one folder per backend module
│   ├── identity/  matching/  booking/  sessions/  wallet/  payments/
│   ├── recognition/  skills/  reputation/  notifications/  analytics/
│   └── <module>/routes.tsx  the routes and navigation entries of the module
└── test/                    the fake server, fixtures and the render helper of the tests
```

**One folder per backend module**, named as in `backend/src/main/java/pe/ayni`: a screen about
reservations lives in `features/booking/`, the purchase of credits in `features/payments/`, the
wallet in `features/wallet/`. Inside a feature, one folder per screen with its page, the components
only it uses, its hooks and its tests. When a second feature needs a component, it moves to
`shared/ui` through a pull request to the lead, not before. (`audit` has no screen; the catalogue is
part of `skills`.)

### shared/api

```ts
import { useApiMutation, useApiQuery, errorText } from '@/shared/api';

// Paths are the OpenAPI ones; parameters and answer are typed from it.
const bookings = useApiQuery(['booking', 'mine', { view, page }], '/api/v1/bookings/mine', {
  query: { view, page },
});

// Something that moves credits declares Idempotency-Key, so the types demand `idempotent`.
const purchase = useApiMutation('post', '/api/v1/payments/purchases', {
  idempotent: true,
  invalidates: [['wallet']],
});
purchase.mutate({ body: { credits: 3 } });
```

- Nothing calls `fetch` outside this folder. `api.get/post/put/delete`, `uploadFiles` (multipart)
  and `downloadFile` (attachments) are the doors; the token goes in `Authorization: Bearer` from the
  session module and nowhere else.
- A failed call throws `ApiError { status, kind, message, path, body }`. `message` is the server's
  English: never shown. `errorText(error, overrides)` gives the Spanish of the guide's table, and a
  screen passes its own texts for the 409 and 422 it knows. `errorBody<T>(error)` reads a refusal's
  body (a late cancellation, the missing hours).
- A 401 forgets the session and the guards take the person to `/ingresar`.
- With `idempotent`, each attempt carries a key kept while the same attempt is retried after a 5xx
  or no network, and replaced once the server answered. A two-step operation holds its key with
  `useIdempotencyKey()` and passes it to both calls. While a mutation is in flight, a second
  `mutate` is ignored.
- Query keys are `[module, resource, params]`. The header's balance is `['wallet', 'balance']`:
  invalidate `['wallet']` after moving credits.

### shared/session

`useSession()`, `useMe()` (`GET /api/v1/me`), `useSignOut()`, and the guards `App.tsx` applies to
every route: `RequireAuth`, `RequireRole`, `OnboardingGuard` (a student whose `onboardingStep` is not
`COMPLETED` goes to `/onboarding`) and `HomeRedirect` (`/` sends a student to `/buscar`, a
coordinator to `/coordinador/reconocimiento`, an administrator to `/admin`). The session lives in
memory and is mirrored to localStorage, as the guide says.

### shared/time

`<DateTime value zone />` and `formatInZone` show an instant in the university's zone (the list's
`timezone`, or `MyUniversityView.timezone`), never the browser's. Days without time are
`YYYY-MM-DD` strings: `todayInZone`, `addDays`, `formatDay`. `relativeTime` writes "hace 5 min";
`useCountdown(heldUntil, { offsetMs })` counts a hold down.

A hold is counted against the server's clock, not the browser's (the demo profile moves the
server's, and a browser can be minutes off). `HeldHours.serverNow` is the server's time when it
answered: `clockOffset(serverNow)` returns the server's clock minus the browser's, in milliseconds
(0 when `serverNow` is missing), and that number is the `offsetMs` of `useCountdown`. Compute it once
per answer, when the hold arrives, not on every render:

```tsx
const offsetMs = useMemo(() => clockOffset(hold.serverNow), [hold]);
const countdown = useCountdown(hold.heldUntil, { offsetMs });
```

The offset includes the trip of the answer, a fraction of a second; the server still decides whether
the time ran out.

### shared/brand

`BrandProvider` reads the university (`GET /api/v1/university`, or the sign-in preview of
`GET /api/v1/access/university?domain=` through `useUniversityPreview`) and sets `--brand-primary`,
`--brand-secondary`, `--brand-primary-soft`, `--brand-primary-pressed` and `--brand-text` on the
root. Tailwind reads them as `brand`, `brand-secondary`, `brand-soft`, `brand-pressed` and
`brand-text`. Use them only where the design does: primary buttons, the active tab, links,
progress bars, selected badges, the focus of the fields, the selected day and the bell's counter.
Text in the university's colour is `text-brand-text`, never `text-brand`: UPC's red does not reach
4.5:1 on the grey.

### shared/ui

`Button` (primary, secondary, danger, ghost; `loading`), `ButtonLink`, `TextField`, `TextArea`,
`Select`, `Checkbox`, `Field` (label, hint and error around an input), `Card`, `Table`, `Tabs`,
`Badge`, `Dialog`, `useToast()`, `Spinner`, `PageLoading`, `Skeleton`, `EmptyState`, `ErrorState`
(with retry), `Pagination`, `PageHeader`, `DateTime`, `Credits`, `Avatar`, `UniversityLogo`,
`AyniLogo` and the icons. Every one is in Spanish, keyboard reachable and tested. Their props are
fixed: a missing variant is a request to the lead.

## Adding a screen

1. Open a draft pull request the day you start, from `feature/FE-xx-short-description`.
2. Replace the stub page of your screen: it is already in its feature, at its route, for its roles
   (for example `features/booking/my-tutorings/MyTutoringsPage.tsx`). A new screen gets a new folder
   in its feature and one line in that feature's `routes.tsx`; `App.tsx` is never touched.
3. Read the data with `useApiQuery` / `useApiMutation` and draw it with `shared/ui`: loading,
   empty, error and ready, plus every refusal its section lists.
4. Write the test of its logic next to it, faking the server at the `fetch` level:

   ```tsx
   const server = fakeServer().on('GET', '/api/v1/wallet', { body: { available: 3 } });
   signIn('STUDENT');
   renderWithProviders(<WalletPage />, { route: '/creditos' });
   expect(await screen.findByText('3 créditos')).toBeInTheDocument();
   ```

   `src/features/identity/sign-in/SignInPage.test.tsx` is a whole example.

5. Run it against the real backend, take a screenshot of each state, and keep the pull request to
   one screen (about 400 changed lines).

## Regenerating the API types

When the backend changes an endpoint, with the backend running:

```bash
npm run api:snapshot
```

It saves `docs/api/openapi.json` and regenerates `src/shared/api/schema.d.ts`. Commit both. If
the backend is not running and someone already updated the snapshot, `npm run api:types` is enough.
Never edit `schema.d.ts` by hand.

## What not to do

- Do not call `fetch` outside `shared/api`.
- Do not hand-write a response type: the generated one is the contract. If it is wrong, the backend is.
- Do not show a text that comes from the server (`message`, `guidance`, `warning`): every screen
  writes its own Spanish from the status, the `kind` or the state.
- Do not add dependencies, and do not copy into your feature a component that `shared/ui` has.
- Do not change another person's screen, nor `App.tsx`, `shared/` or the `routes.tsx` of a feature
  that is not yours.
- Do not store tokens anywhere but the session, nor put an email or a token in a URL you build (the access link's own token is the one exception; the
  sign-in preview asks for the university by its domain).
- Do not use the browser's time zone, nor a date library: the server sends instants and the zone.
- Do not decide the contract with the backend yourself. Ask in the pull request.
