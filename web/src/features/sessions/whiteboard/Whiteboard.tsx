import { Suspense, lazy } from 'react';
import { Spinner, cx } from '@/shared/ui';
import { BoardLoading } from './BoardLoading';
import type { ChannelSnapshot } from './channel';
import { useWhiteboard } from './useWhiteboard';

// Excalidraw is loaded only by the screens that draw a board, with the room already open.
const LiveBoard = lazy(() => import('./LiveBoard'));

function statusOf(board: ChannelSnapshot): { text: string; tone: 'quiet' | 'loud' } {
  switch (board.status) {
    case 'CONNECTING':
      return { text: 'Conectando la pizarra…', tone: 'loud' };
    case 'RECONNECTING':
      return { text: 'Reconectando… la pizarra vuelve completa', tone: 'loud' };
    case 'OFFLINE':
      return { text: 'Sin conexión. Lo que dibujes se guarda al volver', tone: 'loud' };
    case 'CLOSED':
      return { text: 'La sesión terminó. La pizarra quedó guardada', tone: 'quiet' };
    case 'LIVE':
      return { text: board.unconfirmed > 0 ? 'Guardando…' : 'Guardado · podrás repasarla después', tone: 'quiet' };
  }
}

/**
 * FE-22 · the shared whiteboard, in the room: Excalidraw with every tool it has (ADR 0010), over
 * the live channel of ADR 0009. The channel opens at once, while Excalidraw loads. When the
 * session is over the board stays, to be read.
 */
export function Whiteboard({ sessionId, onClosed }: { sessionId: string; onClosed?: () => void }) {
  const board = useWhiteboard(sessionId, onClosed);
  const status = statusOf(board);

  return (
    <div className="flex h-full min-h-[420px] flex-col overflow-hidden rounded-[20px] border border-line bg-white lg:min-h-0">
      <p
        role="status"
        className={cx(
          'flex min-h-9 items-center justify-center gap-2 border-b border-line px-3 py-1.5 text-center text-xs font-medium',
          status.tone === 'loud' ? 'bg-ink text-white' : 'bg-white text-ink-2',
        )}
      >
        {status.tone === 'loud' && board.status !== 'OFFLINE' && <Spinner size="sm" decorative />}
        {status.text}
      </p>
      <div className="relative min-h-0 flex-1">
        <Suspense fallback={<BoardLoading label="Cargando la pizarra…" />}>
          <LiveBoard
            channel={board.channel}
            ready={board.ready}
            readOnly={board.status === 'CLOSED'}
            refused={board.refused}
          />
        </Suspense>
      </div>
    </div>
  );
}
