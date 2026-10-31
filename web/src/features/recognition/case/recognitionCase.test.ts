import { describe, expect, it } from 'vitest';
import {
  alertTitle,
  decidedAt,
  firstToReview,
  isDecided,
  longDay,
  realFigures,
  reasonError,
  sessionFigures,
  shortDay,
  studentRating,
  taughtText,
  timeRange,
} from './recognitionCase';

const LIMA = 'America/Lima';

describe('the reason of a decision', () => {
  it('is required, trimmed, and up to 500 characters', () => {
    expect(reasonError('')).toBe('Escribe el motivo de tu decisión');
    expect(reasonError('   ')).toBe('Escribe el motivo de tu decisión');
    expect(reasonError('a'.repeat(501))).toBe('El motivo puede tener hasta 500 caracteres');
    expect(reasonError('  Cumple las horas  ')).toBeUndefined();
    expect(reasonError('a'.repeat(500))).toBeUndefined();
  });
});

describe('a decided request', () => {
  it('is one approved or rejected', () => {
    expect(isDecided('APPROVED')).toBe(true);
    expect(isDecided('REJECTED')).toBe(true);
    expect(isDecided('SUBMITTED')).toBe(false);
    expect(isDecided('UNDER_REVIEW')).toBe(false);
    expect(isDecided(undefined)).toBe(false);
  });
});

describe('dates and times', () => {
  it('are written in the university zone, as the frame writes them', () => {
    expect(shortDay('2026-10-20T23:00:00Z', LIMA)).toBe('Mar 20 oct');
    expect(timeRange('2026-10-20T23:00:00Z', '2026-10-20T23:22:00Z', LIMA)).toBe('18:00 – 18:22');
    expect(longDay('2026-10-21T02:00:00Z', LIMA)).toBe('20 oct 2026');
    expect(decidedAt('2026-10-22T16:40:00Z', LIMA)).toBe('22 oct 2026, 11:40');
    expect(shortDay(undefined, LIMA)).toBe('');
    expect(timeRange('nonsense', '2026-10-20T23:22:00Z', LIMA)).toBe('');
    expect(decidedAt(null, LIMA)).toBe('');
  });
});

describe('a session', () => {
  it('says how long it lasted, its hours and its rating', () => {
    expect(sessionFigures({ durationMinutes: 58, hours: 1, stars: 5 })).toBe('58 min · 1 hora · ★ 5');
    expect(sessionFigures({ durationMinutes: 118, hours: 2, stars: null })).toBe(
      '118 min · 2 horas · sin calificación',
    );
  });

  it('opens on the first one with an alert, or on the first one', () => {
    const quiet = { sessionId: 'a', alerts: [] };
    const flagged = { sessionId: 'b', alerts: [{ kind: 'SHORT_SESSIONS' }] };
    expect(firstToReview([quiet, flagged])).toBe('b');
    expect(firstToReview([quiet])).toBe('a');
    expect(firstToReview([])).toBeUndefined();
  });
});

describe('the evidence', () => {
  it('names what was taught, what really happened and the rating of the student', () => {
    const evidence = {
      taught: { name: 'Algoritmos', courseCode: 'CC215' },
      startedAt: '2026-10-20T23:00:00Z',
      endedAt: '2026-10-20T23:22:00Z',
      durationMinutes: 22,
      hours: 1,
      tutorStars: 4,
    };
    expect(taughtText(evidence)).toBe('Algoritmos · CC215');
    expect(taughtText({ taught: { name: 'Figma' } })).toBe('Figma');
    expect(taughtText({})).toBe('Sin dato');
    expect(realFigures(evidence, LIMA)).toBe('18:00 – 18:22 · 22 min · 1 hora');
    expect(studentRating(evidence)).toBe('★ 4');
    expect(studentRating({ tutorStars: null })).toBe('Sin calificación');
  });
});

describe('an alert of the audit', () => {
  it('is named in Spanish with its severity', () => {
    expect(alertTitle({ kind: 'SHORT_SESSIONS', severity: 'MEDIUM' })).toBe('Duración corta · gravedad media');
    expect(alertTitle({ kind: 'REPEATED_PAIR', severity: 'LOW' })).toBe('Pareja repetida · gravedad baja');
    expect(alertTitle({ kind: 'SOMETHING_NEW', severity: 'HIGH' })).toBe('Alerta de auditoría · gravedad alta');
    expect(alertTitle({ kind: 'SHORT_SESSIONS' })).toBe('Duración corta');
  });
});
