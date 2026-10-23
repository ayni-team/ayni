import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { ButtonLink } from '../ui/Button';
import { ErrorState } from '../ui/ErrorState';
import { PageLoading } from '../ui/Spinner';
import { HOME_BY_ROLE, type Role } from './roles';
import { useMe } from './useMe';
import { useSession } from './useSession';

/** The route of the sign-in screen, where every guard sends whoever has no session. */
export const SIGN_IN_PATH = '/ingresar';
export const ONBOARDING_PATH = '/onboarding';

/** Lets through only someone signed in; anybody else goes to `/ingresar`. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const session = useSession();
  const location = useLocation();
  if (!session) {
    return <Navigate to={SIGN_IN_PATH} replace state={{ from: location.pathname }} />;
  }
  return <>{children}</>;
}

/**
 * Lets through only the roles of the route. Somebody signed in with another role is told so, with
 * a way to their own start, instead of being sent somewhere without explanation.
 */
export function RequireRole({ roles, children }: { roles: readonly Role[]; children: ReactNode }) {
  const session = useSession();
  if (!session) {
    return <RequireAuth>{children}</RequireAuth>;
  }
  if (!roles.includes(session.role)) {
    return <Forbidden home={HOME_BY_ROLE[session.role]} />;
  }
  return <>{children}</>;
}

export function Forbidden({ home }: { home: string }) {
  return (
    <ErrorState
      title="No tienes permiso para esto"
      description="Esta página es para otro tipo de cuenta."
      action={<ButtonLink to={home}>Ir a mi inicio</ButtonLink>}
    />
  );
}

/**
 * A student who has not finished the onboarding is sent to `/onboarding` from every route but that
 * one. The step is the server's (`MyProfileView.onboardingStep`), so closing the browser halfway
 * resumes where the person was.
 */
export function OnboardingGuard({ children }: { children: ReactNode }) {
  const session = useSession();
  const location = useLocation();
  const me = useMe();

  if (session?.role !== 'STUDENT' || location.pathname === ONBOARDING_PATH) {
    return <>{children}</>;
  }
  if (me.isPending) {
    return <PageLoading />;
  }
  if (me.isError) {
    return <ErrorState error={me.error} onRetry={() => void me.refetch()} />;
  }
  if (me.data?.onboardingStep !== 'COMPLETED') {
    return <Navigate to={ONBOARDING_PATH} replace />;
  }
  return <>{children}</>;
}

/** `/` has no screen: it sends each role where the table of routes says. */
export function HomeRedirect() {
  const session = useSession();
  return <Navigate to={session ? HOME_BY_ROLE[session.role] : SIGN_IN_PATH} replace />;
}
