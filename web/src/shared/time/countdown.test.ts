import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { formatCountdown, remainingMs, useCountdown } from './countdown';

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

  it('has nothing to count without an instant', () => {
    const { result } = renderHook(() => useCountdown(null));
    expect(result.current).toEqual({ remainingMs: 0, expired: true, label: '0:00' });
  });
});
