import type { Schema } from '@/shared/api';
import { dayInZone, formatDay, formatInZone } from '@/shared/time';
import { Avatar, cx } from '@/shared/ui';
import { formatHourLabel, formatShortName, getDaysOfWeek, getSlotKey } from './dateUtils';

type Offer = Schema<'Offer'>;
type StudentBooking = Schema<'StudentBooking'>;

/** The globe before the zone of the hours, as the design draws it (shared/ui has none yet). */
function IconGlobe({ className }: { className?: string }) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="10" />
      <path d="M2 12h20" />
      <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
    </svg>
  );
}

export interface SelectedSlot {
  day: string;
  hour: number;
  offers: readonly Offer[];
}

export interface SearchGridProps {
  courseName: string;
  mondayStr: string;
  offers: readonly Offer[];
  myBookings?: readonly StudentBooking[];
  zone: string;
  selectedSlot: SelectedSlot | null;
  onSelectSlot: (slot: SelectedSlot) => void;
}

export function SearchGrid({
  courseName,
  mondayStr,
  offers,
  myBookings = [],
  zone,
  selectedSlot,
  onSelectSlot,
}: SearchGridProps) {
  const daysOfWeek = getDaysOfWeek(mondayStr);

  // Group offers by day and hour
  const offersBySlot = new Map<string, Offer[]>();
  for (const offer of offers) {
    if (!offer.startsAt) continue;
    const day = dayInZone(offer.startsAt, zone);
    const timeStr = formatInZone(offer.startsAt, zone, 'time');
    const hour = parseInt(timeStr.split(':')[0], 10);
    const key = getSlotKey(day, hour);
    const existing = offersBySlot.get(key) ?? [];
    existing.push(offer);
    offersBySlot.set(key, existing);
  }

  // Also index user's upcoming bookings
  const bookingsBySlot = new Map<string, StudentBooking>();
  for (const booking of myBookings) {
    if (!booking.startsAt) continue;
    const day = dayInZone(booking.startsAt, zone);
    const timeStr = formatInZone(booking.startsAt, zone, 'time');
    const hour = parseInt(timeStr.split(':')[0], 10);
    const key = getSlotKey(day, hour);
    bookingsBySlot.set(key, booking);
  }

  // Determine hours range (default 14 to 20, or adapt to offers)
  let minHour = 14;
  let maxHour = 20;
  for (const offer of offers) {
    if (!offer.startsAt) continue;
    const hour = parseInt(formatInZone(offer.startsAt, zone, 'time').split(':')[0], 10);
    if (!Number.isNaN(hour)) {
      minHour = Math.min(minHour, hour);
      maxHour = Math.max(maxHour, hour);
    }
  }

  const hoursList: number[] = [];
  for (let h = minHour; h <= maxHour; h++) {
    hoursList.push(h);
  }

  return (
    <div className="min-w-0 flex-1 rounded-[20px] border border-line bg-white p-4 sm:p-5">
      {/* Grid Header & Legend */}
      <div className="flex flex-col gap-3 pb-4 xl:flex-row xl:items-start xl:justify-between">
        <div className="min-w-0">
          <h2 className="font-display text-lg font-semibold text-ink">Horas libres · {courseName}</h2>
          <p className="mt-0.5 flex items-center gap-1 text-[13px] text-ink-2">
            <IconGlobe className="h-3.5 w-3.5 shrink-0" />
            Hora de Lima · cada bloque es 1 hora = 1 crédito
          </p>
        </div>

        {/* Legend: swatches of 12 px, as the design draws them */}
        <ul className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[13px] text-ink-2" aria-label="Leyenda">
          <li className="inline-flex items-center gap-1.5">
            <span aria-hidden="true" className="h-3 w-3 rounded-[3px] bg-info-soft" />1 tutor
          </li>
          <li className="inline-flex items-center gap-1.5">
            <span aria-hidden="true" className="h-3 w-3 rounded-[3px] border border-info-text bg-info-soft" />
            Varios tutores
          </li>
          <li className="inline-flex items-center gap-1.5">
            <span aria-hidden="true" className="h-3 w-3 rounded-[3px] border border-dashed border-ink-3 bg-white" />
            Tu tutoría
          </li>
        </ul>
      </div>

      {/* Calendar Grid Table: the columns share what there is, so the panel beside it fits */}
      <div className="overflow-x-auto">
        <table className="w-full table-fixed border-collapse text-center">
          <thead>
            <tr>
              <th scope="col" className="w-12 pb-3 text-xs font-normal text-ink-2" />
              {daysOfWeek.map((day) => {
                const dayName = formatDay(day, { weekday: 'short' }).replace('.', '').toUpperCase();
                const dayNum = parseInt(day.split('-')[2], 10);
                const isSelectedDay = selectedSlot?.day === day;

                return (
                  <th key={day} scope="col" className="px-1 pb-3 text-center">
                    <div
                      className={cx(
                        'text-xs font-semibold uppercase',
                        isSelectedDay ? 'text-brand-text' : 'text-ink-2',
                      )}
                    >
                      {dayName}
                    </div>
                    <div
                      className={cx(
                        'font-display text-xl font-semibold',
                        isSelectedDay ? 'text-brand-text' : 'text-ink',
                      )}
                    >
                      {dayNum}
                    </div>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {hoursList.map((hour) => {
              const hourLabel = formatHourLabel(hour);

              return (
                <tr key={hour} className="border-t border-line">
                  {/* Hour label column */}
                  <th
                    scope="row"
                    className="w-12 select-none py-3 pr-2 text-right align-top text-xs font-normal text-ink-2"
                  >
                    {hourLabel}
                  </th>

                  {/* Day columns */}
                  {daysOfWeek.map((day) => {
                    const key = getSlotKey(day, hour);
                    const slotOffers = offersBySlot.get(key) ?? [];
                    const userBooking = bookingsBySlot.get(key);
                    const isSelected = selectedSlot?.day === day && selectedSlot?.hour === hour;

                    return (
                      <td key={day} className="h-14 border-l border-line p-1 align-top">
                        {userBooking ? (
                          <div className="select-none rounded-lg border border-dashed border-ink-3 bg-white p-1.5 text-left">
                            <div className="flex items-center gap-1">
                              <Avatar
                                name={userBooking.tutor?.name ?? ''}
                                size="sm"
                                className="!h-6 !w-6 !text-[9px] shrink-0"
                              />
                              <div className="min-w-0">
                                <p className="truncate text-[11px] font-semibold leading-tight text-ink">Tu tutoría</p>
                                <p className="truncate text-[10px] leading-tight text-ink-2">
                                  con {userBooking.tutor?.name?.split(' ')[0]}
                                </p>
                              </div>
                            </div>
                          </div>
                        ) : slotOffers.length > 0 ? (
                          <button
                            type="button"
                            onClick={() => onSelectSlot({ day, hour, offers: slotOffers })}
                            aria-label={`Ver ${slotOffers.length} tutores disponibles a las ${hourLabel}`}
                            className={cx(
                              'w-full rounded-lg p-1.5 text-left transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand',
                              isSelected
                                ? 'bg-brand-primary text-brand-secondary'
                                : slotOffers.length === 1
                                  ? 'border border-transparent bg-info-soft text-ink hover:border-info-text'
                                  : 'border border-info-text bg-info-soft text-info-text hover:bg-white',
                            )}
                          >
                            {isSelected || slotOffers.length > 1 ? (
                              // Several tutors, or the hour chosen: the faces on top and how many below, as
                              // the design draws it, so a narrow column still reads.
                              <div className="flex flex-col items-start gap-1">
                                <div className="flex shrink-0 -space-x-1 overflow-hidden">
                                  {slotOffers.slice(0, 3).map((o, idx) => (
                                    <Avatar
                                      key={idx}
                                      name={o.tutorName ?? ''}
                                      size="sm"
                                      className="!h-6 !w-6 !text-[9px] ring-1 ring-white"
                                    />
                                  ))}
                                </div>
                                <span
                                  className={cx(
                                    'max-w-full truncate text-xs font-semibold',
                                    isSelected ? 'text-brand-secondary' : 'text-info-text',
                                  )}
                                >
                                  {slotOffers.length} {slotOffers.length === 1 ? 'tutor' : 'tutores'}
                                </span>
                              </div>
                            ) : (
                              <div className="flex items-center gap-1.5">
                                <Avatar
                                  name={slotOffers[0].tutorName ?? ''}
                                  size="sm"
                                  className="!h-6 !w-6 !text-[9px] shrink-0"
                                />
                                <div className="min-w-0 flex-1">
                                  <p className="truncate text-xs font-semibold leading-tight text-ink">
                                    {formatShortName(slotOffers[0].tutorName)}
                                  </p>
                                  <p className="text-[10px] leading-tight text-ink-2">
                                    {slotOffers[0].newTutor
                                      ? 'Nuevo'
                                      : `★ ${Number(slotOffers[0].averageStars ?? 0).toFixed(1)}`}
                                  </p>
                                </div>
                              </div>
                            )}
                          </button>
                        ) : (
                          <div className="h-full w-full" />
                        )}
                      </td>
                    );
                  })}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
