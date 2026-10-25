import { addDays, dayInZone, formatDay, formatInZone } from '@/shared/time';
import type { Schema } from '@/shared/api';

type Offer = Schema<'Offer'>;

/** Finds the Monday for a given date in YYYY-MM-DD format (UTC based) */
export function getMondayOfWeek(dateStr: string): string {
  const [year, month, day] = dateStr.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  const dayOfWeek = date.getUTCDay();
  // 0 is Sunday -> 6 days back; 1 is Monday -> 0 days; etc.
  const distanceToMonday = dayOfWeek === 0 ? 6 : dayOfWeek - 1;
  date.setUTCDate(date.getUTCDate() - distanceToMonday);
  return date.toISOString().slice(0, 10);
}

/** Returns the 7 days of the week [Mon..Sun] starting from a Monday */
export function getDaysOfWeek(mondayStr: string): string[] {
  return Array.from({ length: 7 }, (_, i) => addDays(mondayStr, i));
}

/** Formats "Bruno Salas" -> "Bruno S." */
export function formatShortName(name: string | null | undefined): string {
  if (!name) return '';
  const parts = name.trim().split(/\s+/);
  if (parts.length > 1) {
    return `${parts[0]} ${parts[1][0]}.`;
  }
  return parts[0];
}

/** Key for slot grouping: YYYY-MM-DD_HH */
export function getSlotKey(day: string, hour: number): string {
  return `${day}_${hour}`;
}

/** 14 -> "14:00" */
export function formatHourLabel(hour: number): string {
  return `${String(hour).padStart(2, '0')}:00`;
}

/** "26 oct – 1 nov" */
export function formatWeekRange(mondayStr: string): string {
  const sundayStr = addDays(mondayStr, 6);
  const from = formatDay(mondayStr, { day: 'numeric', month: 'short' }).replace('.', '');
  const to = formatDay(sundayStr, { day: 'numeric', month: 'short' }).replace('.', '');
  return `${from} – ${to}`;
}

/** "También libre 16:00 y 18:00" or "Solo esta hora" */
export function getOtherFreeHoursText(
  allOffers: readonly Offer[],
  currentOffer: Offer,
  zone: string,
): string {
  if (!currentOffer.startsAt || !currentOffer.tutorId) {
    return 'Solo esta hora';
  }

  const currentDay = dayInZone(currentOffer.startsAt, zone);
  const currentHourStr = formatInZone(currentOffer.startsAt, zone, 'time');

  const otherHours = allOffers
    .filter((o) => {
      if (o.tutorId !== currentOffer.tutorId || !o.startsAt) return false;
      const oDay = dayInZone(o.startsAt, zone);
      const oHourStr = formatInZone(o.startsAt, zone, 'time');
      return oDay === currentDay && oHourStr !== currentHourStr;
    })
    .map((o) => formatInZone(o.startsAt!, zone, 'time'));

  const uniqueHours = Array.from(new Set(otherHours)).sort();

  if (uniqueHours.length === 0) {
    return 'Solo esta hora';
  }

  if (uniqueHours.length === 1) {
    return `También libre ${uniqueHours[0]}`;
  }

  const last = uniqueHours[uniqueHours.length - 1];
  const rest = uniqueHours.slice(0, -1).join(', ');
  return `También libre ${rest} y ${last}`;
}
