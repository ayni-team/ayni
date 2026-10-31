import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AYNI_COLORS } from '@/shared/brand';
import { fakeServer } from '@/test/fakeServer';
import { UPC, USIL_IDENTITY } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import SignInPage from './SignInPage';

/**
 * FE-01 · /ingresar
 *
 * Feature tests for:
 * - university preview by domain,
 * - university branding,
 * - non-affiliated domains,
 * - access-link request,
 * - resend waiting period,
 * - returning to another email.
 */
function serveUniversities() {
  return fakeServer().on('GET', '/api/v1/access/university', ({ query }) => {
    const domain = query.get('domain');

    if (domain === 'upc.edu.pe') {
      return {
        body: UPC,
      };
    }

    if (domain === 'usil.edu.pe') {
      return {
        body: USIL_IDENTITY,
      };
    }

    return {
      status: 404,
      body: {
        status: 404,
        message: 'The institution is not affiliated with Ayni',
      },
    };
  });
}

const brand = () => document.documentElement.style.getPropertyValue('--brand-primary');

describe('FE-01 sign in', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("paints UPC's name, logo and colours before signing in", async () => {
    serveUniversities();

    const { user } = renderWithProviders(<SignInPage />, {
      route: '/ingresar',
    });

    await user.type(
      screen.getByRole('textbox', {
        name: 'Correo institucional',
      }),
      'u202400001@upc.edu.pe',
    );

    expect(await screen.findByText('Universidad Peruana de Ciencias Aplicadas')).toBeInTheDocument();

    expect(
      screen.getByRole('img', {
        name: /Logo de Universidad Peruana/,
      }),
    ).toHaveAttribute('src', '/logos/upc.png');

    expect(brand()).toBe('#E42322');

    expect(
      screen.getByRole('button', {
        name: 'Enviarme el enlace',
      }),
    ).toBeEnabled();
  });

  it('switches to USIL when the address is of USIL', async () => {
    serveUniversities();

    const { user } = renderWithProviders(<SignInPage />, {
      route: '/ingresar',
    });

    const email = screen.getByRole('textbox', {
      name: 'Correo institucional',
    });

    await user.type(email, 'u202400001@upc.edu.pe');

    await waitFor(() => {
      expect(brand()).toBe('#E42322');
    });

    await user.clear(email);

    await user.type(email, 'lucia.paredes@usil.edu.pe');

    expect(await screen.findByText('Universidad San Ignacio de Loyola')).toBeInTheDocument();

    await waitFor(() => {
      expect(brand()).toBe('#07256D');
    });
  });

  it('only hints at a domain of no university, without blocking the submit', async () => {
    const server = serveUniversities();
    const { user } = renderWithProviders(<SignInPage />, { route: '/ingresar' });
    const email = screen.getByRole('textbox', { name: 'Correo institucional' });

    await user.type(email, 'alguien@inventada.pe');

    expect(
      await screen.findByText(/No encontramos una universidad afiliada a Ayni con ese dominio/),
    ).toBeInTheDocument();
    expect(screen.queryByText(/not affiliated/)).not.toBeInTheDocument();
    expect(email).not.toHaveAttribute('aria-invalid');
    expect(screen.getByRole('button', { name: 'Enviarme el enlace' })).toBeEnabled();
    expect(brand()).toBe(AYNI_COLORS.primaryColor);
    expect(server.requests('POST')).toHaveLength(0);
  });

  it('lets the platform administrator ask for a link with an address of no university', async () => {
    const server = serveUniversities().on('POST', '/api/v1/access/request', { status: 202 });
    const { user } = renderWithProviders(<SignInPage />, { route: '/ingresar' });

    await user.type(screen.getByRole('textbox', { name: 'Correo institucional' }), 'admin@example.org');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Enviarme el enlace' })).toBeEnabled());
    await user.click(screen.getByRole('button', { name: 'Enviarme el enlace' }));

    expect(
      await screen.findByText('Te enviamos un enlace a admin@example.org. Vence en 10 minutos.'),
    ).toBeInTheDocument();
    expect(server.requests('POST', '/api/v1/access/request')[0].body).toEqual({ email: 'admin@example.org' });
  });

  it('says the university is not affiliated when the backend refuses an address of no university', async () => {
    serveUniversities().on('POST', '/api/v1/access/request', {
      status: 400,
      body: { status: 400, message: 'The institution is not affiliated with Ayni' },
    });
    const { user } = renderWithProviders(<SignInPage />, { route: '/ingresar' });
    const email = screen.getByRole('textbox', { name: 'Correo institucional' });

    await user.type(email, 'alguien@inventada.pe');
    await waitFor(() => expect(screen.getByRole('button', { name: 'Enviarme el enlace' })).toBeEnabled());
    await user.click(screen.getByRole('button', { name: 'Enviarme el enlace' }));

    expect(await screen.findByText('Tu universidad todavía no está afiliada a Ayni')).toBeInTheDocument();
    expect(screen.queryByText(/not affiliated/)).not.toBeInTheDocument();
    expect(email).toHaveAttribute('aria-invalid', 'true');

    await user.type(email, 'x');
    expect(screen.queryByText('Tu universidad todavía no está afiliada a Ayni')).not.toBeInTheDocument();
  });

  it('keeps the submit disabled until the address looks like one', async () => {
    const server = serveUniversities();
    const { user } = renderWithProviders(<SignInPage />, { route: '/ingresar' });

    await user.type(screen.getByRole('textbox', { name: 'Correo institucional' }), 'alguien@');

    expect(screen.getByRole('button', { name: 'Enviarme el enlace' })).toBeDisabled();
    expect(server.requests()).toHaveLength(0);
  });

  it('sends only the domain to look the university up, never the address', async () => {
    const server = serveUniversities();

    const { user } = renderWithProviders(<SignInPage />, {
      route: '/ingresar',
    });

    await user.type(
      screen.getByRole('textbox', {
        name: 'Correo institucional',
      }),
      'U202400001@UPC.edu.pe',
    );

    await screen.findByText('Universidad Peruana de Ciencias Aplicadas');

    const lookups = server.requests('GET', '/api/v1/access/university');

    expect(lookups).toHaveLength(1);

    expect(lookups[0].query.get('domain')).toBe('upc.edu.pe');

    expect(lookups[0].query.has('email')).toBe(false);

    expect(lookups[0].query.toString()).not.toContain('202400001');
  });

  it('asks for the link and says where it went', async () => {
    const server = serveUniversities().on('POST', '/api/v1/access/request', {
      status: 202,
    });

    const { user } = renderWithProviders(<SignInPage />, {
      route: '/ingresar',
    });

    await user.type(
      screen.getByRole('textbox', {
        name: 'Correo institucional',
      }),
      'u202400001@upc.edu.pe',
    );

    await waitFor(() => {
      expect(
        screen.getByRole('button', {
          name: 'Enviarme el enlace',
        }),
      ).toBeEnabled();
    });

    await user.click(
      screen.getByRole('button', {
        name: 'Enviarme el enlace',
      }),
    );

    expect(
      await screen.findByText('Te enviamos un enlace a u202400001@upc.edu.pe. Vence en 10 minutos.'),
    ).toBeInTheDocument();

    expect(server.requests('POST', '/api/v1/access/request')[0].body).toEqual({
      email: 'u202400001@upc.edu.pe',
    });
  });

  it('shows the resend countdown after the access link is sent', async () => {
    serveUniversities().on('POST', '/api/v1/access/request', {
      status: 202,
    });

    const { user } = renderWithProviders(<SignInPage />, {
      route: '/ingresar',
    });

    await user.type(
      screen.getByRole('textbox', {
        name: 'Correo institucional',
      }),
      'u202400001@upc.edu.pe',
    );

    await waitFor(() => {
      expect(
        screen.getByRole('button', {
          name: 'Enviarme el enlace',
        }),
      ).toBeEnabled();
    });

    await user.click(
      screen.getByRole('button', {
        name: 'Enviarme el enlace',
      }),
    );

    expect(
      await screen.findByRole('heading', {
        name: 'Revisa tu correo',
      }),
    ).toBeInTheDocument();

    const resendButton = await screen.findByRole('button', {
      name: /Reenviar en 0:\d{2}/,
    });

    expect(resendButton).toBeDisabled();

    expect(
      screen.getByRole('button', {
        name: 'Usar otro correo',
      }),
    ).toBeEnabled();

    expect(screen.getByText('Reenviar se habilita 30 segundos después del envío.')).toBeInTheDocument();
  });

  it('lets the user return and enter another email after the link was sent', async () => {
    serveUniversities().on('POST', '/api/v1/access/request', {
      status: 202,
    });

    const { user } = renderWithProviders(<SignInPage />, {
      route: '/ingresar',
    });

    await user.type(
      screen.getByRole('textbox', {
        name: 'Correo institucional',
      }),
      'u202400001@upc.edu.pe',
    );

    await waitFor(() => {
      expect(
        screen.getByRole('button', {
          name: 'Enviarme el enlace',
        }),
      ).toBeEnabled();
    });

    await user.click(
      screen.getByRole('button', {
        name: 'Enviarme el enlace',
      }),
    );

    await screen.findByRole('heading', {
      name: 'Revisa tu correo',
    });

    await user.click(
      screen.getByRole('button', {
        name: 'Usar otro correo',
      }),
    );

    expect(
      screen.getByRole('heading', {
        name: 'Ingresa a Ayni',
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('textbox', {
        name: 'Correo institucional',
      }),
    ).toHaveValue('');

    expect(
      screen.getByRole('button', {
        name: 'Enviarme el enlace',
      }),
    ).toBeDisabled();
  });
});
