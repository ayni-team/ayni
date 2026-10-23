import { describe, expect, it } from 'vitest';
import { authorization, currentSession, forgetSession, onSessionChange, parse, rememberSession } from './session';

const answer = {
  sessionToken: 'secret',
  tenantId: 'UPC',
  userId: 'u1',
  role: 'STUDENT',
  expiresAt: '2099-01-01T00:00:00Z',
};

describe('session', () => {
  it('keeps the session the link opened, across a reload', () => {
    rememberSession(answer);
    expect(currentSession()).toMatchObject({ role: 'STUDENT', tenantId: 'UPC' });
    expect(JSON.parse(localStorage.getItem('ayni.session') ?? '{}').sessionToken).toBe('secret');
  });

  it('gives the token only as the Authorization header', () => {
    expect(authorization()).toBeNull();
    rememberSession(answer);
    expect(authorization()).toBe('Bearer secret');
  });

  it('has no university for a platform administrator', () => {
    rememberSession({ ...answer, role: 'PLATFORM_ADMIN', tenantId: undefined });
    expect(currentSession()?.tenantId).toBeNull();
  });

  it('forgets an expired session', () => {
    rememberSession({ ...answer, expiresAt: '2000-01-01T00:00:00Z' });
    expect(currentSession()).toBeNull();
  });

  it('refuses something that is not a session', () => {
    expect(parse({ ...answer, role: 'TUTOR' })).toBeNull();
    expect(parse(null)).toBeNull();
    expect(() => rememberSession({ sessionToken: 'x' })).toThrow();
  });

  it('tells whoever listens when somebody signs in or out', () => {
    const changes: (string | null)[] = [];
    const stop = onSessionChange(() => changes.push(currentSession()?.role ?? null));
    rememberSession(answer);
    forgetSession();
    forgetSession();
    stop();
    expect(changes).toEqual(['STUDENT', null]);
    expect(localStorage.getItem('ayni.session')).toBeNull();
  });
});
