import { NavLink } from 'react-router-dom';
import { cx } from '../ui/cx';
import type { NavGroup, NavEntry } from './navigation';

export interface NavSection {
  group: NavGroup;
  label: string;
  entries: NavEntry[];
}

/** The tabs of the role, under the header on a wide screen. The active one wears the brand. */
export function AppNav({ sections }: { sections: NavSection[] }) {
  if (sections.length === 0) return null;
  return (
    <nav aria-label="Principal" className="hidden border-t border-gray-100 md:block">
      <div className="mx-auto flex max-w-6xl items-stretch gap-6 overflow-x-auto px-4">
        {sections.map((section, index) => (
          <div
            key={section.group}
            className={cx('flex items-stretch gap-1', index > 0 && 'border-l border-gray-200 pl-6')}
          >
            {sections.length > 1 && (
              <span className="self-center pr-2 text-xs font-medium uppercase tracking-wide text-gray-500">
                {section.label}
              </span>
            )}
            <ul className="flex items-stretch gap-1">
              {section.entries.map((entry) => (
                <li key={entry.to} className="flex">
                  <NavLink
                    to={entry.to}
                    className={({ isActive }) =>
                      cx(
                        '-mb-px flex items-center whitespace-nowrap border-b-2 px-3 py-3 text-sm',
                        'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-brand',
                        isActive
                          ? 'border-brand font-semibold text-brand-text'
                          : 'border-transparent text-gray-600 hover:border-gray-300 hover:text-gray-900',
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
    </nav>
  );
}
