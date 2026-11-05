import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import SessionReviewPage from './SessionReviewPage';

const URL = '/api/v1/sessions/x/review';

const REVIEW = {
  sessionId: 'x',
  status: 'COMPLETED',
  statusNote: 'English the screen never shows',
  catalogItem: { name: 'Diseño de Base de Datos' },
  counterpart: { id: 't', name: 'Bruno Salas' },
  scheduledStart: '2026-10-23T21:00:00Z',
  scheduledEnd: '2026-10-23T22:00:00Z',
  bookedMinutes: 60,
  realDurationMinutes: 58,
  needDescription: 'Repasar normalización',
  whiteboard: {
    hasActivity: true,
    live: false,
    elements: [{ id: 't1', type: 'text', data: { x: 40, y: 40, text: 'Resumen de la sesión' } }],
  },
};

function setup(reply: Parameters<ReturnType<typeof fakeServer>['on']>[2]) {
  const server = fakeServer();
  serveFrame(server);
  signIn('STUDENT');
  server.on('GET', URL, reply);
}

function open() {
  renderWithProviders(<SessionReviewPage />, { route: '/sesion/x/repaso', path: '/sesion/:id/repaso' });
}

describe('FE-24 SessionReviewPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows what happened, the duration, the need and the board as it was left', async () => {
    setup({ body: REVIEW });

    open();

    expect(await screen.findByRole('heading', { name: 'Diseño de Base de Datos' })).toBeInTheDocument();
    expect(screen.getByText('La sesión se realizó y los dos confirmaron su presencia.')).toBeInTheDocument();
    expect(screen.getByText('Duró 58 minutos de los 60 reservados')).toBeInTheDocument();
    expect(screen.getByText('“Repasar normalización”')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Resumen de la sesión/ })).toBeInTheDocument();
    expect(screen.queryByText(/English/)).not.toBeInTheDocument();
  });

  it('says nobody drew on a board with nothing', async () => {
    setup({ body: { ...REVIEW, whiteboard: { hasActivity: false, elements: [] } } });

    open();

    expect(await screen.findByText('En esta sesión nadie dibujó en la pizarra.')).toBeInTheDocument();
  });

  it('says why a session did not take place instead of an empty workspace', async () => {
    setup({
      body: {
        ...REVIEW,
        status: 'ABANDONED',
        realDurationMinutes: null,
        whiteboard: null,
        absentParticipantIds: ['t'],
      },
    });

    open();

    expect(await screen.findByText('Bruno Salas no entró a tiempo.')).toBeInTheDocument();
    expect(screen.getByText('No hubo sala, así que tampoco hay pizarra.')).toBeInTheDocument();
    expect(screen.queryByText('La pizarra')).not.toBeInTheDocument();
    expect(screen.queryByText(/^Duró/)).not.toBeInTheDocument();
  });

  it('leads to the room of a session that is not over', async () => {
    setup({ body: { ...REVIEW, status: 'IN_PROGRESS', realDurationMinutes: null, whiteboard: null } });

    open();

    expect(await screen.findByRole('link', { name: 'Ir a la sala' })).toHaveAttribute('href', '/sesion/x');
  });

  it('does not offer to retry what does not exist or is not yours, and retries the rest', async () => {
    setup({ status: 404, body: { status: 404, message: 'nope' } });
    open();
    expect(await screen.findByText('No encontramos esta sesión')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
    expect(screen.queryByText('nope')).not.toBeInTheDocument();
  });

  it('offers to retry when the platform fails', async () => {
    setup({ status: 500, body: { status: 500, message: 'boom' } });
    open();
    expect(await screen.findByText('No pudimos cargar el repaso')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
