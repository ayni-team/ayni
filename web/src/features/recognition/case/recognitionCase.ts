import type { Schema } from '@/shared/api';
import { formatInZone } from '@/shared/time';

export type RecognitionCase = Schema<'RecognitionCase'>;
export type CaseSession = Schema<'RecognitionCaseSession'>;
export type CaseAlert = Schema<'RecognitionCaseAlert'>;
export type SessionEvidence = Schema<'RecognitionSessionEvidence'>;
export type Decision = NonNullable<Schema<'RecognitionDecisionRequest'>['decision']>;

/** The guide's limit for the reason (the server takes up to 1000; the contract asks for 500). */
export const REASON_MAX = 500;

/** What is wrong with the reason, in the coordinator's words, or nothing when it can be sent. */
export function reasonError(reason: string): string | undefined {
  const text = reason.trim();
  if (!text) return 'Escribe el motivo de tu decisión';
  if (text.length > REASON_MAX) return `El motivo puede tener hasta ${REASON_MAX} caracteres`;
  return undefined;
}

/** A request that was approved or rejected cannot be decided again. */
export function isDecided(status: RecognitionCase['status']): boolean {
  return status === 'APPROVED' || status === 'REJECTED';
}

function safely(format: () => string): string {
  try {
    return format();
  } catch {
    return '';
  }
}

function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/** "Lun 20 oct", as the frame dates a session. */
export function shortDay(instant: string | undefined, zone: string): string {
  if (!instant) return '';
  return safely(() =>
    capitalize(
      formatInZone(instant, zone, { weekday: 'short', day: 'numeric', month: 'short' })
        .replace(/\./g, '')
        .replace(',', ''),
    ),
  );
}

/** "18:00 – 18:22" in the university's zone. */
export function timeRange(from: string | undefined, to: string | undefined, zone: string): string {
  if (!from || !to) return '';
  return safely(() => `${formatInZone(from, zone, 'time')} – ${formatInZone(to, zone, 'time')}`);
}

/** "20 oct 2026", the day the request was sent. */
export function longDay(instant: string | undefined, zone: string): string {
  if (!instant) return '';
  return safely(() =>
    formatInZone(instant, zone, { day: 'numeric', month: 'short', year: 'numeric' }).replace('.', ''),
  );
}

/** "22 oct 2026, 11:40", the moment of a decision. */
export function decidedAt(instant: string | undefined | null, zone: string): string {
  if (!instant) return '';
  return safely(() =>
    formatInZone(instant, zone, {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    }).replace('.', ''),
  );
}

function hours(count: number | undefined): string {
  const value = count ?? 0;
  return `${value} ${value === 1 ? 'hora' : 'horas'}`;
}

function stars(value: number | null | undefined, none: string): string {
  return value === null || value === undefined ? none : `★ ${value}`;
}

/** "58 min · 1 hora · ★ 5" or "59 min · 1 hora · sin calificación". */
export function sessionFigures(session: CaseSession): string {
  return [`${session.durationMinutes ?? 0} min`, hours(session.hours), stars(session.stars, 'sin calificación')].join(
    ' · ',
  );
}

/** "18:00 – 18:22 · 22 min · 1 hora", what really happened. */
export function realFigures(evidence: SessionEvidence, zone: string): string {
  return [
    timeRange(evidence.startedAt, evidence.endedAt, zone),
    `${evidence.durationMinutes ?? 0} min`,
    hours(evidence.hours),
  ]
    .filter(Boolean)
    .join(' · ');
}

/** "Algoritmos · CC215", what was taught. */
export function taughtText(evidence: SessionEvidence): string {
  const taught = evidence.taught;
  if (!taught?.name) return 'Sin dato';
  return taught.courseCode ? `${taught.name} · ${taught.courseCode}` : taught.name;
}

export function studentRating(evidence: SessionEvidence): string {
  return stars(evidence.tutorStars, 'Sin calificación');
}

/**
 * The kinds of alert the audit publishes (`AnomalyKind`), each with the name and the explanation the
 * web writes: the server's `description` is English and, as every text of the server, never shown.
 */
const ALERT_KINDS: Record<string, { title: string; text: string }> = {
  REPEATED_PAIR: {
    title: 'Pareja repetida',
    text: 'El mismo tutor y el mismo estudiante completaron más sesiones de lo habitual.',
  },
  UNUSUAL_FREQUENCY: { title: 'Frecuencia inusual', text: 'Hubo más sesiones de lo habitual en poco tiempo.' },
  SHORT_DURATION: { title: 'Duración corta', text: 'La sesión duró una pequeña parte de lo reservado.' },
  FAILED_PRESENCE: { title: 'Presencia sin confirmar', text: 'Alguien no confirmó que estaba en la sesión.' },
  NO_BOARD_ACTIVITY: { title: 'Pizarra sin uso', text: 'La sesión terminó sin nada dibujado en la pizarra.' },
};

const UNKNOWN_ALERT = { title: 'Alerta de auditoría', text: 'Revisa esta sesión con atención.' };

function alertKind(alert: CaseAlert): { title: string; text: string } {
  return (alert.kind ? ALERT_KINDS[alert.kind] : undefined) ?? UNKNOWN_ALERT;
}

const SEVERITIES: Record<string, string> = { LOW: 'baja', MEDIUM: 'media', HIGH: 'alta' };

/** "Duración corta · gravedad media". */
export function alertTitle(alert: CaseAlert): string {
  const kind = alertKind(alert).title;
  const severity = alert.severity && SEVERITIES[alert.severity];
  return severity ? `${kind} · gravedad ${severity}` : kind;
}

/** What the alert means, in Spanish. */
export function alertText(alert: CaseAlert): string {
  return alertKind(alert).text;
}

/** The session the evidence opens on: the first one with an alert, since that is what to look at. */
export function firstToReview(sessions: CaseSession[]): string | undefined {
  return (sessions.find((session) => (session.alerts?.length ?? 0) > 0) ?? sessions[0])?.sessionId;
}

/** The verb of each button and of its confirmation. */
export const DECISIONS: Record<Decision, { verb: string; doing: string; done: string }> = {
  APPROVE: { verb: 'Aprobar', doing: 'Aprobando…', done: 'Aprobaste la solicitud' },
  REJECT: { verb: 'Rechazar', doing: 'Rechazando…', done: 'Rechazaste la solicitud' },
};
