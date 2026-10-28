import { Link } from 'react-router-dom';
import { ButtonLink, IconCheck } from '@/shared/ui';
import { formatInZone } from '@/shared/time';

export interface BookingConfirmedCardProps {
  tutorName?: string;
  courseName?: string;
  courseCode?: string | null;
  startsAt?: string | null;
  endsAt?: string | null;
  hours?: number;
  creditsCharged?: number;
  zone: string;
}

function capitalize(str: string) {
  if (!str) return '';
  return str.charAt(0).toUpperCase() + str.slice(1);
}

export function BookingConfirmedCard({
  tutorName = 'tu tutor',
  courseName = 'Curso',
  startsAt,
  endsAt,
  hours = 1,
  creditsCharged = 1,
  zone,
}: BookingConfirmedCardProps) {
  const dayText = startsAt ? capitalize(formatInZone(startsAt, zone, 'longDate')).replace(',', '') : '';
  const startTime = startsAt ? formatInZone(startsAt, zone, 'time') : '';
  const effectiveEnd =
    endsAt ?? (startsAt ? new Date(new Date(startsAt).getTime() + hours * 3600000).toISOString() : '');
  const endTime = effectiveEnd ? formatInZone(effectiveEnd, zone, 'time') : '';
  const timeRange = startTime && endTime ? `${startTime} – ${endTime}` : '';

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <div>
        <Link
          to="/buscar"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-brand hover:underline"
        >
          <span>← Volver a Buscar</span>
        </Link>
      </div>

      <h1 className="text-2xl font-bold text-gray-900">Reserva confirmada</h1>

      <div className="rounded-2xl border border-gray-100 bg-white p-6 sm:p-8 shadow-sm space-y-3">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-sky-100 text-sky-600">
          <IconCheck className="h-6 w-6 stroke-[2.5]" />
        </div>

        <div>
          <h2 className="text-lg font-bold text-gray-900">Listo, {tutorName} te espera</h2>
          <p className="mt-1 text-xs text-gray-500">
            {dayText}
            {timeRange ? ` · ${timeRange}` : ''} · {courseName}
          </p>
          <p className="mt-1 text-xs text-gray-500">
            Se descontó {creditsCharged} {creditsCharged === 1 ? 'crédito' : 'créditos'}. La sala se abre 15 minutos
            antes.
          </p>
        </div>

        <div className="flex items-center gap-5 pt-3">
          <ButtonLink to="/mis-tutorias" variant="primary" className="rounded-xl px-5 py-2.5">
            Ver mis tutorías
          </ButtonLink>
          <Link to="/buscar" className="text-sm font-medium text-brand hover:underline">
            Buscar otra
          </Link>
        </div>
      </div>
    </div>
  );
}
