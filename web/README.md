# Ayni web

React, TypeScript and Vite. It runs with the rest of the project through `docker compose up`, and
is served at http://localhost:5173.

## Structure

```
src/
├── main.tsx              mounts the application
├── App.tsx               the routes
├── shared/
│   ├── config.ts         the university every request belongs to
│   ├── api/client.ts     the only place that calls fetch
│   └── ui/               frame and components used by several features
└── features/
    ├── home/
    └── booking/
```

**One folder per feature, named after the backend module that serves it.** A screen about
reservations lives in `features/booking/` because `booking` is the module that answers it. That way
the same word means the same thing on both sides, and finding where something lives requires no
explanation.

Inside a feature: its pages, the components only it uses, and the calls to the API it needs. When a
component is needed by a second feature, it moves to `shared/ui`, not before. Moving it early
produces a shared folder full of things used once.

Only the features that exist are created. There is no empty folder waiting for a screen: create it
when you write the screen.

## Calling the API

Always through `shared/api/client.ts`:

```ts
import { api } from '../../shared/api/client';

const slots = await api.get<Slot[]>('/v1/slots?course=CS101');
```

It adds the `X-Tenant-Id` header, which is how the backend knows which university is asking, and it
turns a failed response into an `ApiError` you can catch. Do not call `fetch` directly: a call that
forgets the header reaches the wrong data, or none.

Paths are relative and start with `/v1/...`. Vite proxies `/api` to the backend, so the browser
never leaves its own origin and there is nothing to configure about CORS.

## While you work

The browser reloads on save. If you add a dependency to `package.json`, rebuild the image once:

```bash
docker compose up -d --build web
```

Run `npm install` on your machine as well, so that the editor finds the types. The container keeps
its own copy; they do not interfere.

## What is missing on purpose

No styling library and no server state library yet. Styles are inline while the screens are this
simple. Tailwind and TanStack Query are worth adding when there are several real screens, not
before: every dependency is something the whole team has to learn.
