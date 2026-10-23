import { isRole, type Role } from './roles';

/**
 * The session the person opened by following their access link.
 *
 * Opening the link returns a session token (TS03). It is the only thing that says who the person
 * is and which university they belong to: the API reads both from it, never from anything else the
 * browser sends. It lives in memory and is mirrored to localStorage, as the guide says, so a reload
 * does not ask for a new link. Signing out revokes it on the server and forgets it here.
 *
 * This module is the only place that holds the token: `shared/api` asks it for the header and
 * nothing else reads it.
 */
export interface Session {
  sessionToken: string;
  /** The university's code. Absent for a platform administrator. */
  tenantId: string | null;
  userId: string;
  role: Role;
  expiresAt: string;
}

const KEY = 'ayni.session';

let memory: Session | null | undefined;
const listeners = new Set<() => void>();

function storage(): Storage | null {
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

function read(): Session | null {
  try {
    const stored = storage()?.getItem(KEY);
    return stored ? parse(JSON.parse(stored)) : null;
  } catch {
    return null;
  }
}

/** Accepts what the server answered (or what was stored) only if it is a whole session. */
export function parse(value: unknown): Session | null {
  if (!value || typeof value !== 'object') return null;
  const candidate = value as Record<string, unknown>;
  if (
    typeof candidate.sessionToken !== 'string' ||
    typeof candidate.userId !== 'string' ||
    typeof candidate.expiresAt !== 'string' ||
    !isRole(candidate.role)
  ) {
    return null;
  }
  return {
    sessionToken: candidate.sessionToken,
    tenantId: typeof candidate.tenantId === 'string' ? candidate.tenantId : null,
    userId: candidate.userId,
    role: candidate.role,
    expiresAt: candidate.expiresAt,
  };
}

function expired(session: Session, now = Date.now()): boolean {
  return new Date(session.expiresAt).getTime() <= now;
}

/**
 * The current session, or null. It returns the same object until the session changes, so React
 * can subscribe to it.
 */
export function currentSession(): Session | null {
  if (memory === undefined) {
    memory = read();
  }
  return memory && !expired(memory) ? memory : null;
}

/** Keeps the session that `POST /api/v1/access/confirm` answered. */
export function rememberSession(answer: unknown): Session {
  const session = parse(answer);
  if (!session) {
    throw new Error('The answer is not a session');
  }
  memory = session;
  try {
    storage()?.setItem(KEY, JSON.stringify(session));
  } catch {
    // Without storage the session lasts until the page is closed.
  }
  notify();
  return session;
}

export function forgetSession(): void {
  if (memory === undefined) memory = read();
  const had = memory !== null;
  memory = null;
  try {
    storage()?.removeItem(KEY);
  } catch {
    // Nothing stored, nothing to forget.
  }
  if (had) notify();
}

/** The value of the `Authorization` header, for `shared/api` only. */
export function authorization(): string | null {
  const session = currentSession();
  return session ? `Bearer ${session.sessionToken}` : null;
}

/** Lets a screen redraw when somebody signs in or out. */
export function onSessionChange(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function notify(): void {
  listeners.forEach((listener) => listener());
}

// Signing out in another tab signs out here too.
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (event) => {
    if (event.key === KEY || event.key === null) {
      memory = read();
      notify();
    }
  });
}

/** Tests start from no session; nothing else calls this. */
export function resetSessionForTests(): void {
  memory = undefined;
  try {
    storage()?.removeItem(KEY);
  } catch {
    // Nothing stored.
  }
}
