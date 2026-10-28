import { screen, waitFor, within } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { ANA, CARLOS, UPC, WALLET, serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import { currentSession } from '../session/session';
import { AppLayout } from './AppLayout';
import type { NavEntry } from './navigation';
import { navigationFor } from './navigation';

const NAVIGATION: NavEntry[] = [
  { to: '/buscar', label: 'Buscar', role: 'STUDENT', group: 'learn', order: 10 },
  { to: '/tutor/agenda', label: 'Agenda', role: 'STUDENT', group: 'teach', order: 10 },
  { to: '/mis-tutorias', label: 'Mis tutorías', role: 'STUDENT', group: 'learn', order: 20 },
  { to: '/coordinador/regla', label: 'Regla', role: 'COORDINATOR', group: 'coordinate', order: 20 },
];

function renderFrame(route = '/buscar') {
  return renderWithProviders(
    <Routes>
      <Route element={<AppLayout navigation={NAVIGATION} bell={<a href="/avisos">Avisos</a>} />}>
        <Route path="*" element={<p>Contenido</p>} />
      </Route>
    </Routes>,
    { route },
  );
}

describe('navigationFor', () => {
  it('groups the entries of a role in order', () => {
    expect(navigationFor('STUDENT', NAVIGATION).map((s) => [s.label, s.entries.map((e) => e.label)])).toEqual([
      ['Aprender', ['Buscar', 'Mis tutorías']],
      ['Como tutor', ['Agenda']],
    ]);
    expect(navigationFor('PLATFORM_ADMIN', NAVIGATION)).toEqual([]);
  });
});

describe('AppLayout and the university', () => {
  it('paints no content until the university has loaded', async () => {
    let release: (() => void) | undefined;
    const gate = new Promise<void>((resolve) => (release = resolve));
    serveFrame(fakeServer());
    const original = globalThis.fetch;
    vi.stubGlobal('fetch', async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).includes('/api/v1/university')) await gate;
      return original(input, init);
    });
    signIn('STUDENT');
    renderFrame();

    expect(await screen.findAllByRole('status')).not.toHaveLength(0);
    expect(screen.queryByText('Contenido')).not.toBeInTheDocument();
    release?.();
    expect(await screen.findByText('Contenido')).toBeInTheDocument();
  });

  it('offers to retry, and never shows the content, when the university fails to load', async () => {
    const server = fakeServer()
      .on('GET', '/api/v1/me', { body: ANA })
      .on('GET', '/api/v1/wallet', { body: WALLET })
      .on('GET', '/api/v1/university', { status: 500, body: { status: 500, message: 'boom' } });
    signIn('STUDENT');
    const { user } = renderFrame();

    expect(await screen.findByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
    expect(screen.queryByText('Contenido')).not.toBeInTheDocument();

    server.on('GET', '/api/v1/university', { body: UPC });
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(await screen.findByText('Contenido')).toBeInTheDocument();
  });

  it('does not wait for a university a platform administrator does not have', async () => {
    fakeServer();
    signIn('PLATFORM_ADMIN');
    renderFrame('/admin');
    expect(await screen.findByText('Contenido')).toBeInTheDocument();
  });
});

describe('AppHeader', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('shows a student the university, both sides of the navigation, the balance and the bell', async () => {
    serveFrame(fakeServer());
    signIn('STUDENT');
    renderFrame();

    expect(await screen.findByRole('img', { name: 'Logo de Universidad Peruana de Ciencias Aplicadas' })).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Tu saldo: 12 créditos' })).toHaveAttribute('href', '/creditos');
    expect(screen.getByRole('link', { name: 'Avisos' })).toBeInTheDocument();
    expect(await screen.findAllByText('Ana Torres')).not.toHaveLength(0);
    expect(screen.getAllByText('Ing. de Software · 4.° ciclo')).not.toHaveLength(0);

    // The tabs of a wide screen; the phone menu is closed.
    const nav = screen.getByRole('navigation', { name: 'Principal' });
    expect(within(nav).getByRole('link', { name: 'Buscar' })).toHaveAttribute('aria-current', 'page');
    expect(within(nav).getByRole('link', { name: 'Agenda' })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'Regla' })).not.toBeInTheDocument();
  });

  it('paints the frame with the university colours', async () => {
    serveFrame(fakeServer());
    signIn('STUDENT');
    renderFrame();
    await waitFor(() => expect(document.documentElement.style.getPropertyValue('--brand-primary')).toBe('#E42322'));
    expect(document.documentElement.style.getPropertyValue('--brand-text')).toBe('#B61C1B');
  });

  it('shows a coordinator their navigation and no balance', async () => {
    const server = serveFrame(fakeServer(), CARLOS);
    signIn('COORDINATOR');
    renderFrame('/coordinador/regla');

    expect(await screen.findAllByText('Carlos Rivera')).not.toHaveLength(0);
    expect(screen.getAllByRole('link', { name: 'Regla', hidden: true })[0]).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /Tu saldo/ })).not.toBeInTheDocument();
    expect(server.requests('GET', '/api/v1/wallet')).toHaveLength(0);
  });

  it('shows no navigation to a student still in the onboarding', async () => {
    serveFrame(fakeServer()).on('GET', '/api/v1/me', { body: { fullName: 'Ana Torres', onboardingStep: 'PROFILE' } });
    signIn('STUDENT');
    renderFrame('/onboarding');
    expect(await screen.findAllByText('Ana Torres')).not.toHaveLength(0);
    expect(screen.queryByRole('link', { name: 'Buscar', hidden: true })).not.toBeInTheDocument();
  });

  it('signs out on the server and forgets the session', async () => {
    const server = serveFrame(fakeServer()).on('POST', '/api/v1/access/logout', { status: 204 });
    signIn('STUDENT');
    const { user } = renderFrame();

    const [signOut] = await screen.findAllByRole('button', { name: 'Salir', hidden: true });
    await user.click(signOut);

    await waitFor(() => expect(currentSession()).toBeNull());
    expect(server.requests('POST', '/api/v1/access/logout')[0].headers.get('Authorization')).toBe('Bearer token-student');
  });

  it('opens and closes the menu of a phone', async () => {
    serveFrame(fakeServer());
    signIn('STUDENT');
    const { user } = renderFrame();
    const toggle = screen.getByRole('button', { name: 'Abrir menú' });
    expect(toggle).toHaveAttribute('aria-expanded', 'false');

    await user.click(toggle);
    expect(screen.getByRole('button', { name: 'Cerrar menú' })).toHaveAttribute('aria-expanded', 'true');

    await user.keyboard('{Escape}');
    expect(screen.getByRole('button', { name: 'Abrir menú' })).toHaveFocus();
  });
});
