import { Avatar, Badge, IconCalendar, IconClock } from '@/shared/ui';
import { formatInZone } from '@/shared/time';

export interface BookingSummaryCardProps {
  courseName?: string;
  courseCode?: string | null;
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
  tutorName = 'Tutor',
  averageStars,
  ratingsCount,
  sessionsTaught,
  newTutor = false,
  startsAt,
  endsAt,
  hours = 1,
  zone,
  availableBalance,
}: BookingSummaryCardProps) {
  const dayText = startsAt ? capitalize(formatInZone(startsAt, zone, 'longDate')) : '';
  const startTime = startsAt ? formatInZone(startsAt, zone, 'time') : '';
  const endTime = endsAt ? formatInZone(endsAt, zone, 'time') : '';

  const totalReviews = ratingsCount ?? sessionsTaught ?? 0;

  return (
    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm">
      <div className="space-y-4 p-5 sm:p-6">
        {/* Course info */}
        <div>
          <h2 className="text-lg font-bold text-gray-900">{courseName}</h2>
          {courseCode && <span className="font-mono text-xs text-gray-500">{courseCode}</span>}
        </div>

        <hr className="border-gray-100" />

        {/* Tutor info */}
        <div className="flex items-center gap-3">
          <Avatar name={tutorName} size="md" />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-gray-900">{tutorName}</p>
            <div className="mt-0.5 flex items-center gap-1.5 text-xs text-gray-600">
              {newTutor ? (
                <Badge tone="neutral">Nuevo tutor</Badge>
              ) : averageStars !== undefined && averageStars !== null ? (
                <span className="flex items-center gap-1">
                  <span className="text-amber-500">★</span>
                  <span className="font-medium text-gray-800">{averageStars.toFixed(1)}</span>
                  <span className="text-gray-400">·</span>
                  <span>
                    {totalReviews} {totalReviews === 1 ? 'tutoría' : 'tutorías'}
                  </span>
                </span>
              ) : (
                <span>Tutor de la universidad</span>
              )}
            </div>
          </div>
        </div>

        <hr className="border-gray-100" />

        {/* Date and time */}
        <div className="space-y-2 text-sm text-gray-700">
          {startsAt && (
            <div className="flex items-center gap-2.5">
              <IconCalendar className="h-4 w-4 shrink-0 text-gray-400" />
              <span>{dayText}</span>
            </div>
          )}
          {startsAt && endsAt && (
            <div className="flex items-center gap-2.5">
              <IconClock className="h-4 w-4 shrink-0 text-gray-400" />
              <span>
                {startTime} – {endTime} ({hours} {hours === 1 ? 'hora' : 'horas'})
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Cost and Balance strip */}
      <div className="flex items-center justify-between border-t border-gray-100 bg-gray-50/90 px-5 py-3.5 text-xs font-medium text-gray-700 sm:text-sm">
        <span>
          Costo {hours} {hours === 1 ? 'crédito' : 'créditos'}
        </span>
        <span className="text-gray-600">
          Tu saldo:{' '}
          <strong className="font-semibold text-gray-900">
            {availableBalance !== undefined
              ? `${availableBalance} ${availableBalance === 1 ? 'crédito' : 'créditos'}`
              : '...'}
          </strong>
        </span>
      </div>
    </div>
  );
}
