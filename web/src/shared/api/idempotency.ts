import { useCallback, useRef } from 'react';

/**
 * Operations that move credits send an `Idempotency-Key`: the same key returns the same result,
 * so a retried request never charges twice. The key belongs to one attempt of one action: it is
 * created when the person starts it, kept while that same action is retried, and replaced after
 * the server answers it for good (a new attempt after a rejection takes a new key).
 */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

export interface IdempotencyKey {
  /** The key of the current attempt, created the first time it is asked for. */
  current: () => string;
  /** Ends the attempt: the next `current()` is a new key. */
  renew: () => void;
}

/**
 * Holds the key of an attempt across renders. A two-step operation (a redemption, a donation)
 * sends the confirmation with the same key as the first call, so it holds the key here and passes
 * it to both calls.
 */
export function useIdempotencyKey(): IdempotencyKey {
  const key = useRef<string | null>(null);
  const current = useCallback(() => {
    key.current ??= newIdempotencyKey();
    return key.current;
  }, []);
  const renew = useCallback(() => {
    key.current = null;
  }, []);
  return useRef({ current, renew }).current;
}
