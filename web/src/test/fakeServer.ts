import { vi } from 'vitest';

/**
 * The server, faked at the `fetch` level (the guide's rule: never mock a screen's own hooks). A
 * test declares what each method and path answers and reads back what was sent.
 *
 *     const server = fakeServer();
 *     server.on('GET', '/api/v1/wallet', { body: wallet });
 *     server.on('DELETE', '/api/v1/bookings/b1', { status: 409, body: { late: true } });
 *     expect(server.requests('DELETE')).toHaveLength(1);
 *
 * A request nobody declared answers 501, so a missing answer fails loudly.
 */
export interface Recorded {
  method: string;
  path: string;
  query: URLSearchParams;
  headers: Headers;
  body: unknown;
}

export interface Reply {
  status?: number;
  body?: unknown;
  headers?: Record<string, string>;
}

type Answer = Reply | ((request: Recorded) => Reply | Promise<Reply>);

interface Route {
  method: string;
  path: string | RegExp;
  answer: Answer;
}

function matches(path: string | RegExp, actual: string): boolean {
  return typeof path === 'string' ? path === actual : path.test(actual);
}

function parseBody(body: BodyInit | null | undefined): unknown {
  if (typeof body !== 'string') return body ?? undefined;
  try {
    return JSON.parse(body);
  } catch {
    return body;
  }
}

export function fakeServer() {
  const routes: Route[] = [];
  const recorded: Recorded[] = [];

  const fetchMock = vi.fn(async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const url = new URL(typeof input === 'string' ? input : input instanceof URL ? input.href : input.url, 'http://localhost');
    const request: Recorded = {
      method: (init.method ?? 'GET').toUpperCase(),
      path: url.pathname,
      query: url.searchParams,
      headers: new Headers(init.headers),
      body: parseBody(init.body),
    };
    recorded.push(request);
    const route = [...routes].reverse().find((r) => r.method === request.method && matches(r.path, request.path));
    if (!route) {
      return new Response(JSON.stringify({ status: 501, message: `No fake answer for ${request.method} ${request.path}` }), {
        status: 501,
        headers: { 'Content-Type': 'application/json' },
      });
    }
    const reply = typeof route.answer === 'function' ? await route.answer(request) : route.answer;
    const status = reply.status ?? 200;
    const empty = reply.body === undefined || status === 204;
    return new Response(empty ? null : JSON.stringify(reply.body), {
      status,
      headers: { ...(empty ? {} : { 'Content-Type': 'application/json' }), ...reply.headers },
    });
  });

  vi.stubGlobal('fetch', fetchMock);

  return {
    on(method: string, path: string | RegExp, answer: Answer = {}) {
      routes.push({ method: method.toUpperCase(), path, answer });
      return this;
    },
    /** What was sent, optionally only of a method and a path. */
    requests(method?: string, path?: string | RegExp): Recorded[] {
      return recorded.filter(
        (r) => (!method || r.method === method.toUpperCase()) && (!path || matches(path, r.path)),
      );
    },
    fetch: fetchMock,
  };
}

export type FakeServer = ReturnType<typeof fakeServer>;
