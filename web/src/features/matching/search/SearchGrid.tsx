import type { Schema } from '@/shared/api';
import { dayInZone, formatDay, formatInZone } from '@/shared/time';
import { Avatar, cx } from '@/shared/ui';
import { formatHourLabel, formatShortName, getDaysOfWeek, getSlotKey } from './dateUtils';

type Offer = Schema<'Offer'>;
type StudentBooking = Schema<'StudentBooking'>;

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
    <div className="flex-1 rounded-xl border border-gray-200 bg-white p-4 shadow-sm sm:p-5">
      {/* Grid Header & Legend */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between border-b border-gray-100 pb-4">
        <div>
          <h2 className="text-base font-bold text-gray-900">Horas libres · {courseName}</h2>
          <p className="text-xs text-gray-500 mt-0.5">🌐 Hora de Lima · cada bloque es 1 hora = 1 crédito</p>
        </div>

        {/* Legend */}
        <div className="flex flex-wrap items-center gap-2 text-xs">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-blue-50 px-2.5 py-0.5 font-medium text-blue-800 border border-blue-100">
            1 tutor
          </span>
          <span className="inline-flex items-center gap-1.5 rounded-full border border-cyan-400 bg-cyan-50 px-2.5 py-0.5 font-medium text-cyan-900">
            Varios tutores
          </span>
          <span className="inline-flex items-center gap-1.5 rounded-full border border-dashed border-gray-400 bg-gray-50 px-2.5 py-0.5 font-medium text-gray-700">
            Tu tutoría
          </span>
        </div>
      </div>

      {/* Calendar Grid Table */}
      <div className="mt-4 overflow-x-auto">
        <table className="w-full border-collapse text-center">
          <thead>
            <tr>
              <th scope="col" className="w-14 pb-3 text-xs font-normal text-gray-400" />
              {daysOfWeek.map((day) => {
                const dayName = formatDay(day, { weekday: 'short' }).replace('.', '').toUpperCase();
                const dayNum = parseInt(day.split('-')[2], 10);
                const isSelectedDay = selectedSlot?.day === day;

                return (
                  <th key={day} scope="col" className="pb-3 text-center min-w-[100px] px-1">
                    <div
                      className={cx(
                        'text-xs font-semibold uppercase',
                        isSelectedDay ? 'text-brand-text' : 'text-gray-500',
                      )}
                    >
                      {dayName}
                    </div>
                    <div className={cx('text-base font-bold', isSelectedDay ? 'text-brand-text' : 'text-gray-900')}>
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
                <tr key={hour} className="border-t border-gray-100">
                  {/* Hour label column */}
                  <th
                    scope="row"
                    className="w-14 py-3 pr-2 text-right align-top text-xs font-normal text-gray-400 select-none"
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
                      <td key={day} className="p-1 align-top h-14 border-l border-gray-100">
                        {userBooking ? (
                          <div className="rounded-lg border border-dashed border-gray-300 bg-gray-50 p-1.5 text-left select-none">
                            <div className="flex items-center gap-1">
                              <Avatar
                                name={userBooking.tutor?.name ?? ''}
                                size="sm"
                                className="h-5 w-5 text-[9px] shrink-0"
                              />
                              <div className="min-w-0">
                                <p className="truncate text-[11px] font-semibold text-gray-900 leading-tight">
                                  Tu tutoría
                                </p>
                                <p className="truncate text-[10px] text-gray-500 leading-tight">
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
                              'w-full rounded-lg p-1.5 text-left transition-all focus:outline-none focus:ring-2',
                              isSelected
                                ? 'bg-brand-primary text-brand-secondary shadow-sm ring-2 ring-brand-primary ring-offset-1'
                                : slotOffers.length === 1
                                  ? 'border border-blue-100 bg-blue-50/80 hover:bg-blue-100 text-gray-900 focus:ring-blue-400'
                                  : 'border border-cyan-300 bg-cyan-50/80 hover:bg-cyan-100 text-cyan-950 focus:ring-cyan-400',
                            )}
                          >
                            {isSelected ? (
                              <div className="flex items-center gap-1.5">
                                <div className="flex -space-x-1 overflow-hidden shrink-0">
                                  {slotOffers.slice(0, 3).map((o, idx) => (
                                    <Avatar
                                      key={idx}
                                      name={o.tutorName ?? ''}
                                      size="sm"
                                      className="h-5 w-5 text-[9px] ring-1 ring-white"
                                    />
                                  ))}
                                </div>
                                <span className="truncate text-xs font-bold text-brand-secondary">
                                  {slotOffers.length} {slotOffers.length === 1 ? 'tutor' : 'tutores'}
                                </span>
                              </div>
                            ) : slotOffers.length === 1 ? (
                              <div className="flex items-center gap-1.5">
                                <Avatar
                                  name={slotOffers[0].tutorName ?? ''}
                                  size="sm"
                                  className="h-5 w-5 text-[9px] shrink-0"
                                />
                                <div className="min-w-0 flex-1">
                                  <p className="truncate text-xs font-semibold text-gray-900 leading-tight">
                                    {formatShortName(slotOffers[0].tutorName)}
                                  </p>
                                  <p className="text-[10px] text-gray-600 leading-tight">
                                    {slotOffers[0].newTutor
                                      ? 'Nuevo'
                                      : `★ ${Number(slotOffers[0].averageStars ?? 0).toFixed(1)}`}
                                  </p>
                                </div>
                              </div>
                            ) : (
                              <div className="flex items-center gap-1.5">
                                <div className="flex -space-x-1 overflow-hidden shrink-0">
                                  {slotOffers.slice(0, 3).map((o, idx) => (
                                    <Avatar
                                      key={idx}
                                      name={o.tutorName ?? ''}
                                      size="sm"
                                      className="h-5 w-5 text-[9px] ring-1 ring-white"
                                    />
                                  ))}
                                </div>
                                <span className="truncate text-xs font-medium text-cyan-950">
                                  {slotOffers.length} tutores
                                </span>
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
