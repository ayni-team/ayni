import { useCallback, useState } from 'react';
import { api } from '../api/client';
import { forgetSession } from './session';

/**
 * Signs out: the token is revoked on the server first (`POST /api/v1/access/logout`) and then
 * forgotten here. If the server cannot be reached the session is forgotten all the same.
 */
export function useSignOut(): { signOut: () => Promise<void>; signingOut: boolean } {
  const [signingOut, setSigningOut] = useState(false);
  const signOut = useCallback(async () => {
    setSigningOut(true);
    try {
      await api.post('/api/v1/access/logout');
    } catch {
      // An expired session is already closed; anything else must not keep the person signed in.
    } finally {
      forgetSession();
      setSigningOut(false);
    }
  }, []);
  return { signOut, signingOut };
}
