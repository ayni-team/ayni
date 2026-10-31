import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/shared/api';
import { fakeServer } from '@/test/fakeServer';
import { ANA, serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import { NoticesBell } from './NoticesBell';

const RESOLVED: Schema<'Notice'> = {
  id: '22222222-0000-4000-8000-000000000001',
  kind: 'REQUEST_RESOLVED',
  payload: { requestId: 'r1', approved: 'true' },
  createdAt: '2026-10-23T14:55:00Z',
  readAt: null,
};

function tray(items: Schema<'Notice'>[], unreadCount: number): Schema<'NotificationsPage'> {
  return { items, page: 0, size: 4, total: items.length, unreadCount };
}

function setup() {
  const server = fakeServer();
  serveFrame(server, ANA);
  signIn('STUDENT');
  return server;
}

describe('FE-60 NoticesBell', () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('counts the unread notices and names the count for screen readers', async () => {
    const server = setup();
    server.on('GET', '/api/v1/notifications', { body: tray([RESOLVED], 3) });

    renderWithProviders(<NoticesBell />);

    const bell = await screen.findByRole('button', { name: 'Avisos, 3 sin leer' });
    expect(within(bell).getByText('3')).toBeInTheDocument();
  });

  it('draws no counter when everything was read', async () => {
    const server = setup();
    server.on('GET', '/api/v1/notifications', { body: tray([], 0) });

    renderWithProviders(<NoticesBell />);

    const bell = await screen.findByRole('button', { name: 'Avisos, ninguno sin leer' });
    expect(bell.textContent).toBe('');
  });

  it('opens a tray with the latest notices that leads to the page and closes with Escape', async () => {
    const server = setup();
    server.on('GET', '/api/v1/notifications', { body: tray([RESOLVED], 1) });

    const { user } = renderWithProviders(<NoticesBell />);
    await user.click(await screen.findByRole('button', { name: 'Avisos, 1 sin leer' }));

    const dialog = screen.getByRole('dialog', { name: 'Avisos recientes' });
    expect(within(dialog).getByText('Se resolvió tu solicitud de reconocimiento')).toBeInTheDocument();
    expect(within(dialog).getByText('Aprobada')).toBeInTheDocument();
    expect(within(dialog).getByRole('link', { name: 'Ver todos los avisos' })).toHaveAttribute('href', '/avisos');

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('marks all read from the tray and the counter goes away', async () => {
    const server = setup();
    let unread = 2;
    server
      .on('GET', '/api/v1/notifications', () => ({ body: tray([RESOLVED], unread) }))
      .on('POST', '/api/v1/notifications/read-all', () => {
        unread = 0;
        return { status: 204 };
      });

    const { user } = renderWithProviders(<NoticesBell />);
    await user.click(await screen.findByRole('button', { name: 'Avisos, 2 sin leer' }));
    await user.click(screen.getByRole('button', { name: 'Marcar todas' }));

    expect(await screen.findByRole('button', { name: 'Avisos, ninguno sin leer' })).toBeInTheDocument();
  });

  it('opening a notice marks it read, goes to its screen and closes the tray', async () => {
    const server = setup();
    server
      .on('GET', '/api/v1/notifications', { body: tray([RESOLVED], 1) })
      .on('POST', `/api/v1/notifications/${RESOLVED.id}/read`, { status: 204 });

    const { user, location } = renderWithProviders(<NoticesBell />);
    await user.click(await screen.findByRole('button', { name: 'Avisos, 1 sin leer' }));
    await user.click(screen.getByRole('button', { name: /Se resolvió tu solicitud/ }));

    await waitFor(() => expect(location()).toBe('/reconocimiento'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(server.requests('POST')).toHaveLength(1);
  });

  it('asks again every minute while the tab is visible', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const server = setup();
    server.on('GET', '/api/v1/notifications', { body: tray([], 0) });

    renderWithProviders(<NoticesBell />);
    await screen.findByRole('button', { name: 'Avisos, ninguno sin leer' });
    const before = server.requests('GET').filter((request) => request.path === '/api/v1/notifications').length;

    await vi.advanceTimersByTimeAsync(60_000);

    await waitFor(() =>
      expect(server.requests('GET').filter((request) => request.path === '/api/v1/notifications').length).toBe(
        before + 1,
      ),
    );
  });
});
