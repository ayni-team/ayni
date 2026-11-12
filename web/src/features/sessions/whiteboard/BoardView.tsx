import { Suspense, lazy } from 'react';
import type { BoardElement } from './board';
import { BoardLoading } from './BoardLoading';

const ReadOnlyBoard = lazy(() => import('./ReadOnlyBoard'));

/**
 * A board that is only read, as the review of a session shows it (FE-24): Excalidraw in view
 * mode, no tools and no connection. Boards of the old format are converted when read.
 */
export function BoardView({ elements }: { elements: readonly BoardElement[] | undefined }) {
  return (
    <div className="relative h-[420px] overflow-hidden rounded-xl border border-line bg-white sm:h-[520px]">
      <Suspense fallback={<BoardLoading label="Cargando la pizarra…" />}>
        <ReadOnlyBoard elements={elements ?? []} />
      </Suspense>
    </div>
  );
}
