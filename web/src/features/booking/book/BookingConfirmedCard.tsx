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
  tutorName = 'El tutor',
  courseName = 'Curso',
  courseCode,
  startsAt,
  endsAt,
  creditsCharged = 1,
  zone,
}: BookingConfirmedCardProps) {
  const dayText = startsAt ? capitalize(formatInZone(startsAt, zone, 'longDate')) : '';
  const startTime = startsAt ? formatInZone(startsAt, zone, 'time') : '';
  const endTime = endsAt ? formatInZone(endsAt, zone, 'time') : '';
  const timeRange = startTime && endTime ? `${startTime} – ${endTime}` : '';

  return (
    <div className="mx-auto max-w-xl rounded-2xl border border-gray-200 bg-white p-6 text-center shadow-sm sm:p-8">
      <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-emerald-100 text-emerald-600">
        <IconCheck className="h-8 w-8 stroke-[2.5]" />
      </div>

      <h1 className="text-2xl font-bold text-gray-900">Reserva confirmada</h1>
      <p className="mt-1 text-base text-gray-600">Listo, {tutorName} te espera</p>

      <div className="my-6 rounded-xl border border-gray-100 bg-gray-50/90 p-4 text-center">
        <p className="font-semibold text-gray-900">
          {dayText}
          {timeRange ? ` · ${timeRange}` : ''}
        </p>
        <p className="mt-1 text-sm text-gray-600">
          {courseName} {courseCode ? `(${courseCode})` : ''}
        </p>
      </div>

      <p className="text-sm text-gray-500">
        Se descontó {creditsCharged} {creditsCharged === 1 ? 'crédito' : 'créditos'}. La sala se
        abre 15 minutos antes.
      </p>

      <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:justify-center">
        <ButtonLink to="/mis-tutorias" variant="primary">
          Ver mis tutorías
        </ButtonLink>
        <ButtonLink to="/buscar" variant="secondary">
          Buscar otra
        </ButtonLink>
      </div>
    </div>
  );
}
