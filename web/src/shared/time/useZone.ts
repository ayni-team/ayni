import { todayInZone } from './format';
import { useUniversity } from '../brand/BrandProvider';

/**
 * The zone the university's instants are shown in. Lists that carry their own `timezone` pass it
 * instead. Until the university is known (or for a platform administrator, who has none) it is
 * UTC: never the browser's zone.
 */
export const FALLBACK_ZONE = 'UTC';

export function useZone(): string {
  return useUniversity()?.timezone ?? FALLBACK_ZONE;
}

/**
 * The university's today as `YYYY-MM-DD`. Screens never read `Date` or the browser's day: "today"
 * is always this, so a student at 23:30 in Lima (04:30 UTC the next day) sees Lima's day. It is
 * right from the first render because the protected layout waits for the university.
 */
export function useUniversityToday(): string {
  return todayInZone(useZone());
}
