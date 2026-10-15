import { currentSession, forgetSession } from '../session';

/**
 * Single entry point for every call to the API.
 *
 * Nothing calls `fetch` directly. Everything goes through here, so that the
 * things every request needs are decided once: who is asking, how errors are
 * surfaced, and where the API lives.
 *
 * Who is asking travels as the session token (TS03): the API reads the
 * university and the person from the session, so nothing here names either.
 * A 401 means the session expired or was closed, and it is forgotten.
 *
 * The URL is relative on purpose. Vite proxies `/api` to the backend, so the
 * browser only ever talks to its own origin and there is no CORS to configure.
 */

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const session = currentSession();
  const response = await fetch(`/api${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(session ? { Authorization: `Bearer ${session.sessionToken}` } : {}),
      ...options.headers,
    },
  });

  if (response.status === 401 && session) {
    forgetSession();
  }

  if (!response.ok) {
    let message = `${options.method ?? 'GET'} ${path} failed`;
    try {
      const body = (await response.json()) as { message?: string };
      message = body.message ?? message;
    } catch {
      // Not every failure has a body.
    }
    throw new ApiError(response.status, message);
  }

  // 202 and 204 have no body, and calling json() on them throws.
  return response.status === 204 || response.status === 202
    ? (undefined as T)
    : ((await response.json()) as T);
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, {
      method: 'POST',
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};
