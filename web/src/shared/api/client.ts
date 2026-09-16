import { CURRENT_TENANT } from '../config';

/**
 * Single entry point for every call to the API.
 *
 * Nothing calls `fetch` directly. Everything goes through here, so that the
 * things every request needs are decided once: which university it belongs to,
 * how errors are surfaced, and where the API lives.
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
  const response = await fetch(`/api${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      // Which university this request belongs to. Today it is a header; when
      // sign in exists it will travel inside the access token and this line
      // disappears without anything else changing.
      'X-Tenant-Id': CURRENT_TENANT,
      ...options.headers,
    },
  });

  if (!response.ok) {
    throw new ApiError(response.status, `${options.method ?? 'GET'} ${path} failed`);
  }

  // 204 has no body, and calling json() on it throws.
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};
