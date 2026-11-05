import type { Schema } from '@/shared/api';
import { formatInZone } from '@/shared/time';

export type Session = Schema<'Session'>;
export type JoinedSession = Schema<'JoinedSession'>;
export type SessionStatus = NonNullable<Session['status']>;

/** How the room stands for the person reading: what the screen has to offer them. */
export type Phase = 'BEFORE_OPEN' | 'OPEN' | 'WAITING' | 'LIVE' | 'CLOSED';

/** A session in one of these will not change any more, so there is nothing to keep asking. */
export function isOver(status: Session['status']): boolean {
  return status === 'COMPLETED' || status === 'UNVERIFIED' || status === 'ABANDONED' || status === 'CANCELLED';
}

/** When the reader first arrived: the session tells both arrivals, and `role` says which is theirs. */
export function myArrival(session: Session): string | null {
  return (session.role === 'TUTOR' ? session.tutorJoinedAt : session.studentJoinedAt) ?? null;
}

export function otherArrival(session: Session): string | null {
  return (session.role === 'TUTOR' ? session.studentJoinedAt : session.tutorJoinedAt) ?? null;
}

/** The state machine of the room. `roomOpen` is whether `joinOpensAt` passed on the server's clock. */
export function phaseOf(session: Session, roomOpen: boolean): Phase {
  if (isOver(session.status)) return 'CLOSED';
  if (session.status === 'IN_PROGRESS') return 'LIVE';
  if (myArrival(session)) return 'WAITING';
  return roomOpen ? 'OPEN' : 'BEFORE_OPEN';
}

/** The other person's name, or who they are when the other module no longer shows it. */
export function otherName(session: Session): string {
  return session.counterpart?.name || (session.role === 'TUTOR' ? 'tu estudiante' : 'tu tutor');
}

/** What the other person is here. No gender: the design says "tutor". */
export function otherRole(session: Session): string {
  return session.role === 'TUTOR' ? 'estudiante' : 'tutor';
}

export function courseName(session: Session): string {
  return session.catalogItem?.name || 'Sesión';
}

function capitalized(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** "16:00 – 17:00". */
export function hoursLine(session: Session, zone: string): string {
  if (!session.scheduledStart || !session.scheduledEnd) return '';
  return `${formatInZone(session.scheduledStart, zone, 'time')} – ${formatInZone(session.scheduledEnd, zone, 'time')}`;
}

/** "Viernes 23 de octubre · 16:00 – 17:00 · con Julián Arce (tutor)". */
export function whenLine(session: Session, zone: string): string {
  const day = session.scheduledStart
    ? capitalized(formatInZone(session.scheduledStart, zone, 'longDate').replace(',', ''))
    : '';
  const hours = hoursLine(session, zone);
  return [day, hours, `con ${otherName(session)} (${otherRole(session)})`].filter(Boolean).join(' · ');
}

const NO_SHOW_MINUTES = 10;

/** "16:10": ten minutes after the start, when somebody who has not entered makes the session abandoned. */
export function noShowLimit(session: Session, zone: string): string {
  if (!session.scheduledStart) return '';
  const limit = new Date(new Date(session.scheduledStart).getTime() + NO_SHOW_MINUTES * 60_000);
  return formatInZone(limit, zone, 'time');
}

export type Tone = 'neutral' | 'brand' | 'success' | 'warning' | 'danger' | 'info';

const STATUS: Record<SessionStatus, { label: string; tone: Tone }> = {
  SCHEDULED: { label: 'Programada', tone: 'neutral' },
  IN_PROGRESS: { label: 'En curso', tone: 'brand' },
  COMPLETED: { label: 'Completada', tone: 'info' },
  UNVERIFIED: { label: 'Sin verificar', tone: 'warning' },
  ABANDONED: { label: 'Abandonada', tone: 'warning' },
  CANCELLED: { label: 'Cancelada', tone: 'neutral' },
};

export function statusBadge(status: Session['status']): { label: string; tone: Tone } {
  return (status && STATUS[status]) || { label: 'Sin estado', tone: 'neutral' };
}

/** Who did not come: the student decides who pays (backend guide, "An absence"), so they go first. */
export function absentOf(session: Session): 'STUDENT' | 'TUTOR' | null {
  const absent = session.absentParticipantIds ?? [];
  if (session.studentId && absent.includes(session.studentId)) return 'STUDENT';
  if (session.tutorId && absent.includes(session.tutorId)) return 'TUTOR';
  return null;
}

export interface RoomNotice {
  tone: 'info' | 'warning';
  title: string;
  detail: string;
  action?: { to: string; label: string };
}

/** The line that waits for the other person. Only a tutor who does not come gives the credit back. */
export function waitingNotice(session: Session, zone: string): RoomNotice {
  const name = otherName(session);
  const limit = noShowLimit(session, zone);
  const consequence =
    session.role === 'TUTOR' ? 'la sesión se cancela.' : 'la sesión se cancela y te devolvemos el crédito.';
  return {
    tone: 'info',
    title: `Esperando a ${name}`,
    detail: `Si no entra antes de las ${limit}, ${consequence}`,
  };
}

const BACK_TO_SEARCH = { to: '/buscar', label: 'Buscar otra hora' };
const BACK_TO_AGENDA = { to: '/tutor/agenda', label: 'Volver a mi agenda' };

/** What an abandoned session means for the person reading, from who did not come. */
function abandonedNotice(session: Session, zone: string): RoomNotice {
  const absent = absentOf(session);
  const student = session.role !== 'TUTOR';
  const limit = noShowLimit(session, zone);
  const name = otherName(session);
  if (absent === 'TUTOR') {
    return student
      ? {
          tone: 'warning',
          title: 'Tu tutor no llegó: te devolvimos el crédito',
          detail: `${name} no entró antes de las ${limit}. Puedes reservar otra hora.`,
          action: BACK_TO_SEARCH,
        }
      : {
          tone: 'warning',
          title: 'No entraste a tiempo',
          detail: `La sesión se canceló a las ${limit} y le devolvimos el crédito a ${name}.`,
          action: BACK_TO_AGENDA,
        };
  }
  if (absent === 'STUDENT') {
    return student
      ? {
          tone: 'warning',
          title: 'No entraste a tiempo',
          detail: `La sesión se canceló a las ${limit}. Esta hora ya estaba reservada para ti: el crédito no se devuelve.`,
          action: BACK_TO_SEARCH,
        }
      : {
          tone: 'warning',
          title: `${capitalized(name)} no llegó`,
          detail: `No entró antes de las ${limit}. Como no hubo sesión, esta hora no suma a tus créditos.`,
          action: BACK_TO_AGENDA,
        };
  }
  return {
    tone: 'warning',
    title: 'La sesión no se realizó',
    detail: 'Alguien no entró a tiempo.',
    action: student ? BACK_TO_SEARCH : BACK_TO_AGENDA,
  };
}

/** The notice of the room, if it has one to give. */
export function roomNotice(session: Session, phase: Phase, zone: string): RoomNotice | null {
  if (phase === 'WAITING') return waitingNotice(session, zone);
  if (phase !== 'CLOSED') return null;
  const student = session.role !== 'TUTOR';
  switch (session.status) {
    case 'ABANDONED':
      return abandonedNotice(session, zone);
    case 'CANCELLED':
      return {
        tone: 'info',
        title: 'Esta tutoría se canceló',
        detail: 'Ya no hay sala para esta hora.',
        action: student ? { to: '/mis-tutorias', label: 'Volver a mis tutorías' } : BACK_TO_AGENDA,
      };
    default:
      return null;
  }
}

/** The line above the participants, while the room has not started. */
export function roomLine(session: Session, phase: Phase, zone: string): string | null {
  if (phase === 'OPEN') return 'La sala está abierta. Empieza cuando entren los dos.';
  if (phase === 'BEFORE_OPEN' && session.joinOpensAt) {
    return `La sala se abre a las ${formatInZone(session.joinOpensAt, zone, 'time')}.`;
  }
  return null;
}

/** How an arrival reads next to each person. */
export function arrivalText(
  arrival: string | null,
  who: 'ME' | 'OTHER',
  session: Session,
  zone: string,
): { text: string; arrived: boolean } {
  if (arrival) {
    const time = formatInZone(arrival, zone, 'time');
    return { text: `${who === 'ME' ? 'Entraste' : 'Entró'} ${time}`, arrived: true };
  }
  return { text: session.status === 'ABANDONED' ? 'No entró' : 'Todavía no', arrived: false };
}

/** "07:12", or "1:07:12" past the hour: how long the session has been running. */
export function elapsedText(startedAt: string | null | undefined, now: number): string {
  if (!startedAt) return '00:00';
  const total = Math.max(0, Math.floor((now - new Date(startedAt).getTime()) / 1000));
  const hours = Math.floor(total / 3600);
  const minutes = String(Math.floor((total % 3600) / 60)).padStart(2, '0');
  const seconds = String(total % 60).padStart(2, '0');
  return hours > 0 ? `${hours}:${minutes}:${seconds}` : `${minutes}:${seconds}`;
}

function minutes(count: number): string {
  return count === 1 ? '1 minuto' : `${count} minutos`;
}

/** "Duró 58 minutos (de 16:02 a 17:00).", from when both were in to the close. */
export function durationLine(session: Session, zone: string): string {
  if (!session.startedAt || !session.endedAt) return '';
  const start = new Date(session.startedAt);
  const end = new Date(session.endedAt);
  const lasted = Math.max(0, Math.round((end.getTime() - start.getTime()) / 60_000));
  return `Duró ${minutes(lasted)} (de ${formatInZone(start, zone, 'time')} a ${formatInZone(end, zone, 'time')}).`;
}

/** What a session left unverified means for the person reading. */
export function unverifiedDetail(session: Session): string {
  return session.role === 'TUTOR'
    ? 'Alguien no confirmó su presencia a tiempo. No ganas esta hora y el estudiante recupera su crédito.'
    : 'Alguien no confirmó su presencia a tiempo. Tu crédito vuelve a ti y el tutor no gana la hora.';
}

/** "Esperamos a que Julián Arce también la termine. Si no, se cierra sola a las 17:15." */
export function waitingCloseLine(session: Session, zone: string): string {
  const closes = session.closesAt
    ? ` Si no, se cierra sola a las ${formatInZone(session.closesAt, zone, 'time')}.`
    : '';
  return `Esperamos a que ${otherName(session)} también la termine.${closes}`;
}
