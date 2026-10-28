import { Suspense, useMemo, type ReactNode } from 'react';
import { Outlet } from 'react-router-dom';
import { useUniversityStatus } from '../brand/BrandProvider';
import { useMe } from '../session/useMe';
import { useSession } from '../session/useSession';
import { ErrorState } from '../ui/ErrorState';
import { PageLoading } from '../ui/Spinner';
import { AppFooter } from './AppFooter';
import { AppHeader } from './AppHeader';
import { navigationFor, type NavEntry } from './navigation';

export interface AppLayoutProps {
  navigation: readonly NavEntry[];
  bell?: ReactNode;
}

function SkipLink() {
  return (
    <a
      href="#contenido"
      className="sr-only rounded-md bg-white px-3 py-2 text-sm font-medium text-brand-text focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50"
    >
      Saltar al contenido
    </a>
  );
}

/**
 * The frame every signed-in screen is drawn inside: header, navigation, content and footer. Pages
 * render through `Outlet` and none of them repeats the frame. A student still in the onboarding
 * sees no navigation: there is nowhere to go until it is finished.
 */
export function AppLayout({ navigation, bell }: AppLayoutProps) {
  const session = useSession();
  const me = useMe();
  const onboarding = session?.role === 'STUDENT' && me.data?.onboardingStep !== 'COMPLETED';
  const role = session?.role;
  const sections = useMemo(
    () => (role && !onboarding ? navigationFor(role, navigation) : []),
    [role, onboarding, navigation],
  );
  // Nothing is drawn with a guessed zone: the content waits for the university (UTC is only the
  // fallback of a platform administrator, who has none).
  const university = useUniversityStatus();
  const showBell = role === 'STUDENT' || role === 'COORDINATOR';

  return (
    <div className="flex min-h-screen flex-col bg-surface">
      <SkipLink />
      <AppHeader sections={sections} bell={showBell ? bell : undefined} />
      <main id="contenido" tabIndex={-1} className="mx-auto w-full max-w-6xl flex-1 px-4 py-6 focus:outline-none sm:py-8">
        {university.status === 'loading' ? (
          <PageLoading />
        ) : university.status === 'error' ? (
          <ErrorState
            title="No pudimos cargar tu universidad"
            description="Sin ella no sabemos en qué día estás. Intenta de nuevo."
            onRetry={university.retry}
          />
        ) : (
          <Suspense fallback={<PageLoading />}>
            <Outlet />
          </Suspense>
        )}
      </main>
      <AppFooter />
    </div>
  );
}
