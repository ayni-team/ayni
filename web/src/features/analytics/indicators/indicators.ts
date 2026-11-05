import type { Schema } from '@/shared/api';
import { addDays, daysBetween, formatDay } from '@/shared/time';

export type Indicators = Schema<'UsageIndicators'>;
export type UsageDay = Schema<'UsageDay'>;

/** The longest period the server calculates, counting both ends. */
export const MAX_DAYS = 366;

/** The default: the last 30 days, today included. */
export function defaultPeriod(today: string): { from: string; to: string } {
  return { from: addDays(today, -29), to: today };
}

export type PeriodError = 'INCOMPLETE' | 'REVERSED' | 'TOO_LONG';

/** What is wrong with a period, or nothing. Nothing is asked of the server until it is right. */
export function periodError(from: string, to: string): PeriodError | null {
  if (!from || !to) return 'INCOMPLETE';
  if (from > to) return 'REVERSED';
  return daysBetween(from, to) + 1 > MAX_DAYS ? 'TOO_LONG' : null;
}

export function periodErrorText(error: PeriodError): string {
  switch (error) {
    case 'INCOMPLETE':
      return 'Elige las dos fechas';
    case 'REVERSED':
      return 'Debe ser después de Desde';
    case 'TOO_LONG':
      return `Hasta ${MAX_DAYS} días`;
  }
}

/** Whether the period has anything to show: days without figures and no searches are nothing. */
export function hasActivity(indicators: Indicators | undefined): boolean {
  const totals = indicators?.totals;
  return (
    Object.values(totals ?? {}).some((figure) => (figure ?? 0) > 0) || (indicators?.uncoveredDemand?.length ?? 0) > 0
  );
}

/** The top and the middle of the axis: round numbers just above the highest bar. */
export function axisOf(highest: number): { top: number; middle: number } {
  for (let magnitude = 1; ; magnitude *= 10) {
    for (const step of [1, 2, 5]) {
      if (step * magnitude * 2 >= highest) return { top: step * magnitude * 2, middle: step * magnitude };
    }
  }
}

export function hoursOf(day: UsageDay): number {
  return day.figures?.hoursExchanged ?? 0;
}

/** The highest day, for the line under the title. Nothing when no hours were exchanged. */
export function peakOf(days: readonly UsageDay[]): UsageDay | null {
  const peak = days.reduce<UsageDay | null>(
    (best, day) => (hoursOf(day) > (best ? hoursOf(best) : 0) ? day : best),
    null,
  );
  return peak && hoursOf(peak) > 0 ? peak : null;
}

function withoutDots(text: string): string {
  return text.replace(/\./g, '');
}

/** "9 oct". */
export function shortDayText(day: string): string {
  return withoutDots(formatDay(day, { day: 'numeric', month: 'short' }));
}

/** "Vie 9 oct". */
export function longDayText(day: string): string {
  const text = withoutDots(formatDay(day, { weekday: 'short', day: 'numeric', month: 'short' })).replace(',', '');
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function count(value: number, one: string, many: string): string {
  return `${value} ${value === 1 ? one : many}`;
}

/** "9 horas · 8 sesiones". */
export function dayFigures(day: UsageDay): string {
  return `${count(hoursOf(day), 'hora', 'horas')} · ${count(day.figures?.sessionsCompleted ?? 0, 'sesión', 'sesiones')}`;
}

/** "23 búsquedas". */
export function searchesText(searches: number | undefined): string {
  return count(searches ?? 0, 'búsqueda', 'búsquedas');
}

/** Every how many days a label fits under the bars. */
export function labelEvery(dayCount: number): number {
  if (dayCount <= 14) return 1;
  if (dayCount <= 45) return 3;
  if (dayCount <= 120) return 7;
  return 30;
}
