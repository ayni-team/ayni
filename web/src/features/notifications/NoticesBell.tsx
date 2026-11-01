import { useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { IconBell, Spinner } from '@/shared/ui';
import { NoticeList } from './NoticeList';
import { bellCount, bellLabel } from './notice';
import { BELL_REFRESH_MS, useMarkAllRead, useNotices } from './useNotices';

/** How many notices the tray shows before "Ver todos los avisos". */
const TRAY_SIZE = 4;

/**
 * The bell of the header, mounted by the frame for students and coordinators (FE-60).
 *
 * It counts the unread notices in the university's colour, refreshed on load and every minute while
 * the tab is visible, and opens a tray with the latest ones. The tray closes with Escape, a click
 * outside it, or opening a notice.
 */
export function NoticesBell() {
  const [open, setOpen] = useState(false);
  const trayId = useId();
  const root = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const notices = useNotices({ unread: false, page: 0, size: TRAY_SIZE }, { refetchInterval: BELL_REFRESH_MS });
  const markAll = useMarkAllRead();

  const unread = notices.data?.unreadCount ?? 0;
  const count = bellCount(unread);
  const items = notices.data?.items ?? [];

  useEffect(() => {
    if (!open) return;
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false);
        button.current?.focus();
      }
    }
    function onPointer(event: PointerEvent) {
      if (root.current && !root.current.contains(event.target as Node)) setOpen(false);
    }
    document.addEventListener('keydown', onKey);
    document.addEventListener('pointerdown', onPointer);
    return () => {
      document.removeEventListener('keydown', onKey);
      document.removeEventListener('pointerdown', onPointer);
    };
  }, [open]);

  return (
    <div ref={root} className="relative">
      <button
        ref={button}
        type="button"
        aria-label={bellLabel(unread)}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={open ? trayId : undefined}
        onClick={() => setOpen((value) => !value)}
        className="relative inline-flex rounded-xl p-2 text-gray-700 hover:bg-gray-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
      >
        <IconBell />
        {count && (
          <span
            aria-hidden="true"
            className="absolute -right-1.5 -top-1.5 inline-flex h-[18px] min-w-[18px] items-center justify-center rounded-full bg-brand px-1 text-[11px] font-semibold leading-none text-brand-secondary"
          >
            {count}
          </span>
        )}
      </button>

      {open && (
        <div
          id={trayId}
          role="dialog"
          aria-label="Avisos recientes"
          className="absolute right-0 top-full z-30 mt-2 w-[min(22rem,calc(100vw-2rem))] overflow-hidden rounded-2xl border border-line bg-white shadow-lg"
        >
          <div className="flex items-center justify-between gap-3 border-b border-line px-4 py-3">
            <p className="text-sm font-semibold text-ink">
              Avisos{unread > 0 && <span className="font-normal text-ink-2"> · {unread} sin leer</span>}
            </p>
            {unread > 0 && (
              <button
                type="button"
                onClick={() => markAll.mutate({})}
                disabled={markAll.isPending}
                className="text-sm font-semibold text-brand-text hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand disabled:opacity-60"
              >
                Marcar todas
              </button>
            )}
          </div>

          {notices.isPending ? (
            <div className="flex justify-center py-6">
              <Spinner label="Cargando tus avisos" />
            </div>
          ) : notices.isError ? (
            <div role="alert" className="flex flex-col items-center gap-2 px-4 py-6 text-center text-sm text-ink-2">
              No pudimos cargar tus avisos.
              <button
                type="button"
                onClick={() => void notices.refetch()}
                className="font-semibold text-brand-text hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
              >
                Reintentar
              </button>
            </div>
          ) : items.length === 0 ? (
            <p className="px-4 py-6 text-center text-sm text-ink-2">Todavía no tienes avisos.</p>
          ) : (
            <NoticeList notices={items} compact onOpen={() => setOpen(false)} />
          )}

          <Link
            to="/avisos"
            onClick={() => setOpen(false)}
            className="block border-t border-line px-4 py-3 text-center text-sm font-semibold text-brand-text hover:bg-gray-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-brand"
          >
            Ver todos los avisos
          </Link>
        </div>
      )}
    </div>
  );
}
