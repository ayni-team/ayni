import type { Schema } from '@/shared/api';
import { formatInZone } from '@/shared/time';
import type { BadgeTone } from '@/shared/ui';

export type Review = Schema<'SessionReview'>;

/** The state and what happened, in the web's own Spanish: `statusNote` is English and is not shown. */
const STATES: Record<NonNullable<Review['status']>, { label: string; tone: BadgeTone; note: string }> = {
  SCHEDULED: { label: 'Programada', tone: 'neutral', note: 'La sesión todavía no empieza.' },
  IN_PROGRESS: { label: 'En curso', tone: 'brand', note: 'La sesión está en curso.' },
  COMPLETED: { label: 'Terminada', tone: 'info', note: 'La sesión se realizó y los dos confirmaron su presencia.' },
  UNVERIFIED: {
    label: 'Sin verificar',
    tone: 'warning',
    note: 'La sesión se realizó, pero alguien no confirmó su presencia.',
  },
  ABANDONED: {
    label: 'Abandonada',
    tone: 'warning',
    note: 'La sesión no empezó: alguien no había entrado a los diez minutos.',
  },
  CANCELLED: { label: 'Cancelada', tone: 'neutral', note: 'La tutoría se canceló antes de empezar.' },
};

export function stateOf(status: Review['status']): { label: string; tone: BadgeTone; note: string } {
  return (
    (status && STATES[status]) || { label: 'Sin estado', tone: 'neutral', note: 'No sabemos qué pasó con esta sesión.' }
  );
}

/** Whether the session never took place: then the review says why, and there is no workspace to show. */
export function didNotHappen(review: Review): boolean {
  return review.status === 'CANCELLED' || review.status === 'ABANDONED';
}

/** A session still ahead or running has its room, and is not reviewed yet. */
export function isOpen(review: Review): boolean {
  return review.status === 'SCHEDULED' || review.status === 'IN_PROGRESS';
}

function minutes(count: number): string {
  return count === 1 ? '1 minuto' : `${count} minutos`;
}

/** "Duró 61 minutos de los 60 reservados". */
export function durationText(review: Review): string | null {
  if (review.realDurationMinutes === null || review.realDurationMinutes === undefined) return null;
  const real = minutes(review.realDurationMinutes);
  return review.bookedMinutes ? `Duró ${real} de los ${review.bookedMinutes} reservados` : `Duró ${real}`;
}

/** "Viernes 23 de octubre · 16:00 – 17:00 · con Julián Arce". */
export function whenLine(review: Review, zone: string): string {
  const parts: string[] = [];
  if (review.scheduledStart) {
    const day = formatInZone(review.scheduledStart, zone, 'longDate').replace(',', '');
    parts.push(day.charAt(0).toUpperCase() + day.slice(1));
  }
  if (review.scheduledStart && review.scheduledEnd) {
    parts.push(
      `${formatInZone(review.scheduledStart, zone, 'time')} – ${formatInZone(review.scheduledEnd, zone, 'time')}`,
    );
  }
  if (review.counterpart?.name) parts.push(`con ${review.counterpart.name}`);
  return parts.join(' · ');
}

/** Who did not come, seen from whoever reads: the review names the other person and not the reader. */
export function absenceText(review: Review): string | null {
  const absent = review.absentParticipantIds ?? [];
  if (absent.length === 0) return null;
  if (absent.length > 1) return 'Ninguno de los dos entró a tiempo.';
  const other = review.counterpart;
  if (other?.id && absent.includes(other.id)) return `${other.name ?? 'La otra persona'} no entró a tiempo.`;
  return 'No entraste a tiempo.';
}
