import type { Schema } from '@/shared/api';
import { formatInZone, relativeTime } from '@/shared/time';

export type Notice = Schema<'Notice'>;
export type NoticeKind = NonNullable<Notice['kind']>;

/** Which drawing goes beside a notice. The components map each one to an icon. */
export type NoticeIcon = 'calendar' | 'check' | 'clock' | 'wallet' | 'alert' | 'cancel' | 'award' | 'shield' | 'bell';

/**
 * A notice as the person reads it: the web writes every text from `kind` and the payload, never from
 * the server (the guide's rule), so a notice the server sends in English still reads in Spanish.
 */
export interface NoticeView {
  title: string;
  /** The second line, when the payload says something worth it ("La canceló el tutor"). */
  detail?: string;
  /** The screen it is about; absent when the payload lacks what the link needs. */
  to?: string;
  icon: NoticeIcon;
  unread: boolean;
}

/** The text of each kind, from the table of FE-60 in the frontend guide. */
const TITLES: Partial<Record<NoticeKind, string>> = {
  BOOKING_CONFIRMED: 'Tu tutoría quedó confirmada',
  BOOKING_CANCELLED: 'Se canceló una tutoría',
  CREDITS_EXPIRING: 'Tienes créditos por vencer',
  SESSION_CLOSED: 'Tu sesión se cerró',
  TUTOR_NO_SHOW: 'Tu tutor no llegó: te devolvimos el crédito',
  PURCHASE_CONFIRMED: 'Tu compra se acreditó',
  REQUEST_RESOLVED: 'Se resolvió tu solicitud de reconocimiento',
  VALIDATION_RESOLVED: 'Revisamos la evidencia de tu habilidad',
};

const ICONS: Partial<Record<NoticeKind, NoticeIcon>> = {
  BOOKING_CONFIRMED: 'calendar',
  BOOKING_CANCELLED: 'cancel',
  CREDITS_EXPIRING: 'clock',
  SESSION_CLOSED: 'check',
  TUTOR_NO_SHOW: 'alert',
  PURCHASE_CONFIRMED: 'wallet',
  REQUEST_RESOLVED: 'award',
  VALIDATION_RESOLVED: 'shield',
};

/** A kind the table does not have (the server only lists these, but a new one must not break). */
const UNKNOWN_TITLE = 'Tienes un aviso nuevo';

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** "Martes 27 de octubre, 17:00" in the university's zone; nothing when the instant is unreadable. */
function dayAndTime(instant: string | undefined, zone: string): string | undefined {
  if (!instant) return undefined;
  try {
    const day = formatInZone(instant, zone, { weekday: 'long', day: 'numeric', month: 'long' });
    const time = formatInZone(instant, zone, 'time');
    return `${capitalize(day.replace(',', ''))}, ${time}`;
  } catch {
    return undefined;
  }
}

/** "31 de octubre" in the university's zone. */
function longDay(instant: string | undefined, zone: string): string | undefined {
  if (!instant) return undefined;
  try {
    return formatInZone(instant, zone, { day: 'numeric', month: 'long' });
  } catch {
    return undefined;
  }
}

function decision(approved: string | undefined): string | undefined {
  if (approved === 'true') return 'Aprobada';
  if (approved === 'false') return 'Rechazada';
  return undefined;
}

function detailOf(kind: NoticeKind | undefined, payload: Record<string, string>, zone: string): string | undefined {
  switch (kind) {
    case 'BOOKING_CONFIRMED':
      return dayAndTime(payload.startsAt, zone);
    case 'BOOKING_CANCELLED':
      if (payload.cancelledBy === 'TUTOR') return 'La canceló el tutor';
      if (payload.cancelledBy === 'STUDENT') return 'La canceló el estudiante';
      if (payload.cancelledBy === 'SYSTEM') return 'La sesión no se pudo verificar';
      return undefined;
    case 'CREDITS_EXPIRING': {
      const amount = Number(payload.amount);
      const day = longDay(payload.expiresAt, zone);
      if (!Number.isFinite(amount) || amount <= 0 || !day) return undefined;
      return `${amount} ${amount === 1 ? 'crédito vence' : 'créditos vencen'} el ${day}`;
    }
    case 'SESSION_CLOSED':
      if (payload.outcome === 'COMPLETED') return payload.role === 'STUDENT' ? 'Completada · Califícala' : 'Completada';
      if (payload.outcome === 'UNVERIFIED') return 'Sin verificar';
      return undefined;
    case 'REQUEST_RESOLVED':
    case 'VALIDATION_RESOLVED':
      return decision(payload.approved);
    default:
      return undefined;
  }
}

function linkOf(kind: NoticeKind | undefined, payload: Record<string, string>): string | undefined {
  switch (kind) {
    case 'BOOKING_CONFIRMED':
      return payload.role === 'TUTOR' ? '/tutor/agenda' : '/mis-tutorias';
    case 'BOOKING_CANCELLED':
      // The notice goes to the other party: when the student cancelled, it is the tutor's.
      return payload.cancelledBy === 'STUDENT' ? '/tutor/agenda' : '/mis-tutorias';
    case 'CREDITS_EXPIRING':
      return '/creditos';
    case 'PURCHASE_CONFIRMED':
      return payload.purchaseId ? `/creditos/compras/${payload.purchaseId}` : '/creditos/compras';
    case 'SESSION_CLOSED':
    case 'TUTOR_NO_SHOW':
      return payload.sessionId ? `/sesion/${payload.sessionId}` : undefined;
    case 'REQUEST_RESOLVED':
      return '/reconocimiento';
    case 'VALIDATION_RESOLVED':
      return '/tutor/habilidades';
    default:
      return undefined;
  }
}

export function noticeView(notice: Notice, zone: string): NoticeView {
  const payload = notice.payload ?? {};
  const kind = notice.kind;
  return {
    title: (kind && TITLES[kind]) ?? UNKNOWN_TITLE,
    detail: detailOf(kind, payload, zone),
    to: linkOf(kind, payload),
    icon: (kind && ICONS[kind]) ?? 'bell',
    unread: !notice.readAt,
  };
}

const DAY_MS = 86_400_000;
const RECENT_MS = 7 * DAY_MS;

/** "hace 2 días" in full, as the frame writes it; the shared format abbreviates days to "d". */
const daysAgo = new Intl.RelativeTimeFormat('es', { numeric: 'always', style: 'long' });

/**
 * When it arrived: "hace 5 min" or "hace 2 días" within a week, the day ("19 oct") before that, as
 * the frame draws it. `now` is an instant, never the day, so `Date.now()` is the right clock.
 */
export function noticeWhen(createdAt: string | undefined, zone: string, now: Date = new Date()): string {
  if (!createdAt) return '';
  try {
    const age = now.getTime() - new Date(createdAt).getTime();
    if (age < DAY_MS) return relativeTime(createdAt, now);
    if (age < RECENT_MS) return daysAgo.format(-Math.round(age / DAY_MS), 'day');
    return formatInZone(createdAt, zone, { day: 'numeric', month: 'short' }).replace('.', '');
  } catch {
    return '';
  }
}

/** "3 sin leer · los más nuevos primero", or the line for a tray with nothing unread. */
export function unreadSummary(unread: number | undefined): string {
  if (!unread) return 'Todo leído · los más nuevos primero';
  return `${unread} sin leer · los más nuevos primero`;
}

/** What the bell says to a screen reader, and the number it draws (99+ beyond that). */
export function bellLabel(unread: number | undefined): string {
  if (!unread) return 'Avisos, ninguno sin leer';
  return `Avisos, ${unread} sin leer`;
}

export function bellCount(unread: number | undefined): string | null {
  if (!unread) return null;
  return unread > 99 ? '99+' : String(unread);
}
