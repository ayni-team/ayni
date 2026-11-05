import { describe, expect, it } from 'vitest';
import { absenceText, didNotHappen, durationText, stateOf, whenLine } from './review';

const LIMA = 'America/Lima';

describe('FE-24 review logic', () => {
  it('says what happened in its own words', () => {
    expect(stateOf('COMPLETED').note).toBe('La sesión se realizó y los dos confirmaron su presencia.');
    expect(stateOf('ABANDONED').label).toBe('Abandonada');
    expect(stateOf(undefined).label).toBe('Sin estado');
  });

  it('compares the real duration with the booked one', () => {
    expect(durationText({ realDurationMinutes: 61, bookedMinutes: 60 })).toBe('Duró 61 minutos de los 60 reservados');
    expect(durationText({ realDurationMinutes: 1 })).toBe('Duró 1 minuto');
    expect(durationText({ realDurationMinutes: null, bookedMinutes: 60 })).toBeNull();
  });

  it('knows a session that did not take place', () => {
    expect(didNotHappen({ status: 'CANCELLED' })).toBe(true);
    expect(didNotHappen({ status: 'ABANDONED' })).toBe(true);
    expect(didNotHappen({ status: 'UNVERIFIED' })).toBe(false);
  });

  it('names who did not come, from whoever reads', () => {
    const counterpart = { id: 't', name: 'Julián Arce' };
    expect(absenceText({ counterpart, absentParticipantIds: ['t'] })).toBe('Julián Arce no entró a tiempo.');
    expect(absenceText({ counterpart, absentParticipantIds: ['me'] })).toBe('No entraste a tiempo.');
    expect(absenceText({ counterpart, absentParticipantIds: ['t', 'me'] })).toBe('Ninguno de los dos entró a tiempo.');
    expect(absenceText({ counterpart, absentParticipantIds: [] })).toBeNull();
  });

  it('writes the day and the hours in the zone of the university', () => {
    expect(
      whenLine(
        { scheduledStart: '2026-10-23T21:00:00Z', scheduledEnd: '2026-10-23T22:00:00Z', counterpart: { name: 'Ana' } },
        LIMA,
      ),
    ).toBe('Viernes 23 de octubre · 16:00 – 17:00 · con Ana');
  });
});
