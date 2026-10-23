import { describe, expect, it } from 'vitest';
import { addDays, dayInZone, daysBetween, formatDay, formatInZone, relativeTime, todayInZone } from './format';

const LIMA = 'America/Lima';

describe('formatInZone', () => {
  it('shows an instant in the university zone, not the browser one', () => {
    // 20:00 UTC is 15:00 in Lima (UTC-5) whatever zone the machine has.
    expect(formatInZone('2026-10-23T20:00:00Z', LIMA, 'time')).toBe('15:00');
    expect(formatInZone('2026-10-23T20:00:00Z', 'UTC', 'time')).toBe('20:00');
  });

  it('writes dates in Spanish', () => {
    expect(formatInZone('2026-10-23T20:00:00Z', LIMA, 'longDate')).toBe('viernes, 23 de octubre');
  });

  it('refuses something that is not an instant', () => {
    expect(() => formatInZone('mañana', LIMA)).toThrow(RangeError);
  });
});

describe('days of the university', () => {
  it('puts a late evening in Lima on its own day, though it is already tomorrow in UTC', () => {
    expect(dayInZone('2026-10-24T03:30:00Z', LIMA)).toBe('2026-10-23');
    expect(todayInZone(LIMA, new Date('2026-10-24T03:30:00Z'))).toBe('2026-10-23');
  });

  it('adds days across months and years', () => {
    expect(addDays('2026-10-28', 7)).toBe('2026-11-04');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addDays('2026-10-23', -30)).toBe('2026-09-23');
    expect(daysBetween('2026-01-01', '2026-12-31')).toBe(364);
  });

  it('formats a day without letting a zone move it', () => {
    expect(formatDay('2026-10-23', 'longDate')).toBe('viernes, 23 de octubre');
  });
});

describe('relativeTime', () => {
  const now = new Date('2026-10-23T20:00:00Z');

  it('says how long ago and how far ahead', () => {
    expect(relativeTime('2026-10-23T19:55:00Z', now)).toBe('hace 5 min');
    expect(relativeTime('2026-10-24T00:00:00Z', now)).toBe('dentro de 4 h');
    expect(relativeTime('2026-10-23T19:59:50Z', now)).toBe('ahora');
  });
});
