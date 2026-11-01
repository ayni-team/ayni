import { describe, expect, it } from 'vitest';
import { figuresLine, ratingText, sentDay, statusBadge, statusOf } from './queue';

describe('the recognition queue', () => {
  it('asks for what waits on the first tab and for each decision on the others', () => {
    expect(statusOf('PENDING')).toBeUndefined();
    expect(statusOf('APPROVED')).toBe('APPROVED');
    expect(statusOf('REJECTED')).toBe('REJECTED');
  });

  it('names every state in Spanish with its tone', () => {
    expect(statusBadge('SUBMITTED')).toEqual({ label: 'Enviada', tone: 'neutral' });
    expect(statusBadge('UNDER_REVIEW')).toEqual({ label: 'En revisión', tone: 'warning' });
    expect(statusBadge('APPROVED')).toEqual({ label: 'Aprobada', tone: 'info' });
    expect(statusBadge('REJECTED')).toEqual({ label: 'Rechazada', tone: 'danger' });
    expect(statusBadge(undefined).label).toBe('Sin estado');
  });

  it('shows the average with a star, and says so when there is none', () => {
    expect(ratingText(4.8)).toBe('★ 4.8');
    expect(ratingText(5)).toBe('★ 5.0');
    expect(ratingText(null)).toBe('Sin calificaciones');
    expect(ratingText(undefined)).toBe('Sin calificaciones');
  });

  it('dates a request on the day of the university, not of UTC', () => {
    // 02:00 UTC on the 21st is still the 20th in Lima.
    expect(sentDay('2026-10-21T02:00:00Z', 'America/Lima')).toBe('20 oct 2026');
    expect(sentDay(undefined, 'America/Lima')).toBe('');
    expect(sentDay('nonsense', 'America/Lima')).toBe('');
  });

  it('writes the figures of a phone card in words, singular when it is one', () => {
    expect(figuresLine({ totalHours: 42, sessionsCount: 38, averageRating: 4.8 })).toBe(
      '42 horas · 38 sesiones · ★ 4.8',
    );
    expect(figuresLine({ totalHours: 1, sessionsCount: 1, averageRating: null })).toBe(
      '1 hora · 1 sesión · Sin calificaciones',
    );
  });
});
