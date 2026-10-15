import { useEffect, useState } from 'react';
import { currentSession, onSessionChange, Session } from './session';

/** The current session, redrawn whenever somebody signs in or out. */
export function useSession(): Session | null {
  const [session, setSession] = useState(currentSession);

  useEffect(() => {
    const unsubscribe = onSessionChange(() => setSession(currentSession()));
    return () => {
      unsubscribe();
    };
  }, []);

  return session;
}
