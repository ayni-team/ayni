/**
 * The video call runs in the self hosted Jitsi, in its own iframe. There is no npm package: the
 * server of the call serves its own `external_api.js`, so the script always matches the server.
 */

export interface VideoTarget {
  origin: string;
  /** Host and port, what the API of Jitsi calls its domain. */
  domain: string;
  roomName: string;
  /** The Jitsi of docker compose answers on plain http, which the API of Jitsi cannot open. */
  plainHttp: boolean;
}

/** Reads the address of the room (`JoinedSession.video.url`). Only http and https, or nothing. */
export function videoTarget(url: string | undefined): VideoTarget | null {
  if (!url) return null;
  try {
    const parsed = new URL(url);
    if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') return null;
    const roomName = parsed.pathname.replace(/^\/+/, '');
    if (!roomName) return null;
    return { origin: parsed.origin, domain: parsed.host, roomName, plainHttp: parsed.protocol === 'http:' };
  } catch {
    return null;
  }
}

export interface JitsiApi {
  addListener: (event: string, listener: (payload?: unknown) => void) => unknown;
  executeCommand: (command: string, ...args: unknown[]) => void;
  dispose: () => void;
}

export type JitsiConstructor = new (domain: string, options: Record<string, unknown>) => JitsiApi;

declare global {
  interface Window {
    JitsiMeetExternalAPI?: JitsiConstructor;
  }
}

/**
 * The address of a plain iframe, for a Jitsi on http: `external_api.js` always writes `https://` in
 * the address of its iframe, whatever the page. This is only for development, where the browser
 * allows the camera on `localhost`. There are no events: the iframe having loaded is all it knows.
 */
export function plainFrameUrl(url: string, token: string | undefined): string {
  const query = token ? `?jwt=${encodeURIComponent(token)}` : '';
  return `${url}${query}#config.prejoinConfig.enabled=false&config.prejoinPageEnabled=false`;
}

const LOAD_TIMEOUT_MS = 15_000;

const loading = new Map<string, Promise<JitsiConstructor>>();

/** Adds the script of that Jitsi to the page, once. A failure can be tried again. */
export function loadJitsi(origin: string): Promise<JitsiConstructor> {
  const known = loading.get(origin);
  if (known) return known;
  const promise = new Promise<JitsiConstructor>((resolve, reject) => {
    const script = document.createElement('script');
    const fail = () => {
      window.clearTimeout(timer);
      script.remove();
      loading.delete(origin);
      reject(new Error('The script of the video call did not load'));
    };
    const timer = window.setTimeout(fail, LOAD_TIMEOUT_MS);
    script.src = `${origin}/external_api.js`;
    script.async = true;
    script.onerror = fail;
    script.onload = () => {
      window.clearTimeout(timer);
      if (window.JitsiMeetExternalAPI) {
        resolve(window.JitsiMeetExternalAPI);
      } else {
        fail();
      }
    };
    document.head.appendChild(script);
  });
  loading.set(origin, promise);
  return promise;
}

/** Forgets the loaded scripts. For tests, which share the module. */
export function forgetLoadedScripts(): void {
  loading.clear();
}

/** Whether what Jitsi reports ends the call, as opposed to something it recovers from by itself. */
export function isFatal(payload: unknown): boolean {
  const error = (payload as { error?: { isFatal?: unknown } } | undefined)?.error;
  return error?.isFatal === true;
}

/** `screenSharingStatusChanged`: whether this person's own screen is being shared (`{ on }`). */
export function ownShareOn(payload: unknown): boolean {
  return (payload as { on?: unknown } | undefined)?.on === true;
}

/** `contentSharingParticipantsChanged`: whether anybody in the call shares a screen (`{ data: ids }`). */
export function anybodySharing(payload: unknown): boolean {
  const data = (payload as { data?: unknown } | undefined)?.data;
  return Array.isArray(data) && data.length > 0;
}

const RENEW_MARGIN_MS = 30_000;
const RENEW_AT_LEAST_MS = 60_000;

/**
 * When to ask for a new pass. It lets in for five minutes, and never past the end of the session,
 * so near the end the pass does not move: the floor keeps that from being a tight loop.
 */
export function renewDelay(expiresAt: string | undefined, now: number): number | null {
  if (!expiresAt) return null;
  const expiry = new Date(expiresAt).getTime();
  if (!Number.isFinite(expiry)) return null;
  return Math.max(expiry - RENEW_MARGIN_MS - now, RENEW_AT_LEAST_MS);
}

/** How many times the call is opened again by itself before asking the person. */
export const MAX_RECONNECTS = 3;
export const RECONNECT_DELAY_MS = 2000;
