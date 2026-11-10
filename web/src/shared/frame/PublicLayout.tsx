import { Suspense } from 'react';
import { Outlet } from 'react-router-dom';
import { PageLoading } from '../ui/Spinner';

/**
 * The frame of the screens without a session: signing in and confirming the link. As wide as the
 * card of FE-01 (460 px) plus its margins, and without a footer: the frame draws none.
 */
export function PublicLayout() {
  return (
    <div className="flex min-h-screen flex-col bg-surface">
      <main id="contenido" className="mx-auto flex w-full max-w-[492px] flex-1 flex-col justify-center px-4 py-10">
        <Suspense fallback={<PageLoading />}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  );
}
