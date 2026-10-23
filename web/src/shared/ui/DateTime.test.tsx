import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import { DateTime } from './DateTime';

describe('DateTime', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows the instant in the zone the list gives, keeping the exact value', () => {
    renderWithProviders(<DateTime value="2026-10-23T20:00:00Z" zone="America/Lima" format="time" />);
    const time = screen.getByText('15:00');
    expect(time.tagName).toBe('TIME');
    expect(time).toHaveAttribute('datetime', '2026-10-23T20:00:00Z');
  });

  it("uses the university's zone when the list gives none, never the browser's", async () => {
    serveFrame(fakeServer());
    signIn('STUDENT');
    renderWithProviders(<DateTime value="2026-10-23T20:00:00Z" format="time" />);
    // UTC until the university is known, then Lima.
    expect(screen.getByText('20:00')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('15:00')).toBeInTheDocument());
  });
});
