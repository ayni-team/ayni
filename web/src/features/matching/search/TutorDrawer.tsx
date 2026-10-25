import type { Schema } from '@/shared/api';
import { formatInZone } from '@/shared/time';
import { Avatar, Badge, Button, IconClock, IconClose, IconInfo } from '@/shared/ui';
import { formatHourLabel, getOtherFreeHoursText } from './dateUtils';

type Offer = Schema<'Offer'>;

export interface TutorDrawerProps {
  day: string;
  hour: number;
  offers: readonly Offer[];
  allOffers: readonly Offer[];
  zone: string;
  onClose: () => void;
  onSelectOffer: (offer: Offer) => void;
}

export function TutorDrawer({
  day,
  hour,
  offers,
  allOffers,
  zone,
  onClose,
  onSelectOffer,
}: TutorDrawerProps) {
  const startHourStr = formatHourLabel(hour);
  const endHourStr = formatHourLabel(hour + 1);

  // Formatted day title: "Martes 27 de octubre"
  const sampleInstant = `${day}T12:00:00Z`;
  const dayTitle = formatInZone(sampleInstant, zone, { weekday: 'long', day: 'numeric', month: 'long' });
  const capitalizedDayTitle = dayTitle.charAt(0).toUpperCase() + dayTitle.slice(1);

  return (
    <aside
      aria-label="Detalle de tutores disponibles"
      className="flex flex-col rounded-xl border border-gray-200 bg-white p-5 shadow-sm w-full lg:w-96 shrink-0"
    >
      {/* Header */}
      <div className="flex items-start justify-between gap-3 border-b border-gray-100 pb-3">
        <div>
          <p className="text-xs font-semibold text-brand-text">{capitalizedDayTitle}</p>
          <h2 className="text-xl font-bold text-gray-900">
            {startHourStr} – {endHourStr}
          </h2>
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label="Cerrar detalle"
          className="rounded-lg p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600 focus:outline-none focus:ring-2 focus:ring-brand-primary"
        >
          <IconClose className="h-5 w-5" />
        </button>
      </div>

      {/* Info Callout */}
      <div className="my-3 flex items-start gap-2 rounded-lg bg-gray-50 p-2.5 text-xs text-gray-700">
        <IconInfo className="h-4 w-4 shrink-0 text-gray-500 mt-0.5" />
        <p>
          {offers.length} {offers.length === 1 ? 'tutor libre' : 'tutores libres'}. Ordenados por su
          calificación en este curso; los tutores nuevos van al final.
        </p>
      </div>

      {/* Tutors List */}
      <div className="flex flex-1 flex-col gap-3 overflow-y-auto pr-0.5">
        {offers.map((offer, index) => {
          const isFirst = index === 0;
          const otherHoursText = getOtherFreeHoursText(allOffers, offer, zone);
          const starsText =
            offer.averageStars !== null && offer.averageStars !== undefined
              ? `★ ${Number(offer.averageStars).toFixed(1)} · ${offer.ratingsCount ?? 0} ${
                  offer.ratingsCount === 1 ? 'calificación' : 'calificaciones'
                }`
              : `Sin promedio aún · ${offer.ratingsCount ?? 0} ${
                  offer.ratingsCount === 1 ? 'calificación' : 'calificaciones'
                }`;

          return (
            <div
              key={offer.blockId ?? `${offer.tutorId}-${offer.startsAt}`}
              className="rounded-xl border border-gray-200 bg-white p-3.5 shadow-xs hover:border-gray-300 transition-colors"
            >
              <div className="flex items-start gap-3">
                <Avatar name={offer.tutorName ?? ''} size="md" />
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <p className="truncate text-sm font-semibold text-gray-900">{offer.tutorName}</p>
                    {offer.newTutor && <Badge tone="info">Tutor nuevo</Badge>}
                  </div>
                  <p className="text-xs font-medium text-gray-700 mt-0.5">{starsText}</p>
                  {offer.sessionsTaught !== undefined && (
                    <p className="text-xs text-gray-500 mt-0.5">
                      {offer.sessionsTaught} {offer.sessionsTaught === 1 ? 'sesión' : 'sesiones'} de este curso
                    </p>
                  )}
                </div>
              </div>

              <div className="mt-3 flex items-center justify-between gap-2 border-t border-gray-100 pt-2.5">
                <div className="flex items-center gap-1 text-[11px] text-gray-500 min-w-0 truncate">
                  <IconClock className="h-3.5 w-3.5 shrink-0 text-gray-400" />
                  <span className="truncate">{otherHoursText}</span>
                </div>
                <Button
                  variant={isFirst ? 'primary' : 'secondary'}
                  size="sm"
                  onClick={() => onSelectOffer(offer)}
                  className="shrink-0"
                >
                  Elegir
                </Button>
              </div>
            </div>
          );
        })}
      </div>

      {/* Footer notice */}
      <p className="mt-4 border-t border-gray-100 pt-3 text-center text-[11px] text-gray-500">
        Al elegir, apartamos la hora 5 minutos para ti mientras escribes qué necesitas.
      </p>
    </aside>
  );
}
