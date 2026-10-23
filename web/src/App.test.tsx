import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { ANA, CARLOS, serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import { AppRoutes } from './App';
import type { Role } from './shared/session';

/**
 * The table "Routes and who may enter" of docs/frontend-guide.md, route by route: each opens its
 * screen for the roles it names and refuses every other role.
 */
const TABLE: [route: string, roles: Role[], screen: string][] = [
  ['/onboarding', ['STUDENT'], 'FE-03'],
  ['/perfil', ['STUDENT'], 'FE-04'],
  ['/buscar', ['STUDENT'], 'FE-10'],
  ['/reservar', ['STUDENT'], 'FE-11'],
  ['/mis-tutorias', ['STUDENT'], 'FE-12'],
  ['/sesion/s1', ['STUDENT'], 'FE-20 a FE-23'],
  ['/sesion/s1/repaso', ['STUDENT'], 'FE-24'],
  ['/creditos', ['STUDENT'], 'FE-30'],
  ['/creditos/comprar', ['STUDENT'], 'FE-31'],
  ['/creditos/compras', ['STUDENT'], 'FE-32'],
  ['/creditos/compras/p1', ['STUDENT'], 'FE-32'],
  ['/creditos/beneficios', ['STUDENT'], 'FE-33'],
  ['/reconocimiento', ['STUDENT'], 'FE-34'],
  ['/tutor/disponibilidad', ['STUDENT'], 'FE-40'],
  ['/tutor/agenda', ['STUDENT'], 'FE-41'],
  ['/tutor/habilidades', ['STUDENT'], 'FE-42'],
  ['/tutor/reputacion', ['STUDENT'], 'FE-43'],
  ['/avisos', ['STUDENT', 'COORDINATOR'], 'FE-60'],
  ['/coordinador/reconocimiento', ['COORDINATOR'], 'FE-50'],
  ['/coordinador/reconocimiento/r1', ['COORDINATOR'], 'FE-51'],
  ['/coordinador/regla', ['COORDINATOR'], 'FE-52'],
  ['/coordinador/indicadores', ['COORDINATOR'], 'FE-53'],
  ['/admin', ['PLATFORM_ADMIN'], 'Administración'],
];

const ROLES: Role[] = ['STUDENT', 'COORDINATOR', 'PLATFORM_ADMIN'];

const CASES = TABLE.flatMap(([route, roles, id]) => ROLES.map((role) => [route, role, roles.includes(role), id] as const));

function serve(role: Role) {
  const server = fakeServer();
  if (role !== 'PLATFORM_ADMIN') serveFrame(server, role === 'COORDINATOR' ? CARLOS : ANA);
  return server;
}

describe('routes', () => {
  afterEach(() => vi.unstubAllGlobals());

  it.each(CASES)('%s as %s: allowed %s', async (route, role, allowed, id) => {
    serve(role);
    signIn(role);
    renderWithProviders(<AppRoutes />, { route });

    if (!allowed) {
      expect(await screen.findByRole('heading', { name: 'No tienes permiso para esto' })).toBeInTheDocument();
    } else if (role === 'PLATFORM_ADMIN') {
      expect(await screen.findByRole('heading', { level: 1, name: 'Administración de la plataforma' })).toBeInTheDocument();
    } else {
      expect(await screen.findByText(id)).toBeInTheDocument();
      expect(screen.getByRole('heading', { name: 'En construcción' })).toBeInTheDocument();
    }
  });

  it.each(TABLE.map(([route]) => route))('%s without a session goes to sign in', async (route) => {
    fakeServer();
    const { location } = renderWithProviders(<AppRoutes />, { route });
    expect(await screen.findByRole('heading', { name: 'Ingresar' })).toBeInTheDocument();
    expect(location()).toBe('/ingresar');
  });

  it.each([
    ['STUDENT', '/buscar'],
    ['COORDINATOR', '/coordinador/reconocimiento'],
    ['PLATFORM_ADMIN', '/admin'],
  ] as const)('/ sends %s to %s', async (role, home) => {
    serve(role);
    signIn(role);
    const { location } = renderWithProviders(<AppRoutes />, { route: '/' });
    await vi.waitFor(() => expect(location()).toBe(home));
  });

  it('sends a student who has not finished the onboarding to /onboarding from any route', async () => {
    serveFrame(fakeServer()).on('GET', '/api/v1/me', { body: { ...ANA, onboardingStep: 'ACADEMIC_RECORD' } });
    signIn('STUDENT');
    const { location } = renderWithProviders(<AppRoutes />, { route: '/creditos' });
    expect(await screen.findByText('FE-03')).toBeInTheDocument();
    expect(location()).toBe('/onboarding');
  });

  it('says a route that does not exist does not exist', async () => {
    serve('STUDENT');
    signIn('STUDENT');
    renderWithProviders(<AppRoutes />, { route: '/tutorias' });
    expect(await screen.findByRole('heading', { name: 'No encontramos lo que buscas' })).toBeInTheDocument();
  });

  it('opens the public screens without a session', async () => {
    fakeServer().on('POST', '/api/v1/access/confirm', { status: 400, body: { message: 'Expired' } });
    renderWithProviders(<AppRoutes />, { route: '/access/confirm?token=t' });
    expect(await screen.findByRole('heading', { name: 'El enlace venció o ya se usó' })).toBeInTheDocument();
  });
});
