import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  anybodySharing,
  forgetLoadedScripts,
  isFatal,
  loadJitsi,
  ownShareOn,
  plainFrameUrl,
  renewDelay,
  videoTarget,
} from './jitsi';

describe('FE-22 the video pass', () => {
  it('reads the address of the room', () => {
    expect(videoTarget('https://meet.ayni.pe/abc123')).toEqual({
      origin: 'https://meet.ayni.pe',
      domain: 'meet.ayni.pe',
      roomName: 'abc123',
      plainHttp: false,
    });
    expect(videoTarget('http://localhost:8000/abc')).toMatchObject({ domain: 'localhost:8000', plainHttp: true });
  });

  it('refuses an address it should not load a script from', () => {
    expect(videoTarget('javascript:alert(1)')).toBeNull();
    expect(videoTarget('not a url')).toBeNull();
    expect(videoTarget('https://meet.ayni.pe/')).toBeNull();
    expect(videoTarget(undefined)).toBeNull();
  });

  it('reads the sharing events with the payloads of the real external_api.js', () => {
    expect(ownShareOn({ on: true, details: { sourceType: 'screen' } })).toBe(true);
    expect(ownShareOn({ on: false })).toBe(false);
    expect(ownShareOn(undefined)).toBe(false);
    expect(anybodySharing({ data: ['a1b2'] })).toBe(true);
    expect(anybodySharing({ data: [] })).toBe(false);
    expect(anybodySharing({ data: 'a1b2' })).toBe(false);
    expect(anybodySharing(null)).toBe(false);
  });

  it('writes the address of the iframe of the Jitsi of docker compose', () => {
    expect(plainFrameUrl('http://localhost:8000/abc', 'a.b.c')).toBe(
      'http://localhost:8000/abc?jwt=a.b.c#config.prejoinConfig.enabled=false&config.prejoinPageEnabled=false',
    );
  });

  it('asks for a new pass before it expires, and never in a tight loop near the end', () => {
    const now = Date.parse('2026-10-23T21:00:00Z');
    expect(renewDelay('2026-10-23T21:05:00Z', now)).toBe(270_000);
    expect(renewDelay('2026-10-23T21:00:10Z', now)).toBe(60_000);
    expect(renewDelay(undefined, now)).toBeNull();
  });

  it('tells a fatal error from one Jitsi recovers from', () => {
    expect(isFatal({ error: { isFatal: true } })).toBe(true);
    expect(isFatal({ error: { isFatal: false } })).toBe(false);
    expect(isFatal(undefined)).toBe(false);
  });
});

describe('FE-22 the script of Jitsi', () => {
  afterEach(() => {
    forgetLoadedScripts();
    document.head.querySelectorAll('script').forEach((script) => script.remove());
    delete window.JitsiMeetExternalAPI;
  });

  function scripts() {
    return document.head.querySelectorAll<HTMLScriptElement>('script');
  }

  it('adds it once, from the server of the call', async () => {
    const first = loadJitsi('http://localhost:8000');
    const second = loadJitsi('http://localhost:8000');
    expect(scripts()).toHaveLength(1);
    expect(scripts()[0].src).toBe('http://localhost:8000/external_api.js');

    const Jitsi = vi.fn();
    window.JitsiMeetExternalAPI = Jitsi as never;
    scripts()[0].dispatchEvent(new Event('load'));

    await expect(first).resolves.toBe(Jitsi);
    await expect(second).resolves.toBe(Jitsi);
  });

  it('can be tried again after failing', async () => {
    const first = loadJitsi('http://localhost:8000');
    scripts()[0].dispatchEvent(new Event('error'));
    await expect(first).rejects.toThrow();
    expect(scripts()).toHaveLength(0);

    void loadJitsi('http://localhost:8000').catch(() => undefined);
    expect(scripts()).toHaveLength(1);
    scripts()[0].dispatchEvent(new Event('error'));
  });
});
