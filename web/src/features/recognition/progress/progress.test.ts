import { describe, expect, it } from 'vitest';
import { ApiError } from '@/shared/api';
import {
  dayOf,
  figures,
  isOpen,
  missingText,
  percent,
  refusalText,
  requestLine,
  requestStatus,
  sessionLine,
} from './progress';

const LIMA = 'America/Lima';
const UNDER = {
  earnedHours: 14,
  sessionsCount: 9,
  requiredHours: 20,
  missingHours: 6,
  requirementMet: false,
  canRequest: false,
};
const MET = {
  earnedHours: 22,
  sessionsCount: 12,
  requiredHours: 20,
  missingHours: 0,
  requirementMet: true,
  canRequest: true,
};
const CLOSED = {
  earnedHours: 5,
  sessionsCount: 3,
  requiredHours: null,
  missingHours: null,
  requirementMet: false,
  canRequest: false,
};

function conflict(body: unknown): ApiError {
  return new ApiError(409, 'refused', '/api/v1/recognition/requests', body);
}

describe('the progress', () => {
  it('fills the bar with the hours taught against those asked for, never past the end', () => {
    expect(percent(UNDER)).toBe(70);
    expect(percent(MET)).toBe(100);
    expect(percent(CLOSED)).toBe(0);
    expect(percent(undefined)).toBe(0);
  });

  it('says how many are taught, how many are asked for and how many are missing', () => {
    expect(figures(UNDER)).toBe('14 de 20 horas');
    expect(missingText(UNDER)).toBe('Te faltan 6 horas');
    expect(missingText({ ...UNDER, missingHours: 1 })).toBe('Te faltan 1 hora');
    expect(missingText(MET)).toBe(
      'Llegaste a las horas que pide tu universidad. Ya puedes solicitar el reconocimiento.',
    );
  });

  it('says so when the university has not opened recognition', () => {
    expect(isOpen(CLOSED)).toBe(false);
    expect(isOpen(UNDER)).toBe(true);
    expect(figures(CLOSED)).toBe('5 horas enseñadas');
    expect(missingText(CLOSED)).toBe('Tu universidad todavía no habilitó el reconocimiento');
  });
});

describe('a refused request', () => {
  it('says how many hours are missing, or that recognition is not open', () => {
    expect(refusalText(conflict({ status: 409, missingHours: 4 }))).toBe('Te faltan 4 horas');
    expect(refusalText(conflict({ status: 409, missingHours: 1 }))).toBe('Te faltan 1 hora');
    expect(refusalText(conflict({ status: 409 }))).toBe('Tu universidad todavía no habilitó el reconocimiento');
    expect(refusalText(new Error('boom'))).toBeUndefined();
  });
});

describe('my requests', () => {
  it('name their state in Spanish', () => {
    expect(requestStatus('SUBMITTED').label).toBe('Enviada');
    expect(requestStatus('UNDER_REVIEW').label).toBe('En revisión');
    expect(requestStatus('APPROVED')).toEqual({ label: 'Aprobada', tone: 'info' });
    expect(requestStatus('REJECTED')).toEqual({ label: 'Rechazada', tone: 'danger' });
  });

  it('are dated on the day of the university and say their figures', () => {
    expect(dayOf('2026-10-21T02:00:00Z', LIMA)).toBe('20 oct 2026');
    expect(requestLine({ submittedAt: '2026-10-21T02:00:00Z', totalHours: 20, sessionsCount: 12 }, LIMA)).toBe(
      'Enviada el 20 oct 2026 · 20 horas · 12 sesiones',
    );
    expect(requestLine({ submittedAt: '2026-10-21T02:00:00Z', totalHours: 1, sessionsCount: 1 }, LIMA)).toBe(
      'Enviada el 20 oct 2026 · 1 hora · 1 sesión',
    );
  });

  it('list each session with its day, its times and its hours', () => {
    expect(sessionLine({ startedAt: '2026-10-05T12:55:00Z', endedAt: '2026-10-05T14:57:00Z', hours: 2 }, LIMA)).toBe(
      'Lun 5 oct · 07:55 – 09:57 · 2 horas',
    );
    expect(sessionLine({ hours: 1 }, LIMA)).toBe('1 hora');
  });
});
