/**
 * How far the server's clock is ahead of the browser's, kept from the answers of the API: each one
 * carries `X-Server-Time`, and the client notes it as it arrives. The demo profile moves the server's
 * clock, and a browser's clock may be minutes off by itself. Until an answer says otherwise the
 * difference is 0, which counts against the browser's clock.
 *
 * It has no imports on purpose: `shared/api` reads it, and the rest of `shared/time` reads the brand,
 * which reads `shared/api`.
 */
let offsetMs = 0;

/** Notes the time of an answer. A missing or unreadable header leaves what was known. */
export function noteServerTime(header: string | null | undefined, received: number = Date.now()): void {
  if (!header) return;
  const server = new Date(header).getTime();
  if (Number.isFinite(server)) offsetMs = server - received;
}

/** The server's clock minus the browser's, in milliseconds (negative when behind). */
export function serverOffset(): number {
  return offsetMs;
}

/** The current instant in the server's clock, in milliseconds since the epoch. For instants, not days. */
export function serverNow(): number {
  return Date.now() + offsetMs;
}

/** Forgets what was noted. For tests, which share the module. */
export function resetServerClock(): void {
  offsetMs = 0;
}
