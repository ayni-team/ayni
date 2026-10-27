import { useEffect, useState } from 'react';
import { useApiQuery } from '../api/hooks';
import type { Schema } from '../api';
import { useBrand } from './BrandProvider';

/** Something that looks like an address: enough to ask for its university. */
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/** How long the sign-in screen waits after the last key before asking (FE-01). */
export const PREVIEW_DEBOUNCE_MS = 400;

export type PreviewStatus = 'idle' | 'searching' | 'found' | 'notAffiliated' | 'error';

function useDebounced<T>(value: T, delay: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delay);
    return () => window.clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

/** The domain of something that looks like an address: all that is ever sent to look a university up. */
export function domainOf(email: string): string {
  return email.slice(email.lastIndexOf('@') + 1);
}

/**
 * Before signing in: once the email looks like an address, asks for the university of its domain
 * and paints its name, logo and colours on the whole page. Only the domain travels
 * (`?domain=`), never the address. A domain of no university answers 404 (`notAffiliated`).
 * Leaving the screen goes back to Ayni's colours.
 */
export function useUniversityPreview(email: string): {
  status: PreviewStatus;
  university: Schema<'UniversityIdentityView'> | null;
} {
  const { setPreview } = useBrand();
  const typed = email.trim().toLowerCase();
  const debounced = useDebounced(typed, PREVIEW_DEBOUNCE_MS);
  const ready = EMAIL_PATTERN.test(debounced);

  const domain = ready ? domainOf(debounced) : '';

  const query = useApiQuery(['identity', 'university-preview', { domain }], '/api/v1/access/university', {
    query: { domain },
    enabled: ready,
    retry: false,
    staleTime: 5 * 60_000,
  });

  let status: PreviewStatus;
  if (!EMAIL_PATTERN.test(typed)) status = 'idle';
  else if (typed !== debounced || query.isPending) status = 'searching';
  else if (query.isError) status = query.error.status === 404 ? 'notAffiliated' : 'error';
  else status = 'found';

  const found = status === 'found' ? (query.data ?? null) : null;

  // While a new address is being looked up the colours stay as they were, so they do not flicker.
  useEffect(() => {
    if (status !== 'searching') setPreview(found);
  }, [status, found, setPreview]);

  useEffect(() => () => setPreview(null), [setPreview]);

  return { status, university: found };
}
