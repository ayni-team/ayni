import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/shared/api';
import { fakeServer } from '@/test/fakeServer';
import { ANA, serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import NoticesPage from './NoticesPage';

const CONFIRMED: Schema<'Notice'> = {
  id: '11111111-0000-4000-8000-000000000001',
  kind: 'BOOKING_CONFIRMED',
  payload: { bookingId: 'b1', startsAt: '2026-10-27T22:00:00Z', role: 'STUDENT' },
  createdAt: '2026-10-23T14:55:00Z',
  readAt: null,
};

const PURCHASE: Schema<'Notice'> = {
  id: '11111111-0000-4000-8000-000000000002',
  kind: 'PURCHASE_CONFIRMED',
  payload: { purchaseId: 'p1', credits: '10' },
  createdAt: '2026-10-20T14:00:00Z',
  readAt: '2026-10-20T15:00:00Z',
};

function page(items: Schema<'Notice'>[], unreadCount: number): Schema<'NotificationsPage'> {
  return { items, page: 0, size: 20, total: items.length, unreadCount };
}

function setup() {
  const server = fakeServer();
  serveFrame(server, ANA);
  signIn('STUDENT');
  return server;
}

describe('FE-60 NoticesPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('lists the notices with the texts of the web and says how many are unread', async () => {
    const server = setup();
    server.on('GET', '/api/v1/notifications', { body: page([CONFIRMED, PURCHASE], 1) });

    renderWithProviders(<NoticesPage />, { route: '/avisos' });

    expect(await screen.findByText('Tu tutoría quedó confirmada')).toBeInTheDocument();
    expect(screen.getByText('Martes 27 de octubre, 17:00')).toBeInTheDocument();
    expect(screen.getByText('Tu compra se acreditó')).toBeInTheDocument();
    expect(screen.getByText('1 sin leer · los más nuevos primero')).toBeInTheDocument();
    expect(screen.getByText('(sin leer)')).toBeInTheDocument();
  });

  it('marks an unread notice read and goes to the screen it is about', async () => {
    const server = setup();
    server
      .on('GET', '/api/v1/notifications', { body: page([CONFIRMED, PURCHASE], 1) })
      .on('POST', `/api/v1/notifications/${CONFIRMED.id}/read`, { status: 204 });

    const { user, location } = renderWithProviders(<NoticesPage />, { route: '/avisos' });
    await user.click(await screen.findByRole('button', { name: /Tu tutoría quedó confirmada/ }));

    await waitFor(() => expect(location()).toBe('/mis-tutorias'));
    expect(server.requests('POST')).toHaveLength(1);
  });

  it('does not mark again a notice already read', async () => {
    const server = setup();
    server.on('GET', '/api/v1/notifications', { body: page([PURCHASE], 0) });

    const { user, location } = renderWithProviders(<NoticesPage />, { route: '/avisos' });
    await user.click(await screen.findByRole('button', { name: /Tu compra se acreditó/ }));

    await waitFor(() => expect(location()).toBe('/creditos/compras/p1'));
    expect(server.requests('POST')).toHaveLength(0);
  });

  it('marks all read, and the button is off when nothing is unread', async () => {
    const server = setup();
    let unread = 1;
    server
      .on('GET', '/api/v1/notifications', () => ({ body: page([CONFIRMED], unread) }))
      .on('POST', '/api/v1/notifications/read-all', () => {
        unread = 0;
        return { status: 204 };
      });

    const { user } = renderWithProviders(<NoticesPage />, { route: '/avisos' });
    const button = await screen.findByRole('button', { name: 'Marcar todas como leídas' });
    await waitFor(() => expect(button).toBeEnabled());
    await user.click(button);

    expect(await screen.findByText('Marcaste todos los avisos como leídos')).toBeInTheDocument();
    await waitFor(() => expect(button).toBeDisabled());
    expect(server.requests('POST')).toHaveLength(1);
  });

  it('asks the server for the unread ones only on the second tab, with its own empty state', async () => {
    const server = setup();
    server.on('GET', '/api/v1/notifications', (request) =>
      request.query.get('unread') === 'true' ? { body: page([], 0) } : { body: page([PURCHASE], 0) },
    );

    const { user } = renderWithProviders(<NoticesPage />, { route: '/avisos' });
    await screen.findByText('Tu compra se acreditó');
    await user.click(screen.getByRole('tab', { name: /Sin leer/ }));

    expect(await screen.findByText('No tienes avisos sin leer')).toBeInTheDocument();
    const asked = server.requests('GET').filter((request) => request.path === '/api/v1/notifications');
    expect(asked.at(-1)?.query.get('unread')).toBe('true');
  });

  it('says there are none yet when the person has no notices', async () => {
    const server = setup();
    server.on('GET', '/api/v1/notifications', { body: page([], 0) });

    renderWithProviders(<NoticesPage />, { route: '/avisos' });

    expect(await screen.findByText('Todavía no tienes avisos')).toBeInTheDocument();
    expect(screen.getByText('Todo leído · los más nuevos primero')).toBeInTheDocument();
  });

  it('offers to retry when the notices cannot be loaded', async () => {
    const server = setup();
    server.on('GET', '/api/v1/notifications', { status: 500, body: { status: 500, message: 'boom' } });

    renderWithProviders(<NoticesPage />, { route: '/avisos' });

    const alert = await screen.findByRole('alert');
    expect(within(alert).getByText('No pudimos cargar tus avisos')).toBeInTheDocument();
    expect(within(alert).getByRole('button', { name: /Reintentar/ })).toBeInTheDocument();
    expect(screen.queryByText('boom')).not.toBeInTheDocument();
  });
});
