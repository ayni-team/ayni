import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { clockOffset, formatCountdown, remainingMs, useCountdown } from './countdown';

describe('countdown', () => {
  it('writes what is left as the hold shows it', () => {
    expect(formatCountdown(272_000)).toBe('4:32');
    expect(formatCountdown(500)).toBe('0:01');
    expect(formatCountdown(0)).toBe('0:00');
    expect(formatCountdown(3_872_000)).toBe('1:04:32');
  });

  it('never counts below zero', () => {
    expect(remainingMs('2026-10-23T20:00:00Z', Date.parse('2026-10-23T20:05:00Z'))).toBe(0);
  });
});

describe('clockOffset', () => {
  const browser = Date.parse('2026-10-23T20:00:00Z');

  it('is the server minus the browser', () => {
    expect(clockOffset('2026-10-23T20:00:30Z', browser)).toBe(30_000);
    expect(clockOffset('2026-10-23T19:59:00Z', browser)).toBe(-60_000);
    expect(clockOffset('2026-10-23T20:00:00Z', browser)).toBe(0);
  });

  it('is zero when the server did not say its time', () => {
    expect(clockOffset(undefined, browser)).toBe(0);
    expect(clockOffset(null, browser)).toBe(0);
    expect(clockOffset('not a date', browser)).toBe(0);
  });

  it('measures against now by default', () => {
    vi.useFakeTimers();
    vi.setSystemTime(browser);
    expect(clockOffset('2026-10-23T20:00:05Z')).toBe(5_000);
    vi.useRealTimers();
  });
});

describe('useCountdown', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-23T20:00:00Z'));
  });
  afterEach(() => vi.useRealTimers());

  it('counts down every second and says when it is over', () => {
    const { result } = renderHook(() => useCountdown('2026-10-23T20:00:03Z'));
    expect(result.current.label).toBe('0:03');

    act(() => vi.advanceTimersByTime(2000));
    expect(result.current.label).toBe('0:01');
    expect(result.current.expired).toBe(false);

    act(() => vi.advanceTimersByTime(1000));
    expect(result.current.expired).toBe(true);
  });

  it('counts against the server when its clock is ahead of the browser', () => {
    // The server says it is 20:05:00 while the browser says 20:00:00; the hold ends 20:05:30 server time.
    const offsetMs = clockOffset('2026-10-23T20:05:00Z');
    const { result } = renderHook(() => useCountdown('2026-10-23T20:05:30Z', { offsetMs }));
    expect(result.current.label).toBe('0:30');

    act(() => vi.advanceTimersByTime(29_000));
    expect(result.current.label).toBe('0:01');
    expect(result.current.expired).toBe(false);

    act(() => vi.advanceTimersByTime(1000));
    expect(result.current.expired).toBe(true);
  });

  it('counts against the server when its clock is behind the browser', () => {
    // The server says 19:55:00; the hold lasts the usual five minutes, until 20:00:00 server time.
    const offsetMs = clockOffset('2026-10-23T19:55:00Z');
    const { result } = renderHook(() => useCountdown('2026-10-23T20:00:00Z', { offsetMs }));
    expect(result.current.label).toBe('5:00');
  });

  it('has nothing to count without an instant', () => {
    const { result } = renderHook(() => useCountdown(null));
    expect(result.current).toEqual({ remainingMs: 0, expired: true, label: '0:00' });
  });
});
