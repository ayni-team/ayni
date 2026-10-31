import { NavLink, useLocation } from 'react-router-dom';
import { cx } from '../ui/cx';
import type { NavEntry, NavGroup } from './navigation';

export interface NavSection {
  group: NavGroup;
  label: string;
  entries: NavEntry[];
}

/**
 * The tabs of the role under the header on a wide screen, as the Marco frame draws them: a rounded
 * bar where the active tab is a pill in the university's colour. The first group goes without a
 * label; the next ones ("Como tutor") name themselves in small type before their tabs.
 */
export function AppNav({ sections }: { sections: NavSection[] }) {
  const { pathname } = useLocation();
  if (sections.length === 0) return null;
  const alsoActive = (entry: NavEntry) => entry.also?.some((path) => pathname.startsWith(path)) ?? false;
  return (
    <nav aria-label="Principal" className="hidden border-t border-line md:block">
      <div className="mx-auto max-w-6xl overflow-x-auto px-4 py-3">
        <div className="mx-auto flex w-fit items-center gap-1 rounded-full bg-surface p-1.5">
          {sections.map((section, index) => (
            <div key={section.group} className="flex items-center gap-1">
              {index > 0 && (
                <span className="whitespace-nowrap px-2 text-xs font-medium text-gray-500">{section.label}</span>
              )}
              <ul className="flex items-center gap-1">
                {section.entries.map((entry) => (
                  <li key={entry.to}>
                    <NavLink
                      to={entry.to}
                      className={({ isActive }) =>
                        cx(
                          'flex items-center whitespace-nowrap rounded-full px-4 py-2 text-[15px] transition-colors xl:px-5',
                          'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand',
                          isActive || alsoActive(entry)
                            ? 'bg-brand font-semibold text-brand-secondary'
                            : 'text-ink-2 hover:bg-white hover:text-ink',
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
        </div>
      </div>
    </nav>
  );
}
