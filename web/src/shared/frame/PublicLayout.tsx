import { Suspense } from 'react';
import { Outlet } from 'react-router-dom';
import { PageLoading } from '../ui/Spinner';

/** The frame of the screens without a session: signing in and confirming the link. */
export function PublicLayout() {
  return (
    <div className="flex min-h-screen flex-col bg-surface">
      <main id="contenido" className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center px-4 py-10">
        <Suspense fallback={<PageLoading />}>
          <Outlet />
        </Suspense>
      </main>
      <footer className="px-4 py-4 text-center text-xs text-ink-2">Ayni · Banco de tiempo académico</footer>
    </div>
  );
}
