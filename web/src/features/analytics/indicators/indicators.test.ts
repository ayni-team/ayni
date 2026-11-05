import { describe, expect, it } from 'vitest';
import {
  axisOf,
  dayFigures,
  defaultPeriod,
  hasActivity,
  labelEvery,
  longDayText,
  peakOf,
  periodError,
  searchesText,
  shortDayText,
} from './indicators';

describe('FE-53 indicators logic', () => {
  it('starts on the last 30 days, today included', () => {
    expect(defaultPeriod('2026-10-23')).toEqual({ from: '2026-09-24', to: '2026-10-23' });
  });

  it('refuses a period the server would refuse, counting both ends', () => {
    expect(periodError('2025-01-01', '2026-01-01')).toBeNull();
    expect(periodError('2025-01-01', '2026-01-02')).toBe('TOO_LONG');
    expect(periodError('2026-10-23', '2026-10-01')).toBe('REVERSED');
    expect(periodError('', '2026-10-01')).toBe('INCOMPLETE');
  });

  it('knows a period with no activity from one with only searches', () => {
    expect(hasActivity({ totals: { sessionsCompleted: 0, hoursExchanged: 0 }, days: [{ day: '2026-10-01' }] })).toBe(
      false,
    );
    expect(hasActivity(undefined)).toBe(false);
    expect(hasActivity({ totals: { sessionsCompleted: 1 } })).toBe(true);
    expect(hasActivity({ uncoveredDemand: [{ catalogItemId: 'a', searches: 2 }] })).toBe(true);
  });

  it('rounds the axis just above the highest bar', () => {
    expect(axisOf(9)).toEqual({ top: 10, middle: 5 });
    expect(axisOf(3)).toEqual({ top: 4, middle: 2 });
    expect(axisOf(1)).toEqual({ top: 2, middle: 1 });
    expect(axisOf(141)).toEqual({ top: 200, middle: 100 });
  });

  it('finds the highest day, and none when no hours were exchanged', () => {
    const low = { day: '2026-10-01', figures: { hoursExchanged: 2 } };
    const high = { day: '2026-10-09', figures: { hoursExchanged: 9 } };
    expect(peakOf([low, high])).toBe(high);
    expect(peakOf([{ day: '2026-10-01', figures: { hoursExchanged: 0 } }])).toBeNull();
  });

  it('writes days and figures as the frame does, with no zone moving the day', () => {
    expect(shortDayText('2026-10-09')).toBe('9 oct');
    expect(longDayText('2026-10-09')).toBe('Vie 9 oct');
    expect(dayFigures({ figures: { hoursExchanged: 9, sessionsCompleted: 8 } })).toBe('9 horas · 8 sesiones');
    expect(dayFigures({ figures: { hoursExchanged: 1, sessionsCompleted: 1 } })).toBe('1 hora · 1 sesión');
    expect(searchesText(1)).toBe('1 búsqueda');
  });

  it('labels fewer days the longer the period', () => {
    expect(labelEvery(7)).toBe(1);
    expect(labelEvery(30)).toBe(3);
    expect(labelEvery(366)).toBe(30);
  });
});
