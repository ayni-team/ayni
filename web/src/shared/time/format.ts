/**
 * Every instant arrives in UTC and is shown in the university's zone (`MyUniversityView.timezone`,
 * or the `timezone` of a list), never in the browser's. Nothing here reads the zone of the machine:
 * every function takes the zone it formats in.
 *
 * Days without time (`from`, `to` of the agenda, `2026-10-23`) are days of the university and are
 * handled as plain `YYYY-MM-DD` strings, so no zone can move them.
 */

const LOCALE = 'es-PE';

export type DateTimePreset = 'date' | 'longDate' | 'shortDate' | 'time' | 'dateTime' | 'weekday';

const PRESETS: Record<DateTimePreset, Intl.DateTimeFormatOptions> = {
  // 23 oct. 2026
  date: { day: 'numeric', month: 'short', year: 'numeric' },
  // viernes, 23 de octubre
  longDate: { weekday: 'long', day: 'numeric', month: 'long' },
  // vie. 23 oct.
  shortDate: { weekday: 'short', day: 'numeric', month: 'short' },
  // 15:00
  time: { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' },
  // 23 oct. 2026, 15:00
  dateTime: { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' },
  // viernes
  weekday: { weekday: 'long' },
};

const formatters = new Map<string, Intl.DateTimeFormat>();

function formatter(zone: string, options: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
  const key = `${zone}|${JSON.stringify(options)}`;
  let cached = formatters.get(key);
  if (!cached) {
    cached = new Intl.DateTimeFormat(LOCALE, { ...options, timeZone: zone });
    formatters.set(key, cached);
  }
  return cached;
}

function toDate(value: string | Date): Date {
  const date = value instanceof Date ? value : new Date(value);
  if (Number.isNaN(date.getTime())) {
    throw new RangeError(`Not an instant: ${String(value)}`);
  }
  return date;
}

/** Formats an instant in a zone, with one of the presets or Intl options of your own. */
export function formatInZone(
  value: string | Date,
  zone: string,
  format: DateTimePreset | Intl.DateTimeFormatOptions = 'dateTime',
): string {
  const options = typeof format === 'string' ? PRESETS[format] : format;
  return formatter(zone, options).format(toDate(value));
}

/** The university's day (`YYYY-MM-DD`) an instant falls on. */
export function dayInZone(value: string | Date, zone: string): string {
  const parts = formatter(zone, { year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(
    toDate(value),
  );
  const part = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

/** "Today" is the university's today. */
export function todayInZone(zone: string, now: Date = new Date()): string {
  return dayInZone(now, zone);
}

const DAY = /^(\d{4})-(\d{2})-(\d{2})$/;

function parseDay(day: string): Date {
  const match = DAY.exec(day);
  if (!match) throw new RangeError(`Not a day: ${day}`);
  return new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])));
}

/** A day plus some days, as `YYYY-MM-DD`: the next 7 days of a search, the last 30 of indicators. */
export function addDays(day: string, days: number): string {
  const date = parseDay(day);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/** How many days from one day to another (`to` minus `from`). */
export function daysBetween(from: string, to: string): number {
  return Math.round((parseDay(to).getTime() - parseDay(from).getTime()) / 86_400_000);
}

/** Formats a day without time. It is already the university's day, so no zone applies. */
export function formatDay(day: string, format: DateTimePreset | Intl.DateTimeFormatOptions = 'date'): string {
  return formatInZone(parseDay(day), 'UTC', format);
}

const relative = new Intl.RelativeTimeFormat('es', { numeric: 'auto', style: 'short' });

/** "hace 5 min", "en 4 h", "ayer": how far an instant is from now. */
export function relativeTime(value: string | Date, now: Date = new Date()): string {
  const seconds = Math.round((toDate(value).getTime() - now.getTime()) / 1000);
  const abs = Math.abs(seconds);
  if (abs < 45) return relative.format(0, 'second');
  if (abs < 45 * 60) return relative.format(Math.round(seconds / 60), 'minute');
  if (abs < 22 * 3600) return relative.format(Math.round(seconds / 3600), 'hour');
  if (abs < 26 * 86_400) return relative.format(Math.round(seconds / 86_400), 'day');
  if (abs < 320 * 86_400) return relative.format(Math.round(seconds / (30 * 86_400)), 'month');
  return relative.format(Math.round(seconds / (365 * 86_400)), 'year');
}
