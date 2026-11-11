import { isApiError } from '@/shared/api';

/**
 * The practice rooms: a tool of the team to try the screen of a session, its video and its
 * whiteboard, between two accounts and without leaving any record. The backend keeps them in memory
 * and answers the endpoints of a session for their id; a session read with `practice` true is one.
 */

/** The whole address to share: `window.location.origin` and the path the backend answered. */
export function practiceLink(origin: string, path: string | undefined): string | null {
  if (!path || !path.startsWith('/')) return null;
  return `${origin.replace(/\/+$/, '')}${path}`;
}

/** Why a room could not be opened, as the page tells it. */
export type OpenRefusal = 'OFF' | 'FULL' | 'OTHER';

export function openRefusal(failure: unknown): OpenRefusal {
  // The switch is off: the backend answers the route as if it did not exist.
  if (isApiError(failure, 404)) return 'OFF';
  if (isApiError(failure, 409)) return 'FULL';
  return 'OTHER';
}

/**
 * Whether the screen of a session should go back to `/practica`: the session was a practice room
 * and it ended, which the backend says with `COMPLETED` to the second person and, once the room is
 * forgotten, with a 404 to whoever was still looking at it.
 */
export function practiceIsOver(session: { practice?: boolean; status?: string } | undefined, error: unknown): boolean {
  if (!session?.practice) return false;
  return session.status === 'COMPLETED' || isApiError(error, 404);
}

/** What the page at `/practica` receives when a room just ended. */
export interface PracticeEnded {
  practiceEnded: true;
}

export function endedFrom(state: unknown): boolean {
  return Boolean(state && typeof state === 'object' && (state as Partial<PracticeEnded>).practiceEnded);
}
