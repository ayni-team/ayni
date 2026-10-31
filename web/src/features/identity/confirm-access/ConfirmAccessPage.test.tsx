import { screen, waitFor } from '@testing-library/react';
import { StrictMode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/shared/api';
import { currentSession } from '@/shared/session';
import { ANA } from '@/test/fixtures';
import { fakeServer } from '@/test/fakeServer';
import { renderWithProviders } from '@/test/render';
import ConfirmAccessPage from './ConfirmAccessPage';

function opened(role: 'STUDENT' | 'COORDINATOR' | 'PLATFORM_ADMIN', token = 'opened'): Schema<'OpenedSession'> {
  return {
    sessionToken: token,

    tenantId: role === 'PLATFORM_ADMIN' ? undefined : 'UPC',

    userId: '11111111-1111-4111-8111-111111111111',

    role,

    expiresAt: '2099-01-01T00:00:00Z',
  };
}

describe('FE-02 confirm the link', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('spends the token once under StrictMode and sends a coordinator directly to recognition', async () => {
    const server = fakeServer().on('POST', '/api/v1/access/confirm', {
      body: opened('COORDINATOR'),
    });

    const { location } = renderWithProviders(
      <StrictMode>
        <ConfirmAccessPage />
      </StrictMode>,
      {
        route: '/access/confirm?token=abc',

        path: '/access/confirm',
      },
    );

    await waitFor(() => expect(location()).toBe('/coordinador/reconocimiento'));

    expect(server.requests('POST', '/api/v1/access/confirm')).toHaveLength(1);

    expect(server.requests('POST', '/api/v1/access/confirm')[0].body).toEqual({
      token: 'abc',
    });

    expect(server.requests('GET', '/api/v1/me')).toHaveLength(0);

    expect(currentSession()?.role).toBe('COORDINATOR');
  });

  it('sends a platform administrator directly to admin without loading a university profile', async () => {
    const server = fakeServer().on('POST', '/api/v1/access/confirm', {
      body: opened('PLATFORM_ADMIN'),
    });

    const { location } = renderWithProviders(<ConfirmAccessPage />, {
      route: '/access/confirm?token=admin-link',

      path: '/access/confirm',
    });

    await waitFor(() => expect(location()).toBe('/admin'));

    expect(server.requests('GET', '/api/v1/me')).toHaveLength(0);

    expect(currentSession()?.role).toBe('PLATFORM_ADMIN');
  });

  it('sends a student with unfinished onboarding to onboarding', async () => {
    const server = fakeServer()
      .on('POST', '/api/v1/access/confirm', {
        body: opened('STUDENT'),
      })
      .on('GET', '/api/v1/me', {
        body: {
          ...ANA,
          onboardingStep: 'ACADEMIC_RECORD',
        },
      });

    const { location } = renderWithProviders(<ConfirmAccessPage />, {
      route: '/access/confirm?token=student-link',

      path: '/access/confirm',
    });

    await waitFor(() => expect(location()).toBe('/onboarding'));

    expect(server.requests('POST', '/api/v1/access/confirm')).toHaveLength(1);

    expect(server.requests('GET', '/api/v1/me')).toHaveLength(1);

    expect(server.requests('GET', '/api/v1/me')[0].headers.get('Authorization')).toBe('Bearer opened');
  });

  it('sends a student with completed onboarding to search', async () => {
    const server = fakeServer()
      .on('POST', '/api/v1/access/confirm', {
        body: opened('STUDENT'),
      })
      .on('GET', '/api/v1/me', {
        body: {
          ...ANA,
          onboardingStep: 'COMPLETED',
        },
      });

    const { location } = renderWithProviders(<ConfirmAccessPage />, {
      route: '/access/confirm?token=student-link',

      path: '/access/confirm',
    });

    await waitFor(() => expect(location()).toBe('/buscar'));

    expect(server.requests('POST', '/api/v1/access/confirm')).toHaveLength(1);

    expect(server.requests('GET', '/api/v1/me')).toHaveLength(1);
  });

  it.each([400, 409])('treats a %i link as expired or already used', async (status) => {
    fakeServer().on('POST', '/api/v1/access/confirm', {
      status,

      body: {
        message: 'Do not show this',
      },
    });

    renderWithProviders(<ConfirmAccessPage />, {
      route: '/access/confirm?token=spent',

      path: '/access/confirm',
    });

    expect(
      await screen.findByRole('heading', {
        name: 'El enlace venció o ya se usó',
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('link', {
        name: 'Pedir otro enlace',
      }),
    ).toHaveAttribute('href', '/ingresar');

    expect(screen.queryByText('Do not show this')).not.toBeInTheDocument();

    expect(currentSession()).toBeNull();
  });

  it('treats a missing token as an unusable link without calling the API', async () => {
    const server = fakeServer();

    renderWithProviders(<ConfirmAccessPage />, {
      route: '/access/confirm',

      path: '/access/confirm',
    });

    expect(
      await screen.findByRole('heading', {
        name: 'El enlace venció o ya se usó',
      }),
    ).toBeInTheDocument();

    expect(server.requests('POST', '/api/v1/access/confirm')).toHaveLength(0);
  });

  it('retries the confirmation only when the confirmation itself failed', async () => {
    let status = 503;

    const server = fakeServer().on('POST', '/api/v1/access/confirm', () => ({
      status,

      body: status === 200 ? opened('COORDINATOR') : {},
    }));

    const { user, location } = renderWithProviders(<ConfirmAccessPage />, {
      route: '/access/confirm?token=abc',

      path: '/access/confirm',
    });

    const retry = await screen.findByRole('button', {
      name: 'Reintentar',
    });

    status = 200;

    await user.click(retry);

    await waitFor(() => expect(location()).toBe('/coordinador/reconocimiento'));

    expect(server.requests('POST', '/api/v1/access/confirm')).toHaveLength(2);
  });

  it('retries only the profile when the student session was already opened', async () => {
    let profileStatus = 503;

    const server = fakeServer()
      .on('POST', '/api/v1/access/confirm', {
        body: opened('STUDENT'),
      })
      .on('GET', '/api/v1/me', () => ({
        status: profileStatus,

        body:
          profileStatus === 200
            ? {
                ...ANA,

                onboardingStep: 'PROFILE',
              }
            : {},
      }));

    const { user, location } = renderWithProviders(<ConfirmAccessPage />, {
      route: '/access/confirm?token=abc',

      path: '/access/confirm',
    });

    expect(
      await screen.findByRole('heading', {
        name: 'Tu sesión ya está abierta',
      }),
    ).toBeInTheDocument();

    expect(currentSession()?.sessionToken).toBe('opened');

    profileStatus = 200;

    await user.click(
      screen.getByRole('button', {
        name: 'Reintentar',
      }),
    );

    await waitFor(() => expect(location()).toBe('/onboarding'));

    /*
     * The important rule:
     * the one-use link was NOT posted again.
     */
    expect(server.requests('POST', '/api/v1/access/confirm')).toHaveLength(1);

    expect(server.requests('GET', '/api/v1/me')).toHaveLength(2);
  });

  it('opening the same link twice does not remove the session opened the first time', async () => {
    const firstServer = fakeServer().on('POST', '/api/v1/access/confirm', {
      body: opened('COORDINATOR', 'first-session'),
    });

    const first = renderWithProviders(<ConfirmAccessPage />, {
      route: '/access/confirm?token=abc',

      path: '/access/confirm',
    });

    await waitFor(() => expect(first.location()).toBe('/coordinador/reconocimiento'));

    expect(firstServer.requests('POST', '/api/v1/access/confirm')).toHaveLength(1);

    expect(currentSession()?.sessionToken).toBe('first-session');

    first.unmount();

    /*
     * The server now says that the same magic link was already used.
     * The previously-opened session must remain untouched.
     */
    fakeServer().on('POST', '/api/v1/access/confirm', {
      status: 409,

      body: {
        message: 'Already used',
      },
    });

    renderWithProviders(<ConfirmAccessPage />, {
      route: '/access/confirm?token=abc',

      path: '/access/confirm',
    });

    expect(
      await screen.findByRole('heading', {
        name: 'El enlace venció o ya se usó',
      }),
    ).toBeInTheDocument();

    expect(currentSession()?.sessionToken).toBe('first-session');
  });
});
