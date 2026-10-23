import { authorization, forgetSession } from '../session/session';
import { ApiError } from './errors';
import type { HttpMethod, OptionsArgument, PathsWith, RequestOptions, ResponseOf } from './types';

/**
 * The single entry point for every call to the API. Nothing calls `fetch` outside this folder.
 *
 * Paths are the ones of the OpenAPI document, written as it writes them (`/api/v1/bookings/{id}`),
 * and the types come from it: the parameters a path takes and the body it answers are checked by
 * the compiler. Vite proxies `/api` to the backend, so the browser only talks to its own origin.
 *
 * Who is asking travels as the session token (TS03). A 401 to a request that carried one means the
 * session ended: it is forgotten, and the route guards take the person to `/ingresar`.
 */

type Query = Record<string, unknown> | undefined;

interface RawOptions {
  path?: Record<string, unknown>;
  query?: Query;
  body?: unknown;
  idempotencyKey?: string;
  signal?: AbortSignal;
}

/** Fills `{id}` with its value and appends the query, skipping what is empty. */
export function buildUrl(template: string, pathParams?: Record<string, unknown>, query?: Query): string {
  const path = template.replace(/\{([^}]+)\}/g, (_, name: string) => {
    const value = pathParams?.[name];
    if (value === undefined || value === null || value === '') {
      throw new Error(`Missing path parameter ${name} for ${template}`);
    }
    return encodeURIComponent(String(value));
  });
  const search = new URLSearchParams();
  for (const [name, value] of Object.entries(query ?? {})) {
    if (value === undefined || value === null || value === '') continue;
    for (const item of Array.isArray(value) ? value : [value]) {
      search.append(name, String(item));
    }
  }
  const text = search.toString();
  return text ? `${path}?${text}` : path;
}

async function readBody(response: Response): Promise<unknown> {
  if (response.status === 204 || response.status === 205) return undefined;
  const text = await response.text();
  if (!text) return undefined;
  const type = response.headers.get('Content-Type') ?? '';
  if (type.includes('json')) {
    try {
      return JSON.parse(text);
    } catch {
      return text;
    }
  }
  return text;
}

function messageOf(body: unknown, fallback: string): string {
  if (body && typeof body === 'object' && typeof (body as { message?: unknown }).message === 'string') {
    return (body as { message: string }).message;
  }
  return fallback;
}

async function send(method: string, template: string, options: RawOptions, accept = 'application/json'): Promise<Response> {
  const url = buildUrl(template, options.path, options.query);
  const headers = new Headers({ Accept: accept });
  const token = authorization();
  if (token) headers.set('Authorization', token);
  if (options.idempotencyKey) headers.set('Idempotency-Key', options.idempotencyKey);

  let body: BodyInit | undefined;
  if (options.body instanceof FormData) {
    // The browser writes the multipart boundary itself.
    body = options.body;
  } else if (options.body !== undefined) {
    headers.set('Content-Type', 'application/json');
    body = JSON.stringify(options.body);
  }

  let response: Response;
  try {
    response = await fetch(url, { method, headers, body, signal: options.signal });
  } catch (failure) {
    if (failure instanceof DOMException && failure.name === 'AbortError') throw failure;
    throw ApiError.network(template);
  }

  if (!response.ok) {
    const answer = await readBody(response).catch(() => undefined);
    if (response.status === 401 && token) {
      forgetSession();
    }
    throw new ApiError(
      response.status,
      messageOf(answer, `${method} ${template} answered ${response.status}`),
      template,
      answer,
    );
  }
  return response;
}

async function request(method: HttpMethod, template: string, options: RawOptions = {}): Promise<unknown> {
  const response = await send(method.toUpperCase(), template, options);
  return readBody(response);
}

/** A typed call: the path must answer the method, and the options are the ones it takes. */
export type Call<M extends HttpMethod> = <P extends PathsWith<M>>(
  path: P,
  ...options: OptionsArgument<RequestOptions<P, M>>
) => Promise<ResponseOf<P, M>>;

function call<M extends HttpMethod>(method: M): Call<M> {
  const untyped = (path: string, options?: RawOptions) => request(method, path, options);
  return untyped as unknown as Call<M>;
}

export const api = {
  get: call('get'),
  post: call('post'),
  put: call('put'),
  delete: call('delete'),
};

/** The untyped door the hooks use once the types have been checked at their own signature. */
export function requestUntyped(method: HttpMethod, path: string, options?: RawOptions): Promise<unknown> {
  return request(method, path, options);
}

/**
 * Sends files as `multipart/form-data`: the files under `fileField` (`files` by default, as the
 * evidence of a skill takes them) and every other field as text.
 */
export async function uploadFiles<P extends PathsWith<'post'>>(
  path: P,
  files: readonly File[],
  fields: Record<string, string | undefined> = {},
  options: { path?: Record<string, string>; fileField?: string; signal?: AbortSignal } = {},
): Promise<ResponseOf<P, 'post'>> {
  const form = new FormData();
  for (const file of files) {
    form.append(options.fileField ?? 'files', file, file.name);
  }
  for (const [name, value] of Object.entries(fields)) {
    if (value !== undefined) form.append(name, value);
  }
  return request('post', path, {
    path: options.path,
    body: form,
    signal: options.signal,
  }) as Promise<ResponseOf<P, 'post'>>;
}

function filenameOf(response: Response, fallback: string): string {
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (encoded) return decodeURIComponent(encoded[1]);
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  return plain ? plain[1] : fallback;
}

/**
 * Downloads an attachment (the indicators' CSV, an evidence file) with the session token, and
 * hands it to the browser to save. The name is the server's when it sends one.
 */
export async function downloadFile<P extends PathsWith<'get'>>(
  path: P,
  filename: string,
  options: { path?: Record<string, string>; query?: Query } = {},
): Promise<void> {
  const response = await send('GET', path, options, '*/*');
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filenameOf(response, filename);
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
