import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import { RatingPanel } from './RatingPanel';

const URL = '/api/v1/sessions/x/rating';

function setup() {
  const server = fakeServer();
  serveFrame(server);
  signIn('STUDENT');
  return server;
}

function asStudent() {
  return renderWithProviders(<RatingPanel sessionId="x" reader="STUDENT" otherName="Julián Arce" />);
}

describe('FE-23 RatingPanel', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('asks for the stars first, then sends stars, tags and comment, once', async () => {
    const server = setup().on('GET', URL, { body: { rated: false } });
    server.on('POST', URL, () => {
      server.on('GET', URL, { body: { rated: true, stars: 4, tags: ['CLEAR'] } });
      return { status: 201, body: { id: 'r1' } };
    });
    const { user } = asStudent();

    const send = await screen.findByRole('button', { name: 'Enviar calificación' });
    expect(send).toBeDisabled();

    await user.click(screen.getByRole('radio', { name: '4 estrellas' }));
    await user.click(screen.getByRole('button', { name: 'Explica con claridad' }));
    await user.type(screen.getByLabelText('Comentario (opcional)'), ' Me ayudó ');
    await user.dblClick(send);

    expect(await screen.findByText('Gracias por calificar')).toBeInTheDocument();
    expect(screen.getByText('Calificaste con 4 estrellas · Explica con claridad.')).toBeInTheDocument();
    expect(server.requests('POST', URL)).toHaveLength(1);
    expect(server.requests('POST', URL)[0].body).toEqual({ stars: 4, tags: ['CLEAR'], comment: 'Me ayudó' });
  });

  it('shows what is registered instead of the form', async () => {
    setup().on('GET', URL, { body: { rated: true, stars: 5, tags: [] } });

    asStudent();

    expect(await screen.findByText('Calificaste con 5 estrellas.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Enviar calificación' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Repasar la sesión' })).toHaveAttribute('href', '/sesion/x/repaso');
  });

  it('shows what is there when another tab already rated it (409)', async () => {
    const server = setup().on('GET', URL, { body: { rated: false } });
    server.on('POST', URL, () => {
      server.on('GET', URL, { body: { rated: true, stars: 3 } });
      return { status: 409, body: { status: 409, message: 'already rated' } };
    });
    const { user } = asStudent();

    await user.click(await screen.findByRole('radio', { name: '5 estrellas' }));
    await user.click(screen.getByRole('button', { name: 'Enviar calificación' }));

    expect(await screen.findByText('Ya calificaste esta sesión')).toBeInTheDocument();
    expect(screen.getByText('Calificaste con 3 estrellas.')).toBeInTheDocument();
    expect(screen.queryByText('already rated')).not.toBeInTheDocument();
  });

  it('asks the tutor three facts and sends all three', async () => {
    const server = setup().on('GET', URL, { body: { rated: false } });
    server.on('POST', URL, () => {
      server.on('GET', URL, { body: { rated: true, wasPunctual: true, connectionOk: false, sessionFlowed: false } });
      return { status: 201, body: { id: 'r1' } };
    });
    const { user } = renderWithProviders(<RatingPanel sessionId="x" reader="TUTOR" otherName="Ana Torres" />);

    await user.click(await screen.findByRole('checkbox', { name: 'Fue puntual' }));
    await user.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(server.requests('POST', URL)[0].body).toEqual({
      wasPunctual: true,
      connectionOk: false,
      sessionFlowed: false,
    });
    expect(await screen.findByText('La conexión falló')).toBeInTheDocument();
  });

  it('waits for the window to open when the session has just closed', async () => {
    const server = setup().on('GET', URL, { status: 404, body: { status: 404, message: 'no window' } });

    asStudent();

    expect(await screen.findByRole('status', { name: 'Preparando la calificación' })).toBeInTheDocument();
    server.on('GET', URL, { body: { rated: false } });
    await waitFor(() => expect(screen.getByText('¿Cómo te fue con Julián Arce?')).toBeInTheDocument(), {
      timeout: 5000,
    });
  });

  it('can be left for later and taken up again', async () => {
    setup().on('GET', URL, { body: { rated: false } });
    const { user } = asStudent();

    await user.click(await screen.findByRole('button', { name: 'Ahora no' }));
    await user.click(screen.getByRole('button', { name: 'Calificar ahora' }));

    expect(screen.getByText('¿Cómo te fue con Julián Arce?')).toBeInTheDocument();
  });
});
