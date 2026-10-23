/**
 * What a failed call becomes.
 *
 * Every error the server answers has `timestamp, status, error, message, path`. The `message` is
 * English and technical: it is for logs and for whoever debugs, never for the person using the
 * screen. Screens decide what to say from the status, the `kind` or the body (`errorText` gives the
 * text of the guide's table when the screen has nothing more specific).
 */

export type ApiErrorKind =
  | 'badRequest'
  | 'unauthorized'
  | 'forbidden'
  | 'notFound'
  | 'conflict'
  | 'unprocessable'
  | 'server'
  | 'network'
  | 'unknown';

/** The shape every error of the API shares. */
export interface ServerError {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
}

export class ApiError extends Error {
  readonly kind: ApiErrorKind;

  constructor(
    /** HTTP status; 0 when the server could not be reached. */
    readonly status: number,
    /** The server's message, in English. Never shown to people. */
    message: string,
    /** The path that was called, without its query. */
    readonly path: string,
    /** The parsed body of the answer, when it had one. Some 409 carry fields a screen reads. */
    readonly body: unknown = undefined,
  ) {
    super(message);
    this.name = 'ApiError';
    this.kind = kindOf(status);
  }

  /** The server could not be reached: no network, a proxy without backend. */
  static network(path: string): ApiError {
    return new ApiError(0, 'The server could not be reached', path);
  }

  /** Whether trying the same thing again may work: the platform failed, not the request. */
  get retriable(): boolean {
    return this.kind === 'network' || this.kind === 'server';
  }
}

export function kindOf(status: number): ApiErrorKind {
  if (status === 0) return 'network';
  if (status === 400) return 'badRequest';
  if (status === 401) return 'unauthorized';
  if (status === 403) return 'forbidden';
  if (status === 404) return 'notFound';
  if (status === 409) return 'conflict';
  if (status === 422) return 'unprocessable';
  if (status >= 500) return 'server';
  return 'unknown';
}

export function isApiError(error: unknown, status?: number): error is ApiError {
  return error instanceof ApiError && (status === undefined || error.status === status);
}

/**
 * The body of a failed answer, typed by whoever reads it (a 409 with `late` and
 * `refundWillBeIssued`, a 409 with `missingHours`). Undefined when it was not an API error.
 */
export function errorBody<T>(error: unknown): T | undefined {
  return isApiError(error) ? (error.body as T | undefined) : undefined;
}

const TEXTS: Record<ApiErrorKind, string> = {
  badRequest: 'Revisa los datos e inténtalo de nuevo',
  unauthorized: 'Tu sesión terminó. Vuelve a ingresar',
  forbidden: 'No tienes permiso para esto',
  notFound: 'No encontramos lo que buscas',
  conflict: 'No se pudo completar. Actualiza la página e inténtalo de nuevo',
  unprocessable: 'Revisa el valor e inténtalo de nuevo',
  server: 'Algo falló de nuestro lado. Inténtalo de nuevo',
  network: 'No pudimos conectar con Ayni. Revisa tu conexión e inténtalo de nuevo',
  unknown: 'Algo salió mal. Inténtalo de nuevo',
};

/**
 * The Spanish text for an error, following the table of the guide. A screen passes its own texts
 * for the kinds it knows better (a 409 of its own, a 422 of a wrong code).
 */
export function errorText(error: unknown, overrides: Partial<Record<ApiErrorKind, string>> = {}): string {
  const kind = isApiError(error) ? error.kind : 'unknown';
  return overrides[kind] ?? TEXTS[kind];
}
