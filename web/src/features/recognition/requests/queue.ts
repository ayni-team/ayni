import type { Schema } from '@/shared/api';
import { formatInZone } from '@/shared/time';
import type { BadgeTone } from '@/shared/ui';

export type QueueItem = Schema<'RecognitionQueueItem'>;
export type RequestStatus = NonNullable<QueueItem['status']>;

/** The tabs of the frame: what waits, and the history of each decision. */
export type QueueTab = 'PENDING' | 'APPROVED' | 'REJECTED';

export const QUEUE_TABS: readonly { value: QueueTab; label: string }[] = [
  { value: 'PENDING', label: 'Por revisar' },
  { value: 'APPROVED', label: 'Aprobadas' },
  { value: 'REJECTED', label: 'Rechazadas' },
];

/**
 * The `status` the endpoint takes for a tab. "Por revisar" sends none: without it the server answers
 * what waits (submitted and under review), the oldest first.
 */
export function statusOf(tab: QueueTab): RequestStatus | undefined {
  return tab === 'PENDING' ? undefined : tab;
}

const STATUS: Record<RequestStatus, { label: string; tone: BadgeTone }> = {
  SUBMITTED: { label: 'Enviada', tone: 'neutral' },
  UNDER_REVIEW: { label: 'En revisión', tone: 'warning' },
  APPROVED: { label: 'Aprobada', tone: 'info' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
};

export function statusBadge(status: RequestStatus | undefined): { label: string; tone: BadgeTone } {
  return (status && STATUS[status]) ?? { label: 'Sin estado', tone: 'neutral' };
}

const RATING = new Intl.NumberFormat('es-PE', { minimumFractionDigits: 1, maximumFractionDigits: 1 });

/** "★ 4.8", or "Sin calificaciones" when the sessions had no rating (today, always: see the field map). */
export function ratingText(average: number | null | undefined): string {
  return average === null || average === undefined ? 'Sin calificaciones' : `★ ${RATING.format(average)}`;
}

/** "20 oct 2026", the day it was sent, in the university's zone. */
export function sentDay(submittedAt: string | undefined, zone: string): string {
  if (!submittedAt) return '';
  try {
    return formatInZone(submittedAt, zone, { day: 'numeric', month: 'short', year: 'numeric' }).replace('.', '');
  } catch {
    return '';
  }
}

function plural(count: number, one: string, many: string): string {
  return `${count} ${count === 1 ? one : many}`;
}

/** The second line of a request on a phone: "42 horas · 38 sesiones · ★ 4.8". */
export function figuresLine(item: QueueItem): string {
  return [
    plural(item.totalHours ?? 0, 'hora', 'horas'),
    plural(item.sessionsCount ?? 0, 'sesión', 'sesiones'),
    ratingText(item.averageRating),
  ].join(' · ');
}

/** What the tray of each tab says when it has nothing. */
export const EMPTY: Record<QueueTab, { title: string; description: string }> = {
  PENDING: {
    title: 'No hay solicitudes por revisar',
    description: 'Cuando un estudiante pida reconocimiento, aparecerá aquí.',
  },
  APPROVED: {
    title: 'Todavía no hay solicitudes aprobadas',
    description: 'Las que apruebes quedarán aquí, con su motivo.',
  },
  REJECTED: {
    title: 'No hay solicitudes rechazadas',
    description: 'Las que rechaces quedarán aquí, con su motivo.',
  },
};
