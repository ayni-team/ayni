import { useApiQuery } from '../api/hooks';
import { belongsToUniversity } from './roles';
import { useSession } from './useSession';

/** The query key of `GET /api/v1/me`: invalidate it after changing the profile or the onboarding. */
export const ME_KEY = ['identity', 'me'] as const;

/**
 * The profile of the person signed in (`MyProfileView`). Only students and coordinators have one;
 * for anybody else it stays idle.
 */
export function useMe() {
  const session = useSession();
  return useApiQuery(ME_KEY, '/api/v1/me', {
    enabled: session !== null && belongsToUniversity(session.role),
    staleTime: 60_000,
  });
}
