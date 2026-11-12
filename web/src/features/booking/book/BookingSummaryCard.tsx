import { CreditCoin, IconCalendar, initialsOf } from '@/shared/ui';
import { formatInZone } from '@/shared/time';

export interface BookingSummaryCardProps {
  courseName?: string;
  courseCode?: string | null;
  universityCode?: string;
  tutorName?: string;
  averageStars?: number | null;
  ratingsCount?: number | null;
  sessionsTaught?: number | null;
  newTutor?: boolean;
  startsAt?: string | null;
  endsAt?: string | null;
  hours?: number;
  zone: string;
  availableBalance?: number;
}

function capitalize(str: string) {
  if (!str) return '';
  return str.charAt(0).toUpperCase() + str.slice(1);
}

export function BookingSummaryCard({
  courseName = 'Curso de tutoría',
  courseCode,
  universityCode,
  tutorName = 'Tu tutor',
  averageStars,
  ratingsCount,
  sessionsTaught,
  newTutor,
  startsAt,
  endsAt,
  hours = 1,
  zone,
  availableBalance,
}: BookingSummaryCardProps) {
  const dayText = startsAt ? capitalize(formatInZone(startsAt, zone, 'longDate')).replace(',', '') : '';
  const startTime = startsAt ? formatInZone(startsAt, zone, 'time') : '';
  const effectiveEnd =
    endsAt ?? (startsAt ? new Date(new Date(startsAt).getTime() + hours * 3600000).toISOString() : '');
  const endTime = effectiveEnd ? formatInZone(effectiveEnd, zone, 'time') : '';
  const hourLabel = `${hours} ${hours === 1 ? 'hora' : 'horas'}`;
  const dayTimeText = startsAt ? `${dayText} · ${startTime} – ${endTime} · ${hourLabel} (hora de Lima)` : '';

  // Tutor ratings display matching Figma
  const stars =
    averageStars !== undefined && averageStars !== null && averageStars > 0 ? averageStars.toFixed(1) : null;
  const ratings = ratingsCount ?? 0;
  const sessionsText =
    sessionsTaught !== undefined && sessionsTaught !== null ? `${sessionsTaught} sesiones de este curso` : null;

  const codeSubtitle = [courseCode, universityCode ? `curso ${universityCode}` : null].filter(Boolean).join(' · ');

  return (
    <div className="overflow-hidden rounded-[20px] border border-line bg-white p-6 space-y-4">
      {/* Course Info */}
      <div>
        <h2 className="font-display text-[22px] font-semibold text-ink">{courseName}</h2>
        {codeSubtitle && <p className="text-xs text-ink-2">{codeSubtitle}</p>}
      </div>

      {/* Tutor Info */}
      <div className="flex items-center gap-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-sky-100 text-xs font-semibold text-sky-700">
          {initialsOf(tutorName)}
        </div>
        <div className="min-w-0">
          <p className="text-sm font-semibold text-ink">{tutorName}</p>
          <p className="text-xs text-ink-2">
            {stars ? (
              <span>
                ★ {stars} · {ratings} calificaciones{sessionsText ? ` · ${sessionsText}` : ''}
              </span>
            ) : newTutor ? (
              <span>Nuevo tutor{sessionsText ? ` · ${sessionsText}` : ''}</span>
            ) : (
              sessionsText && <span>{sessionsText}</span>
            )}
          </p>
        </div>
      </div>

      {/* Date & Time */}
      {dayTimeText && (
        <div className="flex items-center text-xs font-medium text-ink-2">
          <IconCalendar className="mr-2 h-4 w-4 shrink-0 text-ink-2" />
          <span>{dayTimeText}</span>
        </div>
      )}

      {/* Cost & Balance gray strip */}
      <div className="flex items-center justify-between rounded-xl bg-surface px-4 py-3 text-xs text-ink-2">
        <div className="flex items-center gap-1.5">
          <span>Costo</span>
          <CreditCoin className="h-4 w-4" />
          <strong className="font-semibold text-ink">
            {hours} {hours === 1 ? 'crédito' : 'créditos'}
          </strong>
        </div>
        <span>
          Tu saldo:{' '}
          <strong className="font-semibold text-ink">
            {availableBalance !== undefined
              ? `${availableBalance} ${availableBalance === 1 ? 'crédito' : 'créditos'}`
              : '—'}
          </strong>
        </span>
      </div>
    </div>
  );
}
