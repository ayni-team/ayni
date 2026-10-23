import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { UPC, USIL_IDENTITY } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import SignInPage from './SignInPage';

/**
 * The example of a feature test: the screen is rendered as it is, and the server is faked at the
 * `fetch` level with what each request answers.
 */
function serveUniversities() {
  return fakeServer().on('GET', '/api/v1/access/university', ({ query }) => {
    const domain = query.get('email')?.split('@')[1];
    if (domain === 'upc.edu.pe') return { body: UPC };
    if (domain === 'usil.edu.pe') return { body: USIL_IDENTITY };
    return { status: 404, body: { status: 404, message: 'The institution is not affiliated with Ayni' } };
  });
}

const brand = () => document.documentElement.style.getPropertyValue('--brand-primary');

describe('FE-01 sign in', () => {
  afterEach(() => vi.unstubAllGlobals());

  it("paints UPC's name, logo and colours before signing in", async () => {
    serveUniversities();
    const { user } = renderWithProviders(<SignInPage />, { route: '/ingresar' });

    await user.type(screen.getByRole('textbox', { name: 'Correo institucional' }), 'u202400001@upc.edu.pe');

    expect(await screen.findByText('Universidad Peruana de Ciencias Aplicadas')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Logo de Universidad Peruana/ })).toHaveAttribute('src', '/logos/upc.png');
    expect(brand()).toBe('#E42322');
    expect(screen.getByRole('button', { name: 'Enviarme el enlace' })).toBeEnabled();
  });

  it('switches to USIL when the address is of USIL', async () => {
    serveUniversities();
    const { user } = renderWithProviders(<SignInPage />, { route: '/ingresar' });
    const email = screen.getByRole('textbox', { name: 'Correo institucional' });

    await user.type(email, 'u202400001@upc.edu.pe');
    await waitFor(() => expect(brand()).toBe('#E42322'));
    await user.clear(email);
    await user.type(email, 'lucia.paredes@usil.edu.pe');

    expect(await screen.findByText('Universidad San Ignacio de Loyola')).toBeInTheDocument();
    await waitFor(() => expect(brand()).toBe('#07256D'));
  });

  it('refuses a made-up domain and keeps the submit disabled', async () => {
    const server = serveUniversities();
    const { user } = renderWithProviders(<SignInPage />, { route: '/ingresar' });

    await user.type(screen.getByRole('textbox', { name: 'Correo institucional' }), 'alguien@inventada.pe');

    expect(await screen.findByText('Tu universidad todavía no está afiliada a Ayni')).toBeInTheDocument();
    expect(screen.queryByText(/not affiliated/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Enviarme el enlace' })).toBeDisabled();
    expect(server.requests('POST')).toHaveLength(0);
  });

  it('asks for the link and says where it went', async () => {
    const server = serveUniversities().on('POST', '/api/v1/access/request', { status: 202 });
    const { user } = renderWithProviders(<SignInPage />, { route: '/ingresar' });

    await user.type(screen.getByRole('textbox', { name: 'Correo institucional' }), 'u202400001@upc.edu.pe');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Enviarme el enlace' })).toBeEnabled());
    await user.click(screen.getByRole('button', { name: 'Enviarme el enlace' }));

    expect(await screen.findByText('Te enviamos un enlace a u202400001@upc.edu.pe. Vence en 10 minutos.')).toBeInTheDocument();
    expect(server.requests('POST', '/api/v1/access/request')[0].body).toEqual({ email: 'u202400001@upc.edu.pe' });
  });
});
