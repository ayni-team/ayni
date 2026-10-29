import type { Schema } from '@/shared/api';
import { dayInZone, formatInZone, todayInZone } from '@/shared/time';
import { Badge, Button } from '@/shared/ui';
import { cx } from '@/shared/ui/cx';

export interface TutoringCardProps {
  booking: Schema<'StudentBooking'>;
  view: 'UPCOMING' | 'HISTORY';
  timezone: string;
  onJoin: (booking: Schema<'StudentBooking'>) => void;
  onCancel: (booking: Schema<'StudentBooking'>) => void;
  onReview: (booking: Schema<'StudentBooking'>) => void;
  isJoining?: boolean;
  isCancelling?: boolean;
  isReviewing?: boolean;
}

/**
 * Tile showing the 3-letter weekday, 2-digit day number, and 3-letter month.
 * E.g.: VIE 23 OCT, LUN 02 NOV.
 */
function DateTile({ date, timezone }: { date: string; timezone: string }) {
  const weekday = formatInZone(date, timezone, { weekday: 'short' }).replace('.', '').toUpperCase();
  const day = formatInZone(date, timezone, { day: '2-digit' });
  const month = formatInZone(date, timezone, { month: 'short' }).replace('.', '').toUpperCase();

  return (
    <div
      className="flex h-16 w-14 sm:h-16 sm:w-16 flex-col items-center justify-center rounded-xl bg-slate-100/70 border border-slate-200/60 flex-shrink-0 text-center select-none"
      aria-hidden="true"
    >
      <span className="text-[10px] sm:text-[11px] font-semibold text-slate-500 tracking-wider">{weekday}</span>
      <span className="text-xl sm:text-2xl font-bold text-slate-800 leading-none my-0.5">{day}</span>
      <span className="text-[10px] sm:text-[11px] font-semibold text-slate-500 tracking-wider">{month}</span>
    </div>
  );
}

/**
 * Returns relative starts in text ("empieza en 4 h") if the booking starts soon today or within 12 hours.
 */
function getStartsInText(startsAt: string | undefined, timezone: string, joinable?: boolean): string | null {
  if (!startsAt || joinable) return null;
  const target = new Date(startsAt).getTime();
  const now = Date.now();
  const diffMs = target - now;
  if (diffMs <= 0) return null;

  const isToday = dayInZone(startsAt, timezone) === todayInZone(timezone);
  const isWithin12Hours = diffMs <= 12 * 3600 * 1000;

  if (isToday || isWithin12Hours) {
    const hours = Math.round(diffMs / 3600000);
    if (hours < 1) {
      const minutes = Math.max(1, Math.round(diffMs / 60000));
      return `empieza en ${minutes} min`;
    }
    return `empieza en ${hours} h`;
  }
  return null;
}

/**
 * Single booking card for upcoming and history views.
 * Highlights joinable sessions with a red brand border and primary "Entrar" button.
 */
export function TutoringCard({
  booking,
  view,
  timezone,
  onJoin,
  onCancel,
  onReview,
  isJoining = false,
  isCancelling = false,
  isReviewing = false,
}: TutoringCardProps) {
  const isJoinable = Boolean(booking.joinable);
  const courseName = booking.catalogItem?.name ?? 'Tutoría';
  const tutorName = booking.tutor?.name ? `con ${booking.tutor.name}` : '';

  const startTime = booking.startsAt ? formatInZone(booking.startsAt, timezone, 'time') : '';
  const endTime = booking.endsAt ? formatInZone(booking.endsAt, timezone, 'time') : '';
  const hours = booking.hours ?? 1;
  const durationLabel = hours === 1 ? '1 hora' : `${hours} horas`;
  const timeAndDuration = startTime && endTime ? `${startTime} – ${endTime} · ${durationLabel}` : durationLabel;

  const startsInText = view === 'UPCOMING' ? getStartsInText(booking.startsAt, timezone, isJoinable) : null;

  // Status badge logic
  const renderStatusBadges = () => {
    if (view === 'UPCOMING') {
      return (
        <>
          <Badge tone="info">Confirmada</Badge>
          {isJoinable && <Badge tone="danger">En curso</Badge>}
        </>
      );
    }

    switch (booking.status) {
      case 'COMPLETED':
        return <Badge tone="neutral">Completada</Badge>;
      case 'NO_SHOW':
        return <Badge tone="warning">No asistió</Badge>;
      case 'CANCELLED':
        return <Badge tone="danger">Cancelada</Badge>;
      case 'CONFIRMED':
      default:
        return <Badge tone="info">Confirmada</Badge>;
    }
  };

  // Action button logic
  const renderActionButton = () => {
    if (view === 'UPCOMING') {
      if (isJoinable) {
        return (
          <Button variant="primary" size="md" onClick={() => onJoin(booking)} loading={isJoining}>
            Entrar
          </Button>
        );
      }
      return (
        <Button variant="danger" size="md" onClick={() => onCancel(booking)} loading={isCancelling}>
          Cancelar
        </Button>
      );
    }

    if (booking.status === 'COMPLETED') {
      return (
        <Button
          variant="ghost"
          size="md"
          onClick={() => onReview(booking)}
          loading={isReviewing}
          className="font-semibold text-brand-text hover:underline"
        >
          Repasar
        </Button>
      );
    }

    return null;
  };

  return (
    <article
      aria-label={`${courseName} ${tutorName}`}
      className={cx(
        'rounded-2xl bg-white p-4 sm:p-5 transition-shadow shadow-sm',
        isJoinable ? 'border-2 border-brand' : 'border border-gray-200 hover:border-gray-300',
      )}
    >
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        {/* Left column: Date Tile + Course / Time / Tutor */}
        <div className="flex items-start sm:items-center gap-3.5 sm:gap-4 min-w-0">
          {booking.startsAt && <DateTile date={booking.startsAt} timezone={timezone} />}

          <div className="min-w-0 flex-1">
            <h2 className="text-base font-bold text-gray-900 truncate">{courseName}</h2>
            <p className="mt-0.5 text-sm text-gray-600">{timeAndDuration}</p>
            {tutorName && <p className="mt-0.5 text-sm text-gray-500">{tutorName}</p>}
          </div>
        </div>

        {/* Right column: Badges, relative time and Action button */}
        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-2.5 sm:gap-3 flex-shrink-0 pl-16 sm:pl-0">
          <div className="flex flex-wrap items-center gap-2">
            {renderStatusBadges()}
            {startsInText && <span className="text-xs sm:text-sm text-gray-500">{startsInText}</span>}
          </div>
          <div>{renderActionButton()}</div>
        </div>
      </div>
    </article>
  );
}
