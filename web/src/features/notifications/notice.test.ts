import { describe, expect, it } from 'vitest';
import { bellCount, bellLabel, noticeView, noticeWhen, unreadSummary, type Notice } from './notice';

const LIMA = 'America/Lima';
const NOW = new Date('2026-10-23T15:00:00Z');

function notice(kind: Notice['kind'], payload: Record<string, string> = {}, readAt: string | null = null): Notice {
  return { id: 'n1', kind, payload, createdAt: '2026-10-23T14:55:00Z', readAt };
}

describe('noticeView', () => {
  it('writes every kind of the guide in Spanish, whatever the server sends', () => {
    const titles = (
      [
        'BOOKING_CONFIRMED',
        'BOOKING_CANCELLED',
        'CREDITS_EXPIRING',
        'SESSION_CLOSED',
        'TUTOR_NO_SHOW',
        'PURCHASE_CONFIRMED',
        'REQUEST_RESOLVED',
        'VALIDATION_RESOLVED',
      ] as const
    ).map((kind) => noticeView(notice(kind), LIMA).title);

    expect(titles).toEqual([
      'Tu tutoría quedó confirmada',
      'Se canceló una tutoría',
      'Tienes créditos por vencer',
      'Tu sesión se cerró',
      'Tu tutor no llegó: te devolvimos el crédito',
      'Tu compra se acreditó',
      'Se resolvió tu solicitud de reconocimiento',
      'Revisamos la evidencia de tu habilidad',
    ]);
  });

  it('gives a kind it does not know a neutral text, a bell and no link', () => {
    const view = noticeView(notice('SESSION_REMINDER'), LIMA);

    expect(view).toMatchObject({ title: 'Tienes un aviso nuevo', icon: 'bell', to: undefined });
  });

  it('says when a confirmed tutoring starts, in the university zone, and leads each side to its screen', () => {
    const student = noticeView(
      notice('BOOKING_CONFIRMED', { startsAt: '2026-10-27T22:00:00Z', role: 'STUDENT' }),
      LIMA,
    );
    const tutor = noticeView(notice('BOOKING_CONFIRMED', { startsAt: '2026-10-27T22:00:00Z', role: 'TUTOR' }), LIMA);

    expect(student.detail).toBe('Martes 27 de octubre, 17:00');
    expect(student.to).toBe('/mis-tutorias');
    expect(tutor.to).toBe('/tutor/agenda');
  });

  it('says who cancelled and sends the notice to the other side', () => {
    const byTutor = noticeView(notice('BOOKING_CANCELLED', { cancelledBy: 'TUTOR' }), LIMA);
    const byStudent = noticeView(notice('BOOKING_CANCELLED', { cancelledBy: 'STUDENT' }), LIMA);

    expect(byTutor).toMatchObject({ detail: 'La canceló el tutor', to: '/mis-tutorias' });
    expect(byStudent).toMatchObject({ detail: 'La canceló el estudiante', to: '/tutor/agenda' });
  });

  it('counts the credits about to expire and the day they do', () => {
    const many = noticeView(notice('CREDITS_EXPIRING', { amount: '3', expiresAt: '2026-10-31T23:00:00Z' }), LIMA);
    const one = noticeView(notice('CREDITS_EXPIRING', { amount: '1', expiresAt: '2026-10-31T23:00:00Z' }), LIMA);

    expect(many.detail).toBe('3 créditos vencen el 31 de octubre');
    expect(one.detail).toBe('1 crédito vence el 31 de octubre');
    expect(many.to).toBe('/creditos');
  });

  it('asks the student to rate a completed session and links to it', () => {
    const student = noticeView(
      notice('SESSION_CLOSED', { sessionId: 's1', outcome: 'COMPLETED', role: 'STUDENT' }),
      LIMA,
    );
    const tutor = noticeView(notice('SESSION_CLOSED', { sessionId: 's1', outcome: 'COMPLETED', role: 'TUTOR' }), LIMA);
    const unverified = noticeView(notice('SESSION_CLOSED', { sessionId: 's1', outcome: 'UNVERIFIED' }), LIMA);

    expect(student).toMatchObject({ detail: 'Completada · Califícala', to: '/sesion/s1' });
    expect(tutor.detail).toBe('Completada');
    expect(unverified.detail).toBe('Sin verificar');
  });

  it('says whether a request or an evidence was approved', () => {
    expect(noticeView(notice('REQUEST_RESOLVED', { approved: 'true' }), LIMA)).toMatchObject({
      detail: 'Aprobada',
      to: '/reconocimiento',
    });
    expect(noticeView(notice('VALIDATION_RESOLVED', { approved: 'false' }), LIMA)).toMatchObject({
      detail: 'Rechazada',
      to: '/tutor/habilidades',
    });
  });

  it('leads a purchase to its receipt and leaves out what the payload does not carry', () => {
    expect(noticeView(notice('PURCHASE_CONFIRMED', { purchaseId: 'p1' }), LIMA).to).toBe('/creditos/compras/p1');
    expect(noticeView(notice('TUTOR_NO_SHOW', {}), LIMA).to).toBeUndefined();
    expect(noticeView(notice('BOOKING_CONFIRMED', { startsAt: 'not a date' }), LIMA).detail).toBeUndefined();
    expect(noticeView({ id: 'n2' }, LIMA)).toMatchObject({ title: 'Tienes un aviso nuevo', unread: true });
  });

  it('is unread while it has no reading date', () => {
    expect(noticeView(notice('PURCHASE_CONFIRMED'), LIMA).unread).toBe(true);
    expect(noticeView(notice('PURCHASE_CONFIRMED', {}, '2026-10-23T15:00:00Z'), LIMA).unread).toBe(false);
  });
});

describe('noticeWhen', () => {
  it('is relative within a week and the day before that', () => {
    expect(noticeWhen('2026-10-23T14:55:00Z', LIMA, NOW)).toBe('hace 5 min');
    expect(noticeWhen('2026-10-20T15:00:00Z', LIMA, NOW)).toBe('hace 3 días');
    expect(noticeWhen('2026-10-22T14:00:00Z', LIMA, NOW)).toBe('hace 1 día');
    expect(noticeWhen('2026-10-12T15:00:00Z', LIMA, NOW)).toBe('12 oct');
    expect(noticeWhen(undefined, LIMA, NOW)).toBe('');
  });
});

describe('the counter', () => {
  it('says how many are unread, in words and in the badge', () => {
    expect(unreadSummary(3)).toBe('3 sin leer · los más nuevos primero');
    expect(unreadSummary(0)).toBe('Todo leído · los más nuevos primero');
    expect(bellLabel(3)).toBe('Avisos, 3 sin leer');
    expect(bellLabel(undefined)).toBe('Avisos, ninguno sin leer');
    expect(bellCount(0)).toBeNull();
    expect(bellCount(7)).toBe('7');
    expect(bellCount(120)).toBe('99+');
  });
});
