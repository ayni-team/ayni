import { describe, expect, it } from 'vitest';
import { startOfDayInZone } from './dateUtils';

describe('startOfDayInZone', () => {
  it("gives the instant a day of the university starts", () => {
    expect(startOfDayInZone('2026-10-26', 'America/Lima')).toBe('2026-10-26T05:00:00.000Z');
    expect(startOfDayInZone('2026-10-26', 'UTC')).toBe('2026-10-26T00:00:00.000Z');
  });

  it('follows a zone with daylight saving time', () => {
    expect(startOfDayInZone('2026-07-01', 'America/Santiago')).toBe('2026-07-01T04:00:00.000Z');
    expect(startOfDayInZone('2026-12-01', 'America/Santiago')).toBe('2026-12-01T03:00:00.000Z');
  });
});
