import { screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { ANA, CARLOS, serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import { AppRoutes, FEATURES } from './App';
import type { Role } from './shared/session';

/**
 * Every page is replaced by one that only names its screen, so these tests check the guards (who
 * may enter, the redirect to /ingresar, the onboarding redirect, the 404) and never the content of
 * a screen: building a screen must not break them. Each screen tests its own content.
 */
vi.mock('./features/identity/sign-in/SignInPage', () => ({ default: () => 'Pantalla FE-01' }));
vi.mock('./features/identity/confirm-access/ConfirmAccessPage', () => ({ default: () => 'Pantalla FE-02' }));
vi.mock('./features/identity/onboarding/OnboardingPage', () => ({ default: () => 'Pantalla FE-03' }));
vi.mock('./features/identity/profile/ProfilePage', () => ({ default: () => 'Pantalla FE-04' }));
vi.mock('./features/identity/admin/AdminPage', () => ({ default: () => 'Pantalla Administración' }));
vi.mock('./features/matching/search/SearchPage', () => ({ default: () => 'Pantalla FE-10' }));
vi.mock('./features/booking/book/BookPage', () => ({ default: () => 'Pantalla FE-11' }));
vi.mock('./features/booking/my-tutorings/MyTutoringsPage', () => ({ default: () => 'Pantalla FE-12' }));
vi.mock('./features/booking/availability/AvailabilityPage', () => ({ default: () => 'Pantalla FE-40' }));
vi.mock('./features/booking/agenda/AgendaPage', () => ({ default: () => 'Pantalla FE-41' }));
vi.mock('./features/sessions/session/SessionPage', () => ({ default: () => 'Pantalla FE-20 a FE-23' }));
vi.mock('./features/sessions/review/SessionReviewPage', () => ({ default: () => 'Pantalla FE-24' }));
vi.mock('./features/wallet/wallet/WalletPage', () => ({ default: () => 'Pantalla FE-30' }));
vi.mock('./features/wallet/benefits/BenefitsPage', () => ({ default: () => 'Pantalla FE-33' }));
vi.mock('./features/payments/buy/BuyCreditsPage', () => ({ default: () => 'Pantalla FE-31' }));
vi.mock('./features/payments/purchases/PurchasesPage', () => ({ default: () => 'Pantalla FE-32' }));
vi.mock('./features/payments/purchases/PurchaseDetailPage', () => ({ default: () => 'Pantalla FE-32' }));
vi.mock('./features/recognition/progress/RecognitionProgressPage', () => ({ default: () => 'Pantalla FE-34' }));
vi.mock('./features/recognition/requests/RecognitionRequestsPage', () => ({ default: () => 'Pantalla FE-50' }));
vi.mock('./features/recognition/case/RecognitionCasePage', () => ({ default: () => 'Pantalla FE-51' }));
vi.mock('./features/recognition/rule/RecognitionRulePage', () => ({ default: () => 'Pantalla FE-52' }));
vi.mock('./features/skills/my-skills/MySkillsPage', () => ({ default: () => 'Pantalla FE-42' }));
vi.mock('./features/reputation/reputation/ReputationPage', () => ({ default: () => 'Pantalla FE-43' }));
vi.mock('./features/notifications/notices/NoticesPage', () => ({ default: () => 'Pantalla FE-60' }));
vi.mock('./features/analytics/indicators/IndicatorsPage', () => ({ default: () => 'Pantalla FE-53' }));

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

/** The public rows of the same table. */
const PUBLIC: [route: string, screen: string][] = [
  ['/ingresar', 'FE-01'],
  ['/access/confirm?token=t', 'FE-02'],
];

const ROLES: Role[] = ['STUDENT', 'COORDINATOR', 'PLATFORM_ADMIN'];

const CASES = TABLE.flatMap(([route, roles, id]) => ROLES.map((role) => [route, role, roles.includes(role), id] as const));

function serve(role: Role) {
  const server = fakeServer();
  if (role !== 'PLATFORM_ADMIN') serveFrame(server, role === 'COORDINATOR' ? CARLOS : ANA);
  return server;
}

/** `/creditos/compras/p1` → `/creditos/compras/:id`, the way the features declare it. */
function pattern(route: string): string {
  return route.split('?')[0].replace(/\/(s1|p1|r1)(?=\/|$)/, '/:id');
}

describe('routes', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('declares exactly the routes, roles and screens of the table', () => {
    const declared = FEATURES.flatMap((feature) => feature.routes)
      .map((route) => [route.path, route.access === 'public' ? 'public' : [...route.access].sort().join(','), route.screen])
      .sort();
    const expected = [
      ...TABLE.map(([route, roles, id]) => [pattern(route), [...roles].sort().join(','), id]),
      ...PUBLIC.map(([route, id]) => [pattern(route), 'public', id]),
    ].sort();
    expect(declared).toEqual(expected);
  });

  it.each(CASES)('%s as %s: allowed %s', async (route, role, allowed, id) => {
    serve(role);
    signIn(role);
    const { location } = renderWithProviders(<AppRoutes />, { route });

    if (allowed) {
      expect(await screen.findByText(`Pantalla ${id}`)).toBeInTheDocument();
      expect(screen.queryByRole('heading', { name: 'No tienes permiso para esto' })).not.toBeInTheDocument();
    } else {
      expect(await screen.findByRole('heading', { name: 'No tienes permiso para esto' })).toBeInTheDocument();
      expect(screen.queryByText(`Pantalla ${id}`)).not.toBeInTheDocument();
    }
    expect(location()).toBe(route);
  });

  it.each(TABLE.map(([route]) => route))('%s without a session goes to sign in', async (route) => {
    fakeServer();
    const { location } = renderWithProviders(<AppRoutes />, { route });
    expect(await screen.findByText('Pantalla FE-01')).toBeInTheDocument();
    expect(location()).toBe('/ingresar');
  });

  it.each(PUBLIC)('%s opens without a session', async (route, id) => {
    fakeServer();
    const { location } = renderWithProviders(<AppRoutes />, { route });
    expect(await screen.findByText(`Pantalla ${id}`)).toBeInTheDocument();
    expect(location()).toBe(route);
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
    expect(await screen.findByText('Pantalla FE-03')).toBeInTheDocument();
    expect(location()).toBe('/onboarding');
  });

  it('says a route that does not exist does not exist', async () => {
    serve('STUDENT');
    signIn('STUDENT');
    renderWithProviders(<AppRoutes />, { route: '/tutorias' });
    expect(await screen.findByRole('heading', { name: 'No encontramos lo que buscas' })).toBeInTheDocument();
  });
});
