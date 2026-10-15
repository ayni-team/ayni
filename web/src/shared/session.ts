/**
 * The session the person opened by following their access link.
 *
 * Opening the link returns a session token (TS03). It is the only thing that
 * says who the person is and which university they belong to: the API reads
 * both from it, never from anything the browser sends besides it. It is kept
 * in localStorage so a reload does not ask for a new link; signing out
 * revokes it on the server and forgets it here.
 */
export interface Session {
  sessionToken: string;
  tenantId: string | null;
  userId: string;
  role: 'STUDENT' | 'COORDINATOR' | 'PLATFORM_ADMIN';
  expiresAt: string;
}

const KEY = 'ayni.session';

export function currentSession(): Session | null {
  try {
    const stored = localStorage.getItem(KEY);
    if (!stored) {
      return null;
    }
    const session = JSON.parse(stored) as Session;
    if (new Date(session.expiresAt).getTime() <= Date.now()) {
      forgetSession();
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

export function rememberSession(session: Session): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(session));
  } catch {
    // Without storage the session lasts until the page is closed.
  }
  notify();
}

export function forgetSession(): void {
  try {
    localStorage.removeItem(KEY);
  } catch {
    // Nothing stored, nothing to forget.
  }
  notify();
}

const listeners = new Set<() => void>();

/** Lets a screen redraw when somebody signs in or out. */
export function onSessionChange(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

function notify(): void {
  listeners.forEach((listener) => listener());
}
