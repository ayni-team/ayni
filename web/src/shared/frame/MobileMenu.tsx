import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { NavLink, useLocation } from 'react-router-dom';
import { cx } from '../ui/cx';
import {
  IconAward,
  IconBell,
  IconBook,
  IconCalendar,
  IconChart,
  IconClock,
  IconClose,
  IconMenu,
  IconPencil,
  IconSearch,
  IconStar,
  IconUser,
  IconWallet,
} from '../ui/icons';
import type { NavSection } from './AppNav';

/** The icon of each destination in the phone menu, as the Marco frame draws them. */
const ICONS: Record<string, ReactNode> = {
  '/buscar': <IconSearch />,
  '/mis-tutorias': <IconCalendar />,
  '/creditos': <IconWallet />,
  '/reconocimiento': <IconAward />,
  '/tutor/agenda': <IconClock />,
  '/tutor/disponibilidad': <IconCalendar />,
  '/tutor/habilidades': <IconBook />,
  '/tutor/reputacion': <IconStar />,
  '/coordinador/reconocimiento': <IconAward />,
  '/coordinador/regla': <IconPencil />,
  '/coordinador/indicadores': <IconChart />,
  '/avisos': <IconBell />,
  '/perfil': <IconUser />,
};

const ENTRY = 'flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium';

function Entry({ to, label }: { to: string; label: string }) {
  return (
    <NavLink
      to={to}
      className={({ isActive }) =>
        cx(ENTRY, isActive ? 'bg-brand-soft text-brand-text' : 'text-ink hover:bg-surface [&>svg]:text-ink-2')
      }
    >
      {ICONS[to]}
      {label}
    </NavLink>
  );
}

export interface MobileMenuProps {
  sections: NavSection[];
  /** The person, at the top of the menu. */
  person?: ReactNode;
  /** The destinations that are not tabs (Avisos, Mi perfil), after the tabs. */
  extra?: readonly { to: string; label: string }[];
  /** The way out, last and in red. */
  signOut?: ReactNode;
}

/**
 * The navigation on a phone: a bordered button in the header opens a card below it with the
 * person, the tabs of the role with their icons and their groups, and then the notices, the
 * profile and the way out. Choosing a destination or pressing Escape closes it.
 */
export function MobileMenu({ sections, person, extra = [], signOut }: MobileMenuProps) {
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
        className="flex h-10 w-10 items-center justify-center rounded-xl border border-line bg-white text-ink hover:bg-surface focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
      >
        {open ? <IconClose /> : <IconMenu />}
      </button>
      <div
        id={panelId}
        hidden={!open}
        className="absolute inset-x-3 top-full z-40 mt-2 max-h-[calc(100vh-5rem)] overflow-y-auto rounded-[20px] border border-line bg-white p-4 shadow-lg"
      >
        {person && <div className="px-1 pb-3">{person}</div>}
        <nav aria-label="Principal">
          {sections.map((section, index) => (
            <div key={section.group} className={cx(index > 0 && 'mt-2 border-t border-line pt-2')}>
              {index > 0 && (
                <p className="px-1 pb-1 text-[11px] font-medium uppercase tracking-wide text-gray-500">
                  {section.label}
                </p>
              )}
              <ul className="flex flex-col gap-0.5">
                {section.entries.map((entry) => (
                  <li key={entry.to}>
                    <Entry to={entry.to} label={entry.label} />
                  </li>
                ))}
              </ul>
            </div>
          ))}
          {(extra.length > 0 || signOut) && (
            <ul className="mt-2 flex flex-col gap-0.5 border-t border-line pt-2">
              {extra.map((entry) => (
                <li key={entry.to}>
                  <Entry to={entry.to} label={entry.label} />
                </li>
              ))}
              {signOut && <li>{signOut}</li>}
            </ul>
          )}
        </nav>
      </div>
    </div>
  );
}
