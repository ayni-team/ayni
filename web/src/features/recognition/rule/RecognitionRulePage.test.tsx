import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { CARLOS, serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import RecognitionRulePage from './RecognitionRulePage';

const RULE = '/api/v1/coordinator/recognition/rule';

function setup() {
  const server = fakeServer();
  serveFrame(server, CARLOS);
  signIn('COORDINATOR');
  return server;
}

function puts(server: ReturnType<typeof setup>) {
  return server.requests('PUT').filter((request) => request.path === RULE);
}

describe('FE-52 RecognitionRulePage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the hours the university asks for and since when', async () => {
    const server = setup();
    server.on('GET', RULE, { body: { minimumHours: 20, validFrom: '2026-10-20' } });

    renderWithProviders(<RecognitionRulePage />, { route: '/coordinador/regla' });

    expect(await screen.findByText('20 horas')).toBeInTheDocument();
    expect(screen.getByText(/desde el 20 de octubre de 2026/)).toBeInTheDocument();
  });

  it('says the rule is not defined yet when the server has none (404)', async () => {
    const server = setup();
    server.on('GET', RULE, { status: 404, body: { status: 404, message: 'no rule in force' } });

    renderWithProviders(<RecognitionRulePage />, { route: '/coordinador/regla' });

    expect(await screen.findByText('Todavía no definiste cuántas horas pide tu universidad')).toBeInTheDocument();
    expect(screen.queryByText('no rule in force')).not.toBeInTheDocument();
  });

  it('sets a new rule from today when no day is chosen, and shows it', async () => {
    const server = setup();
    let current: { minimumHours: number; validFrom: string } | undefined;
    server
      .on('GET', RULE, () =>
        current ? { body: current } : { status: 404, body: { status: 404, message: 'no rule in force' } },
      )
      .on('PUT', RULE, (request) => {
        current = { minimumHours: (request.body as { minimumHours: number }).minimumHours, validFrom: '2026-10-31' };
        return { body: current };
      });

    const { user } = renderWithProviders(<RecognitionRulePage />, { route: '/coordinador/regla' });
    await screen.findByText('Todavía no definiste cuántas horas pide tu universidad');
    await user.type(screen.getByLabelText(/Horas de sesiones verificadas/), '20');
    await user.click(screen.getByRole('button', { name: 'Guardar regla' }));

    expect(await screen.findByText('Tu universidad pide ahora 20 horas.')).toBeInTheDocument();
    expect(await screen.findByText('20 horas')).toBeInTheDocument();
    expect(puts(server)[0].body).toEqual({ minimumHours: 20, validFrom: null });
  });

  it('sends the start day when one is chosen and says the current rule runs until then', async () => {
    const server = setup();
    server
      .on('GET', RULE, { body: { minimumHours: 20, validFrom: '2026-10-20' } })
      .on('PUT', RULE, { body: { minimumHours: 30, validFrom: '2099-11-15' } });

    const { user } = renderWithProviders(<RecognitionRulePage />, { route: '/coordinador/regla' });
    await screen.findByText('20 horas');
    await user.type(screen.getByLabelText(/Horas de sesiones verificadas/), '30');
    await user.type(screen.getByLabelText(/Desde/), '2099-11-15');
    await user.click(screen.getByRole('button', { name: 'Guardar regla' }));

    expect(
      await screen.findByText(
        'Desde el 15 de noviembre de 2099 tu universidad pedirá 30 horas. Hasta entonces sigue la regla actual.',
      ),
    ).toBeInTheDocument();
    expect(puts(server)[0].body).toEqual({ minimumHours: 30, validFrom: '2099-11-15' });
  });

  it('refuses no hours, zero hours and a day that already passed, and sends nothing', async () => {
    const server = setup();
    server.on('GET', RULE, { body: { minimumHours: 20, validFrom: '2026-10-20' } });

    const { user } = renderWithProviders(<RecognitionRulePage />, { route: '/coordinador/regla' });
    await screen.findByText('20 horas');
    await user.click(screen.getByRole('button', { name: 'Guardar regla' }));
    expect(await screen.findByText('Escribe cuántas horas pide tu universidad')).toBeInTheDocument();

    await user.type(screen.getByLabelText(/Horas de sesiones verificadas/), '0');
    await user.type(screen.getByLabelText(/Desde/), '2020-01-01');
    await user.click(screen.getByRole('button', { name: 'Guardar regla' }));

    expect(await screen.findByText('Pide al menos 1 hora')).toBeInTheDocument();
    expect(screen.getByText('Elige hoy o un día que no haya pasado')).toBeInTheDocument();
    expect(puts(server)).toHaveLength(0);
  });

  it('says it is not allowed when the coordinator cannot change the rule (403)', async () => {
    const server = setup();
    server
      .on('GET', RULE, { body: { minimumHours: 20, validFrom: '2026-10-20' } })
      .on('PUT', RULE, { status: 403, body: { status: 403, message: 'restricted coordinator' } });

    const { user } = renderWithProviders(<RecognitionRulePage />, { route: '/coordinador/regla' });
    await screen.findByText('20 horas');
    await user.type(screen.getByLabelText(/Horas de sesiones verificadas/), '25');
    await user.click(screen.getByRole('button', { name: 'Guardar regla' }));

    expect(await screen.findByText('No tienes permiso para esto')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByText('restricted coordinator')).not.toBeInTheDocument());
  });
});
