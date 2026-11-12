import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import RecognitionProgressPage from './RecognitionProgressPage';

const PROGRESS = '/api/v1/recognition/progress';
const MINE = '/api/v1/recognition/requests/mine';
const REQUESTS = '/api/v1/recognition/requests';
const COURSE = 'b0000000-0000-4000-8000-000000000101';

const UNDER = {
  earnedHours: 14,
  sessionsCount: 9,
  requiredHours: 20,
  missingHours: 6,
  requirementMet: false,
  canRequest: false,
};
const MET = {
  earnedHours: 22,
  sessionsCount: 12,
  requiredHours: 20,
  missingHours: 0,
  requirementMet: true,
  canRequest: true,
};

const REJECTED = {
  id: 'd0000000-0000-4000-8000-000000000001',
  status: 'REJECTED',
  totalHours: 20,
  sessionsCount: 1,
  submittedAt: '2026-10-21T02:00:00Z',
  reviewedAt: '2026-10-23T15:00:00Z',
  decisionReason: 'Faltan sesiones con presencia verificada',
  sessions: [
    {
      sessionId: 'e0000000-0000-4000-8000-000000000001',
      catalogItemId: COURSE,
      hours: 2,
      startedAt: '2026-10-05T12:55:00Z',
      endedAt: '2026-10-05T14:57:00Z',
    },
  ],
};

function setup() {
  const server = fakeServer();
  serveFrame(server);
  signIn('STUDENT');
  server.on('GET', `/api/v1/catalog/${COURSE}`, { body: { id: COURSE, name: 'Cálculo I' } });
  return server;
}

function render() {
  return renderWithProviders(<RecognitionProgressPage />, { route: '/reconocimiento' });
}

describe('FE-34 RecognitionProgressPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows a student under the requirement the bar, what is missing and a disabled button', async () => {
    const server = setup();
    server.on('GET', PROGRESS, { body: UNDER }).on('GET', MINE, { body: [] });

    render();

    expect(await screen.findByText('14 de 20 horas')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: 'Horas enseñadas' })).toHaveAttribute('aria-valuenow', '70');
    expect(screen.getByText('Te faltan 6 horas')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Solicitar reconocimiento' })).toBeDisabled();
    expect(await screen.findByText('Todavía no solicitaste reconocimiento')).toBeInTheDocument();
  });

  it('says so when the university has not opened recognition', async () => {
    const server = setup();
    server
      .on('GET', PROGRESS, { body: { ...UNDER, requiredHours: null, missingHours: null } })
      .on('GET', MINE, { body: [] });

    render();

    expect(await screen.findByText('Tu universidad todavía no habilitó el reconocimiento')).toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Solicitar reconocimiento' })).toBeDisabled();
  });

  it('lets a student over the requirement submit and then shows the request pending', async () => {
    const server = setup();
    let sent = false;
    server
      .on('GET', PROGRESS, () => ({
        body: sent ? { ...UNDER, earnedHours: 0, sessionsCount: 0, missingHours: 20 } : MET,
      }))
      .on('GET', MINE, () => ({
        body: sent
          ? [
              {
                id: 'new',
                status: 'SUBMITTED',
                totalHours: 22,
                sessionsCount: 12,
                submittedAt: '2026-10-31T15:00:00Z',
                sessions: [],
              },
            ]
          : [],
      }))
      .on('POST', REQUESTS, () => {
        sent = true;
        return { status: 201, body: { id: 'new', status: 'SUBMITTED' } };
      });

    const { user } = render();
    const button = await screen.findByRole('button', { name: 'Solicitar reconocimiento' });
    expect(button).toBeEnabled();
    await user.click(button);

    expect(await screen.findByText('Enviada')).toBeInTheDocument();
    expect(screen.getByText(/ya están en tu solicitud en revisión/)).toBeInTheDocument();
    expect(screen.queryByText(/Te faltan/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Solicitar reconocimiento' })).toBeDisabled();
    expect(server.requests('POST', REQUESTS)).toHaveLength(1);
  });

  it('says how many hours are missing when the server refuses the request (409)', async () => {
    const server = setup();
    server
      .on('GET', PROGRESS, { body: MET })
      .on('GET', MINE, { body: [] })
      .on('POST', REQUESTS, {
        status: 409,
        body: { status: 409, message: '4 more hours are needed', missingHours: 4 },
      });

    const { user } = render();
    await user.click(await screen.findByRole('button', { name: 'Solicitar reconocimiento' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Te faltan 4 horas');
    expect(screen.queryByText(/more hours are needed/)).not.toBeInTheDocument();
  });

  it('lists a decided request with its reason and the sessions that back it', async () => {
    const server = setup();
    server.on('GET', PROGRESS, { body: UNDER }).on('GET', MINE, { body: [REJECTED] });

    const { user } = render();

    const item = (await screen.findByText('Rechazada')).closest('li')!;
    expect(within(item).getByText('Enviada el 20 oct 2026 · 20 horas · 1 sesión')).toBeInTheDocument();
    expect(within(item).getByText('Decidida el 23 oct 2026')).toBeInTheDocument();
    expect(within(item).getByText('Faltan sesiones con presencia verificada')).toBeInTheDocument();
    await user.click(within(item).getByText('Ver la sesión que la respalda'));
    expect(await within(item).findByText('Cálculo I')).toBeInTheDocument();
    expect(within(item).getByText('Lun 5 oct · 07:55 – 09:57 · 2 horas')).toBeInTheDocument();
  });

  it('offers to retry when the progress cannot be loaded', async () => {
    const server = setup();
    server.on('GET', PROGRESS, { status: 500, body: { status: 500 } }).on('GET', MINE, { body: [] });

    render();

    expect(await screen.findByRole('button', { name: /Reintentar/ })).toBeInTheDocument();
  });
});
