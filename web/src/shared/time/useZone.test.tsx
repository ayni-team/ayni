import { renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Providers } from '@/Providers';
import { fakeServer } from '@/test/fakeServer';
import { serveFrame, signIn } from '@/test/fixtures';
import { testQueryClient } from '@/test/render';
import { useUniversityToday } from './useZone';

describe('useUniversityToday', () => {
  afterEach(() => vi.useRealTimers());

  it("is the university's day, not UTC's, close to midnight UTC", async () => {
    // 2026-03-11 03:30 UTC is still the 10th in Lima (UTC-5).
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-03-11T03:30:00Z'));
    serveFrame(fakeServer());
    signIn('STUDENT');
    const wrapper = ({ children }: { children: ReactNode }) => (
      <Providers client={testQueryClient()}>{children}</Providers>
    );
    const { result } = renderHook(() => useUniversityToday(), { wrapper });
    // Before the university is known the zone is UTC; the layout never lets a screen see that.
    expect(result.current).toBe('2026-03-11');
    await vi.waitFor(() => expect(result.current).toBe('2026-03-10'));
  });
});
