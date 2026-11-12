import { describe, expect, it } from 'vitest';
import { attemptsLeftText, attemptsText, codeFrom, presenceState, unverifiedConsequence } from './presence';

const NOW = Date.parse('2026-10-23T21:10:00Z');
const CODE = { expiresAt: '2026-10-23T21:20:00Z', attemptsLeft: 5, confirmedAt: null };

describe('FE-21 presence logic', () => {
  it('walks the states of the code', () => {
    expect(presenceState({ presence: null }, NOW)).toBe('UPCOMING');
    expect(presenceState({ presence: CODE }, NOW)).toBe('CODE');
    expect(presenceState({ presence: { ...CODE, confirmedAt: '2026-10-23T21:07:00Z' } }, NOW)).toBe('CONFIRMED');
    expect(presenceState({ presence: { ...CODE, attemptsLeft: 0 } }, NOW)).toBe('NO_ATTEMPTS');
    expect(presenceState({ presence: CODE }, Date.parse('2026-10-23T21:20:00Z'))).toBe('EXPIRED');
  });

  it('keeps only digits, six at most, so a pasted code with spaces works', () => {
    expect(codeFrom('48 19 02')).toBe('481902');
    expect(codeFrom('12345678')).toBe('123456');
    expect(codeFrom('abc')).toBe('');
  });

  it('writes the attempts in singular and plural', () => {
    expect(attemptsText(5)).toBe('5 intentos');
    expect(attemptsText(1)).toBe('1 intento');
    expect(attemptsLeftText(1)).toBe('Te queda 1 intento.');
    expect(attemptsLeftText(3)).toBe('Te quedan 3 intentos.');
  });

  it('says what not confirming costs each one', () => {
    expect(unverifiedConsequence({ role: 'STUDENT' })).toContain('tu crédito vuelve a ti');
    expect(unverifiedConsequence({ role: 'TUTOR' })).toContain('no ganarás esta hora');
  });
});
