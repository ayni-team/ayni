import { formatDay } from '@/shared/time';

const DAY = /^\d{4}-\d{2}-\d{2}$/;

/** What is wrong with the hours typed, or nothing: a whole number of at least one. */
export function hoursError(value: string): string | undefined {
  const text = value.trim();
  if (!text) return 'Escribe cuántas horas pide tu universidad';
  if (!/^\d+$/.test(text)) return 'Escribe un número entero de horas';
  if (Number(text) < 1) return 'Pide al menos 1 hora';
  return undefined;
}

/**
 * What is wrong with the start day, or nothing. It is optional (today when empty) and never in the
 * past: "today" is the university's day, which is what the server compares it with.
 */
export function startError(value: string, today: string): string | undefined {
  if (!value) return undefined;
  if (!DAY.test(value)) return 'Elige un día válido';
  if (value < today) return 'Elige hoy o un día que no haya pasado';
  return undefined;
}

/** "20 horas", "1 hora". */
export function hoursText(hours: number | undefined): string {
  const value = hours ?? 0;
  return `${value} ${value === 1 ? 'hora' : 'horas'}`;
}

/** "20 de octubre de 2026", a day of the university's calendar. */
export function dayText(day: string | undefined): string {
  if (!day || !DAY.test(day)) return '';
  try {
    return formatDay(day, { day: 'numeric', month: 'long', year: 'numeric' });
  } catch {
    return '';
  }
}

/**
 * What to tell the coordinator once the rule is saved. A rule that starts later leaves the current one
 * running until that day, so it says when the new one begins.
 */
export function savedText(hours: number | undefined, validFrom: string | undefined, today: string): string {
  if (validFrom && validFrom > today) {
    return `Desde el ${dayText(validFrom)} tu universidad pedirá ${hoursText(hours)}. Hasta entonces sigue la regla actual.`;
  }
  return `Tu universidad pide ahora ${hoursText(hours)}.`;
}
