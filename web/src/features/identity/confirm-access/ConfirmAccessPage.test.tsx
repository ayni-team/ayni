import { screen, waitFor } from '@testing-library/react';
import { StrictMode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { currentSession } from '@/shared/session';
import { fakeServer } from '@/test/fakeServer';
import { renderWithProviders } from '@/test/render';
import ConfirmAccessPage from './ConfirmAccessPage';

const OPENED = {
  sessionToken: 'opened',
  tenantId: 'UPC',
  userId: 'u1',
  role: 'COORDINATOR',
  expiresAt: '2099-01-01T00:00:00Z',
};

describe('FE-02 confirm the link', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('spends the token once, even under the double effect of development, and keeps the session', async () => {
    const server = fakeServer().on('POST', '/api/v1/access/confirm', { body: OPENED });
    const { location } = renderWithProviders(
      <StrictMode>
        <ConfirmAccessPage />
      </StrictMode>,
      { route: '/access/confirm?token=abc', path: '/access/confirm' },
    );

    await waitFor(() => expect(location()).toBe('/'));
    expect(server.requests('POST')).toHaveLength(1);
    expect(server.requests('POST')[0].body).toEqual({ token: 'abc' });
    expect(currentSession()?.role).toBe('COORDINATOR');
  });

  it('says a used or expired link is spent and offers another', async () => {
    fakeServer().on('POST', '/api/v1/access/confirm', { status: 409, body: { message: 'Already used' } });
    renderWithProviders(<ConfirmAccessPage />, { route: '/access/confirm?token=abc', path: '/access/confirm' });

    expect(await screen.findByRole('heading', { name: 'El enlace venció o ya se usó' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Pedir otro enlace' })).toHaveAttribute('href', '/ingresar');
    expect(currentSession()).toBeNull();
  });

  it('lets the person retry when the platform failed', async () => {
    let status = 503;
    const server = fakeServer().on('POST', '/api/v1/access/confirm', () => ({ status, body: status === 200 ? OPENED : {} }));
    const { user, location } = renderWithProviders(<ConfirmAccessPage />, {
      route: '/access/confirm?token=abc',
      path: '/access/confirm',
    });

    const retry = await screen.findByRole('button', { name: 'Reintentar' });
    status = 200;
    await user.click(retry);

    await waitFor(() => expect(location()).toBe('/'));
    expect(server.requests('POST')).toHaveLength(2);
  });
});
