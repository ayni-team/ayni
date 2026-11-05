import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/shared/api';
import { fakeServer } from '@/test/fakeServer';
import { serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import SessionPage from './SessionPage';

const URL = '/api/v1/sessions/x';
const PAST = '2020-01-01T00:00:00Z';
const FUTURE = '2099-01-01T00:00:00Z';

const SESSION: Schema<'Session'> = {
  id: 'x',
  role: 'STUDENT',
  status: 'SCHEDULED',
  studentId: 's1',
  tutorId: 't1',
  counterpart: { id: 't1', name: 'Julián Arce' },
  catalogItem: { name: 'Estadística Aplicada I' },
  scheduledStart: '2026-10-23T21:00:00Z',
  scheduledEnd: '2026-10-23T22:00:00Z',
  joinOpensAt: PAST,
  needDescription: 'Repasar para la PC2',
  presenceCheckAt: '2026-10-23T21:05:00Z',
  presence: null,
};

const LIVE: Schema<'Session'> = {
  ...SESSION,
  status: 'IN_PROGRESS',
  startedAt: PAST,
  studentJoinedAt: PAST,
  tutorJoinedAt: PAST,
};

const CODE = { expiresAt: FUTURE, attemptsLeft: 5, confirmedAt: null };

function setup(session: Schema<'Session'>) {
  const server = fakeServer();
  serveFrame(server);
  signIn('STUDENT');
  server.on('GET', URL, { body: session }).on('POST', `${URL}/join`, { body: { sessionId: 'x', video: null } });
  return server;
}

function open() {
  return renderWithProviders(<SessionPage />, { route: '/sesion/x', path: '/sesion/:id' });
}

describe('FE-20 SessionPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('keeps the door closed before the room opens, and says when it opens', async () => {
    setup({ ...SESSION, joinOpensAt: FUTURE });

    open();

    expect(await screen.findByRole('button', { name: 'Entrar a la sala' })).toBeDisabled();
    expect(screen.getByText(/La sala se abre a las/)).toBeInTheDocument();
  });

  it('enters once the room is open, and shows the arrival the server records', async () => {
    const server = setup(SESSION);
    const { user } = open();

    await user.click(await screen.findByRole('button', { name: 'Entrar a la sala' }));

    expect(server.requests('POST', `${URL}/join`)).toHaveLength(1);
    server.on('GET', URL, { body: { ...SESSION, studentJoinedAt: '2026-10-23T20:52:00Z' } });
    await waitFor(() => expect(server.requests('GET', URL).length).toBeGreaterThan(1));
  });

  it('waits for the other person and promises the credit', async () => {
    setup({ ...SESSION, studentJoinedAt: '2026-10-23T20:52:00Z' });

    open();

    expect(await screen.findByText('Esperando a Julián Arce')).toBeInTheDocument();
    expect(screen.getByText('Entraste 15:52')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Entrar a la sala' })).not.toBeInTheDocument();
  });

  it('asks for the pass again when it comes back to a room it had entered', async () => {
    const server = setup({ ...SESSION, studentJoinedAt: '2026-10-23T20:52:00Z' });

    open();

    await screen.findByText('Esperando a Julián Arce');
    await waitFor(() => expect(server.requests('POST', `${URL}/join`)).toHaveLength(1));
  });

  it('says the tutor did not come and leads to another hour', async () => {
    setup({ ...SESSION, status: 'ABANDONED', absentParticipantIds: ['t1'], studentJoinedAt: '2026-10-23T20:52:00Z' });

    open();

    expect(await screen.findByText('Tu tutor no llegó: te devolvimos el crédito')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Buscar otra hora' })).toHaveAttribute('href', '/buscar');
    expect(screen.getByText('No entró')).toBeInTheDocument();
  });

  it('does not offer to retry a session that does not exist or is not yours', async () => {
    setup(SESSION).on('GET', URL, { status: 404, body: { status: 404, message: 'nope' } });

    const first = open();
    expect(await screen.findByText('No encontramos esta sesión')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
    expect(screen.queryByText('nope')).not.toBeInTheDocument();
    first.unmount();

    setup(SESSION).on('GET', URL, { status: 403, body: { status: 403, message: 'no' } });
    open();
    expect(await screen.findByText('Esta sesión no es tuya')).toBeInTheDocument();
  });

  it('offers to retry when the platform fails', async () => {
    setup(SESSION).on('GET', URL, { status: 500, body: { status: 500, message: 'boom' } });

    open();

    expect(await screen.findByText('No pudimos cargar la sesión')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});

describe('FE-22 the video, inside the room', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('says there is no video when the server has none, and the room goes on', async () => {
    setup(LIVE);

    open();

    expect(await screen.findByText('El video no está disponible')).toBeInTheDocument();
    expect(screen.getByText('Sigue en la pizarra; tu tutor te ve aquí.')).toBeInTheDocument();
    expect(screen.getByRole('region', { name: 'Tu presencia' })).toBeInTheDocument();
  });

  it('opens the video only when asked while waiting for the other person', async () => {
    const { user } = open0();

    await user.click(await screen.findByRole('button', { name: 'Abrir el video' }));

    expect(await screen.findByText('El video no está disponible')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Abrir el video' })).not.toBeInTheDocument();
  });

  function open0() {
    setup({ ...SESSION, studentJoinedAt: '2026-10-23T20:52:00Z' });
    return open();
  }
});

describe('FE-21 presence, inside the room', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function panel() {
    return within(screen.getByRole('region', { name: 'Tu presencia' }));
  }

  it('says when the code will arrive', async () => {
    setup(LIVE);

    open();

    expect(await screen.findByText(/A las 16:05 te enviaremos un código/)).toBeInTheDocument();
  });

  it('confirms a code of six digits, and only then', async () => {
    const server = setup({ ...LIVE, presence: CODE });
    server.on('POST', `${URL}/presence`, { body: { sessionId: 'x', confirmedAt: '2026-10-23T21:07:00Z' } });
    const { user } = open();

    const button = await screen.findByRole('button', { name: 'Confirmar código' });
    await user.type(panel().getByLabelText('Código de 6 dígitos'), '48 19');
    expect(button).toBeDisabled();

    server.on('GET', URL, { body: { ...LIVE, presence: { ...CODE, confirmedAt: '2026-10-23T21:07:00Z' } } });
    await user.type(panel().getByLabelText('Código de 6 dígitos'), '02');
    await user.click(button);

    expect(server.requests('POST', `${URL}/presence`)[0].body).toEqual({ code: '481902' });
    expect(await screen.findByText('Presencia confirmada')).toBeInTheDocument();
    expect(screen.getByText(/Confirmaste a las 16:07/)).toBeInTheDocument();
  });

  it('says a wrong code is wrong, with the attempts the server has left', async () => {
    const server = setup({ ...LIVE, presence: CODE });
    server.on('POST', `${URL}/presence`, () => {
      server.on('GET', URL, { body: { ...LIVE, presence: { ...CODE, attemptsLeft: 3 } } });
      return { status: 422, body: { status: 422, message: 'wrong' } };
    });
    const { user } = open();

    await user.type(await panel0(), '111111');
    await user.click(screen.getByRole('button', { name: 'Confirmar código' }));

    expect(await screen.findByText('Ese código no es correcto')).toBeInTheDocument();
    expect(await screen.findByText('Te quedan 3 intentos.')).toBeInTheDocument();
    expect(screen.queryByText('wrong')).not.toBeInTheDocument();
  });

  it('stops when the attempts are spent, and does not offer another code', async () => {
    setup({ ...LIVE, presence: { ...CODE, attemptsLeft: 0 } });

    open();

    expect(await screen.findByText('Se acabaron los 5 intentos')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirmar código' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Reenviar código' })).toBeDisabled();
  });

  it('offers another code when it expired', async () => {
    setup({ ...LIVE, presence: { ...CODE, expiresAt: PAST } });

    open();

    expect(await screen.findByText('Tu código venció')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reenviar código' })).toBeEnabled();
  });

  it('sends another code once, then makes it wait', async () => {
    const server = setup({ ...LIVE, presence: CODE });
    server.on('POST', `${URL}/presence/resend`, { status: 202 });
    const { user } = open();

    const resend = await screen.findByRole('button', { name: 'Reenviar código' });
    await user.click(resend);

    await waitFor(() => expect(server.requests('POST', `${URL}/presence/resend`)).toHaveLength(1));
    await waitFor(() => expect(resend).toBeDisabled());
  });

  it('explains the wait when the server refuses another code', async () => {
    const server = setup({ ...LIVE, presence: CODE });
    server.on('POST', `${URL}/presence/resend`, { status: 409, body: { status: 409, message: 'too soon' } });
    const { user } = open();

    await user.click(await screen.findByRole('button', { name: 'Reenviar código' }));

    expect(await screen.findByText('Espera un minuto para pedir otro código')).toBeInTheDocument();
    expect(screen.queryByText('too soon')).not.toBeInTheDocument();
  });

  async function panel0() {
    return screen.findByLabelText('Código de 6 dígitos');
  }
});

describe('FE-23 closing, inside the room', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('asks before ending, and then waits for the other person', async () => {
    const server = setup(LIVE);
    server.on('POST', `${URL}/end`, () => {
      server.on('GET', URL, {
        body: { ...LIVE, endConfirmedAt: '2026-10-23T22:00:00Z', closesAt: '2026-10-23T22:15:00Z' },
      });
      return { body: { sessionId: 'x', status: 'IN_PROGRESS', closesAt: '2026-10-23T22:15:00Z' } };
    });
    const { user } = open();

    await user.click(await screen.findByRole('button', { name: /Terminar/ }));
    expect(screen.getByRole('dialog', { name: '¿Terminar la sesión?' })).toBeInTheDocument();
    expect(server.requests('POST', `${URL}/end`)).toHaveLength(0);

    const dialog = within(screen.getByRole('dialog'));
    await user.click(dialog.getByRole('button', { name: 'Terminar sesión' }));

    expect(await screen.findByText('Terminaste la sesión')).toBeInTheDocument();
    expect(screen.getByText(/Si no, se cierra sola a las 17:15/)).toBeInTheDocument();
    expect(server.requests('POST', `${URL}/end`)).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: 'Volver a la sala' }));
    expect(await screen.findByRole('heading', { name: 'Estadística Aplicada I' })).toBeInTheDocument();
  });

  it('shows how it ended and offers to rate once both confirmed', async () => {
    const server = setup({
      ...LIVE,
      status: 'COMPLETED',
      startedAt: '2026-10-23T21:02:00Z',
      endedAt: '2026-10-23T22:00:00Z',
    });
    server.on('GET', `${URL}/rating`, { body: { rated: false } });

    open();

    expect(await screen.findByText('Los dos confirmaron su presencia')).toBeInTheDocument();
    expect(screen.getByText(/Duró 58 minutos \(de 16:02 a 17:00\)/)).toBeInTheDocument();
    expect(await screen.findByText('¿Cómo te fue con Julián Arce?')).toBeInTheDocument();
  });

  it('does not offer to rate an unverified session', async () => {
    const server = setup({ ...LIVE, status: 'UNVERIFIED' });

    open();

    expect(await screen.findByText('La sesión quedó sin verificar')).toBeInTheDocument();
    expect(screen.getByText('Una sesión sin verificar no se califica.')).toBeInTheDocument();
    expect(server.requests('GET', `${URL}/rating`)).toHaveLength(0);
  });
});
