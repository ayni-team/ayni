import type { Session } from './session';

export type PresenceState = 'UPCOMING' | 'CODE' | 'NO_ATTEMPTS' | 'EXPIRED' | 'CONFIRMED';

/**
 * Where the reader's own code stands. The session gives no code until it is issued, five minutes
 * after the start, and never the code itself: only when it expires and the attempts left.
 */
export function presenceState(session: Session, now: number): PresenceState {
  const presence = session.presence;
  if (!presence) return 'UPCOMING';
  if (presence.confirmedAt) return 'CONFIRMED';
  if (presence.attemptsLeft === 0) return 'NO_ATTEMPTS';
  if (presence.expiresAt && new Date(presence.expiresAt).getTime() <= now) return 'EXPIRED';
  return 'CODE';
}

export const CODE_LENGTH = 6;

/** What was typed or pasted, as the digits the code can take. */
export function codeFrom(text: string): string {
  return text.replace(/\D/g, '').slice(0, CODE_LENGTH);
}

export function attemptsText(attempts: number | undefined): string {
  const left = attempts ?? 0;
  return left === 1 ? '1 intento' : `${left} intentos`;
}

/** "Te queda 1 intento.", "Te quedan 3 intentos." */
export function attemptsLeftText(attempts: number | undefined): string {
  const left = attempts ?? 0;
  return left === 1 ? 'Te queda 1 intento.' : `Te quedan ${left} intentos.`;
}

/** What not confirming means for this person: the tutor loses the hour, the student recovers it. */
export function unverifiedConsequence(session: Session): string {
  return session.role === 'TUTOR'
    ? 'Ya no puedes confirmar esta sesión: quedará sin verificar y no ganarás esta hora.'
    : 'Ya no puedes confirmar esta sesión: quedará sin verificar y tu crédito vuelve a ti.';
}

export function ifNobodyConfirms(session: Session): string {
  return session.role === 'TUTOR'
    ? 'Si alguien no confirma, la sesión queda sin verificar: no ganas horas y el estudiante recupera su crédito.'
    : 'Si alguien no confirma, la sesión queda sin verificar: el tutor no gana horas y tus créditos vuelven a ti.';
}

export function confirmedDetail(session: Session, time: string): string {
  return session.role === 'TUTOR'
    ? `Confirmaste a las ${time}. Si tu estudiante también confirma, la sesión queda verificada.`
    : `Confirmaste a las ${time}. Si tu tutor también confirma, la sesión queda verificada.`;
}
