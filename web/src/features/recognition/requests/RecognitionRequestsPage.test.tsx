import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/shared/api';
import { fakeServer } from '@/test/fakeServer';
import { CARLOS, serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import RecognitionRequestsPage from './RecognitionRequestsPage';

const BRUNO: Schema<'RecognitionQueueItem'> = {
  id: 'd0000000-0000-4000-8000-000000000001',
  student: { id: 's1', name: 'Bruno Salas', studentCode: 'U202300002' },
  status: 'SUBMITTED',
  totalHours: 42,
  sessionsCount: 38,
  averageRating: 4.8,
  submittedAt: '2026-10-20T15:00:00Z',
};

const CARLA: Schema<'RecognitionQueueItem'> = {
  id: 'd0000000-0000-4000-8000-000000000002',
  student: { id: 's2', name: 'Carla Lazo', studentCode: 'U202300022' },
  status: 'UNDER_REVIEW',
  totalHours: 40,
  sessionsCount: 36,
  averageRating: null,
  submittedAt: '2026-10-21T15:00:00Z',
};

function queue(items: Schema<'RecognitionQueueItem'>[], total = items.length): Schema<'RecognitionQueue'> {
  return { items, page: 0, size: 20, total };
}

function setup() {
  const server = fakeServer();
  serveFrame(server, CARLOS);
  signIn('COORDINATOR');
  return server;
}

function asked(server: ReturnType<typeof setup>) {
  return server.requests('GET').filter((request) => request.path === '/api/v1/coordinator/recognition/requests');
}

describe('FE-50 RecognitionRequestsPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('lists what waits with the student, the figures, the day and the state', async () => {
    const server = setup();
    server.on('GET', '/api/v1/coordinator/recognition/requests', { body: queue([BRUNO, CARLA]) });

    renderWithProviders(<RecognitionRequestsPage />, { route: '/coordinador/reconocimiento' });

    const table = await screen.findByRole('table', { name: 'Solicitudes de reconocimiento' });
    const rows = within(table).getAllByRole('row');
    expect(within(rows[1]).getByText('Bruno Salas')).toBeInTheDocument();
    expect(within(rows[1]).getByText('U202300002')).toBeInTheDocument();
    expect(within(rows[1]).getByText('42')).toBeInTheDocument();
    expect(within(rows[1]).getByText('★ 4.8')).toBeInTheDocument();
    expect(within(rows[1]).getByText('20 oct 2026')).toBeInTheDocument();
    expect(within(rows[1]).getByText('Enviada')).toBeInTheDocument();
    expect(within(rows[2]).getByText('Sin calificaciones')).toBeInTheDocument();
    expect(within(rows[2]).getByText('En revisión')).toBeInTheDocument();
    expect(asked(server)[0].query.has('status')).toBe(false);
  });

  it('leads each request to its case', async () => {
    const server = setup();
    server.on('GET', '/api/v1/coordinator/recognition/requests', { body: queue([BRUNO]) });

    renderWithProviders(<RecognitionRequestsPage />, { route: '/coordinador/reconocimiento' });

    const table = await screen.findByRole('table');
    expect(within(table).getByRole('link', { name: 'Revisar la solicitud de Bruno Salas' })).toHaveAttribute(
      'href',
      `/coordinador/reconocimiento/${BRUNO.id}`,
    );
  });

  it('asks for the approved and the rejected ones on their tabs', async () => {
    const server = setup();
    server.on('GET', '/api/v1/coordinator/recognition/requests', (request) =>
      request.query.get('status') === 'APPROVED'
        ? { body: queue([{ ...BRUNO, status: 'APPROVED' }]) }
        : { body: queue([]) },
    );

    const { user } = renderWithProviders(<RecognitionRequestsPage />, { route: '/coordinador/reconocimiento' });
    await screen.findByText('No hay solicitudes por revisar');

    await user.click(screen.getByRole('tab', { name: 'Aprobadas' }));
    const table = await screen.findByRole('table');
    expect(within(table).getByText('Aprobada')).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Rechazadas' }));
    expect(await screen.findByText('No hay solicitudes rechazadas')).toBeInTheDocument();
    expect(asked(server).map((request) => request.query.get('status'))).toEqual([null, 'APPROVED', 'REJECTED']);
  });

  it('moves between pages and starts again at the first one on another tab', async () => {
    const server = setup();
    server.on('GET', '/api/v1/coordinator/recognition/requests', (request) => ({
      body: {
        ...queue([request.query.get('page') === '1' ? CARLA : BRUNO], 25),
        page: Number(request.query.get('page')),
      },
    }));

    const { user } = renderWithProviders(<RecognitionRequestsPage />, { route: '/coordinador/reconocimiento' });
    await screen.findByRole('table');
    await user.click(screen.getByRole('button', { name: 'Siguiente' }));

    await waitFor(() => expect(within(screen.getByRole('table')).getByText('Carla Lazo')).toBeInTheDocument());
    await user.click(screen.getByRole('tab', { name: 'Aprobadas' }));
    await waitFor(() => expect(asked(server).at(-1)?.query.get('page')).toBe('0'));
  });

  it('says there is nothing to review when the queue is empty', async () => {
    const server = setup();
    server.on('GET', '/api/v1/coordinator/recognition/requests', { body: queue([]) });

    renderWithProviders(<RecognitionRequestsPage />, { route: '/coordinador/reconocimiento' });

    expect(await screen.findByText('No hay solicitudes por revisar')).toBeInTheDocument();
    expect(screen.getByText('Cuando un estudiante pida reconocimiento, aparecerá aquí.')).toBeInTheDocument();
  });

  it('offers to retry when the queue cannot be loaded, without the server text', async () => {
    const server = setup();
    server.on('GET', '/api/v1/coordinator/recognition/requests', {
      status: 503,
      body: { status: 503, message: 'down' },
    });

    renderWithProviders(<RecognitionRequestsPage />, { route: '/coordinador/reconocimiento' });

    const alert = await screen.findByRole('alert');
    expect(within(alert).getByRole('button', { name: /Reintentar/ })).toBeInTheDocument();
    expect(screen.queryByText('down')).not.toBeInTheDocument();
  });

  it('says it is not allowed when the person is not a coordinator', async () => {
    const server = setup();
    server.on('GET', '/api/v1/coordinator/recognition/requests', {
      status: 403,
      body: { status: 403, message: 'only a coordinator' },
    });

    renderWithProviders(<RecognitionRequestsPage />, { route: '/coordinador/reconocimiento' });

    expect(await screen.findByText('No tienes permiso para esto')).toBeInTheDocument();
  });
});
