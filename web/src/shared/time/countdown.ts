import { useEffect, useState } from 'react';

/**
 * Countdowns: the five minutes a held hour lasts (`HeldHours.heldUntil`), the 30 seconds before a
 * link can be asked for again. They count against the browser's clock, which is the only one it
 * has; the server decides whether the time ran out.
 *
 * An instant the server set (`heldUntil`) is counted against the server's time, not the browser's:
 * the demo profile moves the server's clock, and a browser with a wrong clock is wrong the same
 * way. `clockOffset(serverNow)` measures the difference once, when the answer arrives, and
 * `useCountdown` takes it as `offsetMs`.
 */

/**
 * How far the server's clock is ahead of the browser's, in milliseconds (negative when behind):
 * `serverNow` is the server's time in the answer that carried it (`HeldHours.serverNow`), and
 * `received` is the browser's time at that moment. Read it once per answer, not on every render. It
 * includes the trip the answer took, a fraction of a second. A missing or unreadable `serverNow`
 * gives 0, which counts against the browser's clock.
 */
export function clockOffset(
  serverNow: string | Date | number | null | undefined,
  received: number = Date.now(),
): number {
  if (serverNow === null || serverNow === undefined) return 0;
  const server = typeof serverNow === 'number' ? serverNow : new Date(serverNow).getTime();
  return Number.isFinite(server) ? server - received : 0;
}

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

export interface CountdownOptions {
  /** Every how many milliseconds it redraws. */
  stepMs?: number;
  /** The server's clock minus the browser's, from `clockOffset`. Without it the browser's clock decides. */
  offsetMs?: number;
}

/** Redraws every second until the instant; `null` means there is nothing to count down to. */
export function useCountdown(
  until: string | Date | number | null | undefined,
  { stepMs = 1000, offsetMs = 0 }: CountdownOptions = {},
): Countdown {
  const [now, setNow] = useState(() => Date.now() + offsetMs);

  useEffect(() => {
    if (until === null || until === undefined) return undefined;
    setNow(Date.now() + offsetMs);
    const timer = window.setInterval(() => {
      const current = Date.now() + offsetMs;
      setNow(current);
      if (remainingMs(until, current) === 0) window.clearInterval(timer);
    }, stepMs);
    return () => window.clearInterval(timer);
  }, [until, stepMs, offsetMs]);

  const left = until === null || until === undefined ? 0 : remainingMs(until, now);
  return { remainingMs: left, expired: left === 0, label: formatCountdown(left) };
}
