import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AppLayout } from '@/shared/frame';
import { fakeServer } from '@/test/fakeServer';
import { ANA, serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import { bookingRoutes } from './routes';

describe('booking navigation', () => {
  it('keeps Mis tutorías active inside the session of a tutoring', async () => {
    serveFrame(fakeServer(), ANA);
    signIn('STUDENT');
    renderWithProviders(
      <Routes>
        <Route element={<AppLayout navigation={bookingRoutes.navigation ?? []} />}>
          <Route path="*" element={<p>Sesión</p>} />
        </Route>
      </Routes>,
      { route: '/sesion/5b2e7c1a-3f4d-4e8a-9c0b-1d2e3f4a5b6c' },
    );

    // The tab drawn as the active one (AppNav marks an `also` path by its look).
    const tab = (await screen.findAllByRole('link', { name: 'Mis tutorías' }))[0];
    expect(tab).toHaveClass('bg-brand');
    expect(screen.getAllByRole('link', { name: 'Agenda' })[0]).not.toHaveClass('bg-brand');
  });
});
