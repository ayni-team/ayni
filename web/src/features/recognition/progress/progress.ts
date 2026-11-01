import { errorBody, isApiError, type Schema } from '@/shared/api';
import { formatInZone } from '@/shared/time';
import type { BadgeTone } from '@/shared/ui';

export type Progress = Schema<'RecognitionProgress'>;
export type MyRequest = Schema<'RecognitionRequest'>;
export type MyRequestSession = Schema<'RecognitionRequestSession'>;

function hours(count: number): string {
  return `${count} ${count === 1 ? 'hora' : 'horas'}`;
}

/** Whether the university opened recognition: without a rule there is no requirement to show. */
export function isOpen(progress: Progress | undefined): boolean {
  return progress?.requiredHours !== null && progress?.requiredHours !== undefined;
}

/** How full the bar is, from 0 to 100, never past the end. */
export function percent(progress: Progress | undefined): number {
  const required = progress?.requiredHours ?? 0;
  if (required <= 0) return 0;
  return Math.min(100, Math.round(((progress?.earnedHours ?? 0) / required) * 100));
}

/** "14 de 20 horas". */
export function figures(progress: Progress | undefined): string {
  const earned = progress?.earnedHours ?? 0;
  if (!isOpen(progress)) return `${hours(earned)} enseñadas`;
  return `${earned} de ${hours(progress?.requiredHours ?? 0)}`;
}

/** The line under the bar: what is missing, or that the student got there. */
export function missingText(progress: Progress | undefined): string {
  if (!isOpen(progress)) return 'Tu universidad todavía no habilitó el reconocimiento';
  if (progress?.requirementMet)
    return 'Llegaste a las horas que pide tu universidad. Ya puedes solicitar el reconocimiento.';
  return `Te faltan ${hours(progress?.missingHours ?? 0)}`;
}

/**
 * What to say when a request is refused. A 409 with `missingHours` is the student short of hours
 * (the figure may have changed since the page loaded); one without it is a university that has not
 * opened recognition.
 */
export function refusalText(error: unknown): string | undefined {
  if (!isApiError(error, 409)) return undefined;
  const missing = errorBody<Schema<'InsufficientHoursError'>>(error)?.missingHours;
  if (missing !== undefined && missing !== null) return `Te faltan ${hours(missing)}`;
  return 'Tu universidad todavía no habilitó el reconocimiento';
}

const STATUS: Record<NonNullable<MyRequest['status']>, { label: string; tone: BadgeTone }> = {
  SUBMITTED: { label: 'Enviada', tone: 'neutral' },
  UNDER_REVIEW: { label: 'En revisión', tone: 'warning' },
  APPROVED: { label: 'Aprobada', tone: 'info' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
};

export function requestStatus(status: MyRequest['status']): { label: string; tone: BadgeTone } {
  return (status && STATUS[status]) ?? { label: 'Sin estado', tone: 'neutral' };
}

function safely(format: () => string): string {
  try {
    return format();
  } catch {
    return '';
  }
}

/** "20 oct 2026", in the university's zone. */
export function dayOf(instant: string | null | undefined, zone: string): string {
  if (!instant) return '';
  return safely(() =>
    formatInZone(instant, zone, { day: 'numeric', month: 'short', year: 'numeric' }).replace('.', ''),
  );
}

/** "Enviada el 20 oct 2026 · 20 horas · 12 sesiones". */
export function requestLine(request: MyRequest, zone: string): string {
  const sessions = request.sessionsCount ?? 0;
  return [
    `Enviada el ${dayOf(request.submittedAt, zone)}`,
    hours(request.totalHours ?? 0),
    `${sessions} ${sessions === 1 ? 'sesión' : 'sesiones'}`,
  ].join(' · ');
}

/** "Lun 5 oct · 07:55 – 09:57 · 2 horas", a session that backs a request. */
export function sessionLine(session: MyRequestSession, zone: string): string {
  const day = safely(() =>
    formatInZone(session.startedAt ?? '', zone, { weekday: 'short', day: 'numeric', month: 'short' })
      .replace(/\./g, '')
      .replace(',', ''),
  );
  const range =
    session.startedAt && session.endedAt
      ? safely(
          () => `${formatInZone(session.startedAt!, zone, 'time')} – ${formatInZone(session.endedAt!, zone, 'time')}`,
        )
      : '';
  return [day && day.charAt(0).toUpperCase() + day.slice(1), range, hours(session.hours ?? 0)]
    .filter(Boolean)
    .join(' · ');
}
