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
