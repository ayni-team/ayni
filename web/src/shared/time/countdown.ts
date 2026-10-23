import { useEffect, useState } from 'react';

/**
 * Countdowns: the five minutes a held hour lasts (`HeldHours.heldUntil`), the 30 seconds before a
 * link can be asked for again. They count against the browser's clock, which is the only one it
 * has; the server decides whether the time ran out.
 */

/** Milliseconds from now until the instant, never negative. */
export function remainingMs(until: string | Date | number, now: number = Date.now()): number {
  const end = typeof until === 'number' ? until : new Date(until).getTime();
  return Math.max(0, end - now);
}

/** "4:32", or "1:04:32" when an hour or more is left. Rounds up, so 0:00 means it is over. */
export function formatCountdown(ms: number): string {
  const total = Math.ceil(Math.max(0, ms) / 1000);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = String(total % 60).padStart(2, '0');
  return hours > 0 ? `${hours}:${String(minutes).padStart(2, '0')}:${seconds}` : `${minutes}:${seconds}`;
}

export interface Countdown {
  remainingMs: number;
  expired: boolean;
  /** As `formatCountdown` writes it. */
  label: string;
}

/** Redraws every second until the instant; `null` means there is nothing to count down to. */
export function useCountdown(until: string | Date | number | null | undefined, stepMs = 1000): Countdown {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (until === null || until === undefined) return undefined;
    setNow(Date.now());
    const timer = window.setInterval(() => {
      const current = Date.now();
      setNow(current);
      if (remainingMs(until, current) === 0) window.clearInterval(timer);
    }, stepMs);
    return () => window.clearInterval(timer);
  }, [until, stepMs]);

  const left = until === null || until === undefined ? 0 : remainingMs(until, now);
  return { remainingMs: left, expired: left === 0, label: formatCountdown(left) };
}
