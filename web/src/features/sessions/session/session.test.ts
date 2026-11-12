import { describe, expect, it } from 'vitest';
import {
  absentOf,
  arrivalText,
  durationLine,
  waitingCloseLine,
  elapsedText,
  otherName,
  phaseOf,
  roomLine,
  roomNotice,
  waitingNotice,
  whenLine,
  type Session,
} from './session';

const LIMA = 'America/Lima';
const STUDENT = 's1';
const TUTOR = 't1';

const SESSION: Session = {
  id: 'x',
  role: 'STUDENT',
  status: 'SCHEDULED',
  studentId: STUDENT,
  tutorId: TUTOR,
  counterpart: { id: TUTOR, name: 'Julián Arce' },
  catalogItem: { name: 'Estadística Aplicada I' },
  scheduledStart: '2026-10-23T21:00:00Z',
  scheduledEnd: '2026-10-23T22:00:00Z',
  joinOpensAt: '2026-10-23T20:45:00Z',
};

describe('FE-20 session logic', () => {
  it('walks the states of the room', () => {
    expect(phaseOf(SESSION, false)).toBe('BEFORE_OPEN');
    expect(phaseOf(SESSION, true)).toBe('OPEN');
    expect(phaseOf({ ...SESSION, studentJoinedAt: '2026-10-23T20:52:00Z' }, true)).toBe('WAITING');
    // The arrival of the other person is not the reader's.
    expect(phaseOf({ ...SESSION, tutorJoinedAt: '2026-10-23T20:52:00Z' }, true)).toBe('OPEN');
    expect(phaseOf({ ...SESSION, status: 'IN_PROGRESS' }, true)).toBe('LIVE');
    for (const status of ['COMPLETED', 'UNVERIFIED', 'ABANDONED', 'CANCELLED'] as const) {
      expect(phaseOf({ ...SESSION, status }, true)).toBe('CLOSED');
    }
  });

  it('reads the arrival of the tutor when the reader is the tutor', () => {
    const asTutor: Session = { ...SESSION, role: 'TUTOR', studentJoinedAt: '2026-10-23T20:52:00Z' };
    expect(phaseOf(asTutor, true)).toBe('OPEN');
    expect(phaseOf({ ...asTutor, tutorJoinedAt: '2026-10-23T20:53:00Z' }, true)).toBe('WAITING');
  });

  it('writes the day and the hours in the zone of the university', () => {
    expect(whenLine(SESSION, LIMA)).toBe('Viernes 23 de octubre · 16:00 – 17:00 · con Julián Arce (tutor)');
    expect(roomLine(SESSION, 'BEFORE_OPEN', LIMA)).toBe('La sala se abre a las 15:45.');
  });

  it('names who the other person is when their name is not there', () => {
    expect(otherName({ ...SESSION, counterpart: { id: TUTOR, name: null } })).toBe('tu tutor');
    expect(otherName({ role: 'TUTOR' })).toBe('tu estudiante');
  });

  it('only promises the credit back to the student', () => {
    expect(waitingNotice(SESSION, LIMA).detail).toBe(
      'Si no entra antes de las 16:10, la sesión se cancela y te devolvemos el crédito.',
    );
    expect(waitingNotice({ ...SESSION, role: 'TUTOR' }, LIMA).detail).toBe(
      'Si no entra antes de las 16:10, la sesión se cancela.',
    );
  });

  it('tells each person what an absence means for them', () => {
    const tutorAbsent: Session = { ...SESSION, status: 'ABANDONED', absentParticipantIds: [TUTOR] };
    expect(roomNotice(tutorAbsent, 'CLOSED', LIMA)?.title).toBe('Tu tutor no llegó: te devolvimos el crédito');
    expect(roomNotice({ ...tutorAbsent, role: 'TUTOR' }, 'CLOSED', LIMA)?.title).toBe('No entraste a tiempo');

    const studentAbsent: Session = { ...SESSION, status: 'ABANDONED', absentParticipantIds: [STUDENT] };
    expect(roomNotice(studentAbsent, 'CLOSED', LIMA)?.detail).toContain('el crédito no se devuelve');
    expect(roomNotice({ ...studentAbsent, role: 'TUTOR', counterpart: { name: 'Ana' } }, 'CLOSED', LIMA)?.title).toBe(
      'Ana no llegó',
    );

    // When nobody came, the student pays: the absence of the student decides.
    expect(absentOf({ ...SESSION, absentParticipantIds: [TUTOR, STUDENT] })).toBe('STUDENT');
  });

  it('writes each arrival', () => {
    expect(arrivalText('2026-10-23T20:52:00Z', 'ME', SESSION, LIMA).text).toBe('Entraste 15:52');
    expect(arrivalText('2026-10-23T20:51:00Z', 'OTHER', SESSION, LIMA).text).toBe('Entró 15:51');
    expect(arrivalText(null, 'OTHER', SESSION, LIMA).text).toBe('Todavía no');
    expect(arrivalText(null, 'OTHER', { ...SESSION, status: 'ABANDONED' }, LIMA).text).toBe('No entró');
  });

  it('derives how long it lasted, from both being in to the close', () => {
    expect(durationLine({ startedAt: '2026-10-23T21:02:00Z', endedAt: '2026-10-23T22:00:00Z' }, LIMA)).toBe(
      'Duró 58 minutos (de 16:02 a 17:00).',
    );
    expect(durationLine({ startedAt: null, endedAt: null }, LIMA)).toBe('');
  });

  it('says when an unconfirmed end closes on its own', () => {
    expect(waitingCloseLine({ ...SESSION, closesAt: '2026-10-23T22:15:00Z' }, LIMA)).toBe(
      'Esperamos a que Julián Arce también la termine. Si no, se cierra sola a las 17:15.',
    );
  });

  it('counts how long the session has been running', () => {
    const start = '2026-10-23T21:00:00Z';
    expect(elapsedText(start, Date.parse('2026-10-23T21:07:12Z'))).toBe('07:12');
    expect(elapsedText(start, Date.parse('2026-10-23T22:07:12Z'))).toBe('1:07:12');
    expect(elapsedText(null, 0)).toBe('00:00');
  });
});
