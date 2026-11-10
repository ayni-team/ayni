import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import PracticePage from './PracticePage';

const OPEN = '/api/v1/practice-rooms';
const ROOM = { sessionId: 'abc', path: '/sesion/abc' };

function setup() {
  const server = fakeServer();
  serveFrame(server);
  signIn('STUDENT');
  return server;
}

function open(route = '/practica') {
  return renderWithProviders(<PracticePage />, { route, path: '/practica' });
}

describe('practice rooms · /practica', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('opens a room once and shows its whole link, to copy or to enter', async () => {
    const server = setup().on('POST', OPEN, { status: 201, body: ROOM });
    const { user } = open();

    await user.click(await screen.findByRole('button', { name: 'Crear sala de práctica' }));

    const link = await screen.findByLabelText('Enlace de la sala');
    expect(link).toHaveValue(`${window.location.origin}/sesion/abc`);
    expect(server.requests('POST', OPEN)).toHaveLength(1);
    expect(screen.getByRole('link', { name: 'Entrar' })).toHaveAttribute('href', '/sesion/abc');

    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    await user.click(screen.getByRole('button', { name: 'Copiar enlace' }));
    expect(writeText).toHaveBeenCalledWith(`${window.location.origin}/sesion/abc`);
  });

  it('says practice rooms are off when the backend does not have the route', async () => {
    setup().on('POST', OPEN, { status: 404, body: { status: 404, message: 'Practice rooms are not enabled' } });
    const { user } = open();

    await user.click(await screen.findByRole('button', { name: 'Crear sala de práctica' }));

    expect(await screen.findByText('Las salas de práctica no están activas')).toBeInTheDocument();
    expect(screen.queryByLabelText('Enlace de la sala')).not.toBeInTheDocument();
    expect(screen.queryByText('Practice rooms are not enabled')).not.toBeInTheDocument();
  });

  it('says there are too many rooms open', async () => {
    setup().on('POST', OPEN, { status: 409, body: { status: 409, message: 'full' } });
    const { user } = open();

    await user.click(await screen.findByRole('button', { name: 'Crear sala de práctica' }));

    expect(await screen.findByText('Hay demasiadas salas abiertas')).toBeInTheDocument();
  });
});
