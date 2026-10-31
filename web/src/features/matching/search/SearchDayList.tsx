import type { Schema } from '@/shared/api';
import { dayInZone, formatInZone } from '@/shared/time';
import { Avatar, Badge, Button, IconInfo, Pagination } from '@/shared/ui';

type Offer = Schema<'Offer'>;

export interface SearchDayListProps {
  offers: readonly Offer[];
  exactMatch?: boolean;
  zone: string;
  page?: number;
  totalPages?: number;
  onPageChange?: (page: number) => void;
  onSelectOffer: (offer: Offer) => void;
  className?: string;
}

export function SearchDayList({
  offers,
  exactMatch = true,
  zone,
  page = 0,
  totalPages = 1,
  onPageChange,
  onSelectOffer,
  className,
}: SearchDayListProps) {
  // Group offers by day
  const grouped = new Map<string, Offer[]>();
  for (const offer of offers) {
    if (!offer.startsAt) continue;
    const day = dayInZone(offer.startsAt, zone);
    const list = grouped.get(day) ?? [];
    list.push(offer);
    grouped.set(day, list);
  }

  // Sort days chronologically
  const sortedDays = Array.from(grouped.keys()).sort();

  return (
    <div className={className}>
      {/* Closest hours warning banner */}
      {!exactMatch && (
        <div
          role="status"
          className="mb-5 flex items-center gap-2.5 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900 shadow-xs"
        >
          <IconInfo className="h-5 w-5 shrink-0 text-amber-700" />
          <span className="font-medium">No hay horas en esa franja. Estas son las más cercanas</span>
        </div>
      )}

      {/* Days list */}
      <div className="space-y-6">
        {sortedDays.map((day) => {
          const dayOffers = grouped.get(day) ?? [];
          const sampleInstant = `${day}T12:00:00Z`;
          const rawDayHeading = formatInZone(sampleInstant, zone, {
            weekday: 'long',
            day: 'numeric',
            month: 'long',
          });
          const dayHeading = rawDayHeading.charAt(0).toUpperCase() + rawDayHeading.slice(1);

          return (
            <section key={day} aria-label={dayHeading} className="space-y-2.5">
              <h3 className="text-sm font-semibold text-gray-700 px-1">{dayHeading}</h3>

              <div className="space-y-2">
                {dayOffers.map((offer) => {
                  const startStr = offer.startsAt ? formatInZone(offer.startsAt, zone, 'time') : '';
                  const endStr = offer.endsAt ? formatInZone(offer.endsAt, zone, 'time') : '';
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
                      className="flex items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white p-3.5 shadow-xs hover:border-gray-300 transition-colors sm:p-4"
                    >
                      {/* Left: Time start / end */}
                      <div className="w-14 shrink-0 text-left">
                        <div className="text-sm font-bold text-gray-900">{startStr}</div>
                        <div className="text-xs text-gray-500">{endStr}</div>
                      </div>

                      {/* Center: Tutor Avatar & Details */}
                      <div className="flex min-w-0 flex-1 items-center gap-3">
                        <Avatar name={offer.tutorName ?? ''} size="md" className="shrink-0" />
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-center gap-1.5">
                            <p className="truncate text-sm font-semibold text-gray-900">{offer.tutorName}</p>
                            {offer.newTutor && <Badge tone="info">Tutor nuevo</Badge>}
                          </div>
                          <p className="truncate text-xs text-gray-600 mt-0.5">{starsText}</p>
                          {offer.sessionsTaught !== undefined && (
                            <p className="hidden text-xs text-gray-500 sm:block mt-0.5">
                              {offer.sessionsTaught} {offer.sessionsTaught === 1 ? 'sesión' : 'sesiones'} de este curso
                            </p>
                          )}
                        </div>
                      </div>

                      {/* Right: Elegir button */}
                      <Button variant="secondary" size="sm" onClick={() => onSelectOffer(offer)} className="shrink-0">
                        Elegir
                      </Button>
                    </div>
                  );
                })}
              </div>
            </section>
          );
        })}
      </div>

      {/* Pagination */}
      {onPageChange && totalPages > 1 && (
        <div className="mt-6 pt-2">
          <Pagination page={page} totalPages={totalPages} onPageChange={onPageChange} />
        </div>
      )}
    </div>
  );
}
