import { useSyncExternalStore } from 'react';
import { currentSession, onSessionChange, type Session } from './session';

/** The current session, redrawn whenever somebody signs in or out. */
export function useSession(): Session | null {
  return useSyncExternalStore(onSessionChange, currentSession, currentSession);
}
