import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { cx } from '../ui/cx';
import { IconClose, IconMenu } from '../ui/icons';
import type { NavSection } from './AppNav';

/**
 * The navigation on a phone: a button in the header opens it below, grouped as on a wide screen,
 * with the person and the way out at the end. Choosing a destination or pressing Escape closes it.
 */
export function MobileMenu({ sections, footer }: { sections: NavSection[]; footer?: ReactNode }) {
  const [open, setOpen] = useState(false);
  const panelId = useId();
  const location = useLocation();
  const button = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    setOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    if (!open) return undefined;
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false);
        button.current?.focus();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [open]);

  return (
    <div className="md:hidden">
      <button
        ref={button}
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        aria-label={open ? 'Cerrar menú' : 'Abrir menú'}
        onClick={() => setOpen((value) => !value)}
        className="rounded-md p-2 text-gray-700 hover:bg-gray-100 focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
      >
        {open ? <IconClose /> : <IconMenu />}
      </button>
      <div
        id={panelId}
        hidden={!open}
        className="absolute inset-x-0 top-full z-40 max-h-[calc(100vh-4rem)] overflow-y-auto border-b border-line bg-white shadow-lg"
      >
        <nav aria-label="Principal" className="px-4 py-3">
          {sections.map((section) => (
            <div key={section.group} className="py-2">
              {sections.length > 1 && (
                <p className="px-3 pb-1 text-xs font-medium uppercase tracking-wide text-gray-500">{section.label}</p>
              )}
              <ul>
                {section.entries.map((entry) => (
                  <li key={entry.to}>
                    <NavLink
                      to={entry.to}
                      className={({ isActive }) =>
                        cx(
                          'block rounded-lg px-3 py-2.5 text-sm',
                          isActive ? 'bg-brand-soft font-semibold text-brand-text' : 'text-gray-800 hover:bg-gray-50',
                        )
                      }
                    >
                      {entry.label}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>
        {footer && <div className="border-t border-gray-100 px-4 py-3">{footer}</div>}
      </div>
    </div>
  );
}
