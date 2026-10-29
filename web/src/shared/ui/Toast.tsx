import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from 'react';
import { cx } from './cx';
import { IconAlert, IconCheck, IconClose, IconInfo } from './icons';

export type ToastTone = 'success' | 'error' | 'info';

export interface ToastInput {
  message: string;
  tone?: ToastTone;
  /** How long it stays; 5 seconds by default. */
  durationMs?: number;
}

interface ToastItem extends Required<ToastInput> {
  id: number;
}

export interface Toaster {
  show: (input: ToastInput) => void;
  success: (message: string) => void;
  error: (message: string) => void;
  info: (message: string) => void;
}

const ToastContext = createContext<Toaster | null>(null);

const TONES: Record<ToastTone, { className: string; icon: ReactNode }> = {
  success: { className: 'border-green-200', icon: <IconCheck className="h-5 w-5 text-green-700" /> },
  error: { className: 'border-red-200', icon: <IconAlert className="h-5 w-5 text-red-700" /> },
  info: { className: 'border-line', icon: <IconInfo className="h-5 w-5 text-brand-text" /> },
};

/**
 * Short messages that confirm something happened ("Te devolvimos 1 crédito"). They are announced to
 * screen readers and leave on their own. Errors that block the screen are not toasts: they are
 * drawn in the screen.
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const next = useRef(0);

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id));
  }, []);

  const show = useCallback(
    ({ message, tone = 'info', durationMs = 5000 }: ToastInput) => {
      const id = ++next.current;
      setToasts((current) => [...current, { id, message, tone, durationMs }]);
      window.setTimeout(() => dismiss(id), durationMs);
    },
    [dismiss],
  );

  const toaster = useMemo<Toaster>(
    () => ({
      show,
      success: (message) => show({ message, tone: 'success' }),
      error: (message) => show({ message, tone: 'error' }),
      info: (message) => show({ message, tone: 'info' }),
    }),
    [show],
  );

  return (
    <ToastContext.Provider value={toaster}>
      {children}
      <div
        aria-live="polite"
        className="pointer-events-none fixed inset-x-0 bottom-0 z-[60] flex flex-col items-center gap-2 p-4 sm:items-end"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role={toast.tone === 'error' ? 'alert' : 'status'}
            className={cx(
              'pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-lg border bg-white p-3 text-sm text-ink shadow-lg',
              TONES[toast.tone].className,
            )}
          >
            {TONES[toast.tone].icon}
            <p className="flex-1">{toast.message}</p>
            <button
              type="button"
              onClick={() => dismiss(toast.id)}
              aria-label="Cerrar aviso"
              className="rounded p-0.5 text-gray-500 hover:text-ink focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
            >
              <IconClose className="h-4 w-4" />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): Toaster {
  const toaster = useContext(ToastContext);
  if (!toaster) {
    throw new Error('useToast needs a ToastProvider above it');
  }
  return toaster;
}
