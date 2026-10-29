import { useEffect, useId, useRef, type ReactNode, type RefObject } from 'react';
import { createPortal } from 'react-dom';
import { IconClose } from './icons';

export interface DialogProps {
  open: boolean;
  /** Escape, the close button and a click outside call it, unless the dialog is not dismissible. */
  onClose: () => void;
  title: ReactNode;
  description?: ReactNode;
  children?: ReactNode;
  /** The buttons. The first one in the markup is drawn on the right on a wide screen. */
  footer?: ReactNode;
  /** False while something is being sent: the person cannot close it halfway. */
  dismissible?: boolean;
  /** What gets the focus on opening; otherwise the first focusable element, the close button. */
  initialFocus?: RefObject<HTMLElement>;
}

const FOCUSABLE =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * A modal dialog: the focus stays inside while it is open, Escape closes it, and closing gives the
 * focus back to whatever opened it.
 */
export function Dialog(props: DialogProps) {
  if (!props.open) return null;
  return createPortal(<DialogPanel {...props} />, document.body);
}

function DialogPanel({ onClose, title, description, children, footer, dismissible = true, initialFocus }: DialogProps) {
  const panel = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const descriptionId = useId();
  const close = useRef(onClose);
  close.current = onClose;
  const canDismiss = useRef(dismissible);
  canDismiss.current = dismissible;
  const focusTarget = useRef(initialFocus);
  focusTarget.current = initialFocus;

  useEffect(() => {
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const first = focusTarget.current?.current ?? panel.current?.querySelector<HTMLElement>(FOCUSABLE) ?? panel.current;
    first?.focus();
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        if (canDismiss.current) {
          event.stopPropagation();
          close.current();
        }
        return;
      }
      if (event.key !== 'Tab' || !panel.current) return;
      const focusable = Array.from(panel.current.querySelectorAll<HTMLElement>(FOCUSABLE));
      if (focusable.length === 0) {
        event.preventDefault();
        panel.current.focus();
        return;
      }
      const firstItem = focusable[0];
      const lastItem = focusable[focusable.length - 1];
      const active = document.activeElement;
      if (event.shiftKey && (active === firstItem || active === panel.current)) {
        event.preventDefault();
        lastItem.focus();
      } else if (!event.shiftKey && active === lastItem) {
        event.preventDefault();
        firstItem.focus();
      } else if (!panel.current.contains(active)) {
        event.preventDefault();
        firstItem.focus();
      }
    }

    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = overflow;
      opener?.focus();
    };
    // The focus is placed once, when the dialog opens.
  }, []);

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center p-4 sm:items-center">
      <div className="absolute inset-0 bg-gray-900/50" aria-hidden="true" onClick={() => dismissible && onClose()} />
      <div
        ref={panel}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        tabIndex={-1}
        className="relative max-h-[90vh] w-full max-w-md overflow-y-auto rounded-xl bg-white p-6 shadow-xl focus:outline-none"
      >
        {dismissible && (
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className="absolute right-3 top-3 rounded-md p-1.5 text-gray-500 hover:bg-gray-100 hover:text-gray-900 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
          >
            <IconClose />
          </button>
        )}
        <h2 id={titleId} className="pr-8 text-lg font-semibold text-gray-900">
          {title}
        </h2>
        {description && (
          <p id={descriptionId} className="mt-2 text-sm text-gray-700">
            {description}
          </p>
        )}
        {children && <div className="mt-4">{children}</div>}
        {footer && (
          <div className="mt-6 flex flex-col-reverse gap-2 sm:flex-row-reverse sm:justify-start">{footer}</div>
        )}
      </div>
    </div>
  );
}
