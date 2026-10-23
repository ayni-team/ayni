import { Route, Routes } from 'react-router-dom';
import { analyticsRoutes } from './features/analytics/routes';
import { bookingRoutes } from './features/booking/routes';
import { identityRoutes } from './features/identity/routes';
import { matchingRoutes } from './features/matching/routes';
import { NoticesBell } from './features/notifications';
import { notificationsRoutes } from './features/notifications/routes';
import { paymentsRoutes } from './features/payments/routes';
import { recognitionRoutes } from './features/recognition/routes';
import { reputationRoutes } from './features/reputation/routes';
import { sessionsRoutes } from './features/sessions/routes';
import { skillsRoutes } from './features/skills/routes';
import { walletRoutes } from './features/wallet/routes';
import { AppLayout, NotFound, PublicLayout, type FeatureRoutes } from './shared/frame';
import { HomeRedirect, OnboardingGuard, RequireAuth, RequireRole } from './shared/session';

/**
 * The routes of every feature, composed once. Each feature declares its own in `routes.tsx`;
 * adding a screen never edits this file.
 *
 * Public routes hang from the public frame. Every other route needs a session, passes the
 * onboarding guard (a student who has not finished it goes to `/onboarding`) and lets in only the
 * roles it declares. `/` has no screen: it sends each role to its start.
 */
export const FEATURES: readonly FeatureRoutes[] = [
  identityRoutes,
  matchingRoutes,
  bookingRoutes,
  sessionsRoutes,
  walletRoutes,
  paymentsRoutes,
  recognitionRoutes,
  skillsRoutes,
  reputationRoutes,
  notificationsRoutes,
  analyticsRoutes,
];

const routes = FEATURES.flatMap((feature) => feature.routes);
const navigation = FEATURES.flatMap((feature) => feature.navigation ?? []);

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<PublicLayout />}>
        {routes.map((route) =>
          route.access === 'public' ? <Route key={route.path} path={route.path} element={route.element} /> : null,
        )}
      </Route>
      <Route
        element={
          <RequireAuth>
            <OnboardingGuard>
              <AppLayout navigation={navigation} bell={<NoticesBell />} />
            </OnboardingGuard>
          </RequireAuth>
        }
      >
        <Route index element={<HomeRedirect />} />
        {routes.map((route) =>
          route.access === 'public' ? null : (
            <Route
              key={route.path}
              path={route.path}
              element={<RequireRole roles={route.access}>{route.element}</RequireRole>}
            />
          ),
        )}
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  );
}

export default AppRoutes;
