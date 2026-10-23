import { useId, useRef, type KeyboardEvent, type ReactNode } from 'react';
import { cx } from './cx';

export interface TabItem<V extends string> {
  value: V;
  label: ReactNode;
  /** A number beside the label ("Sin leer 3"). */
  count?: number;
}

export interface TabsProps<V extends string> {
  /** What the tabs choose between, for screen readers ("Vista de tutorías"). */
  label: string;
  items: readonly TabItem<V>[];
  value: V;
  onChange: (value: V) => void;
  /** The content of the selected tab. */
  children?: ReactNode;
  className?: string;
}

/**
 * Tabs that switch a view (upcoming and history, queue and decided). The arrows move between them
 * and select as they go; the selected one wears the university's colour.
 */
export function Tabs<V extends string>({ label, items, value, onChange, children, className }: TabsProps<V>) {
  const id = useId();
  const refs = useRef(new Map<V, HTMLButtonElement>());
  const tabId = (item: V) => `${id}-tab-${item}`;
  const panelId = `${id}-panel`;

  function move(event: KeyboardEvent, index: number) {
    const last = items.length - 1;
    const next =
      event.key === 'ArrowRight' ? (index === last ? 0 : index + 1)
      : event.key === 'ArrowLeft' ? (index === 0 ? last : index - 1)
      : event.key === 'Home' ? 0
      : event.key === 'End' ? last
      : null;
    if (next === null) return;
    event.preventDefault();
    const target = items[next].value;
    onChange(target);
    refs.current.get(target)?.focus();
  }

  return (
    <div className={className}>
      <div role="tablist" aria-label={label} className="flex gap-1 overflow-x-auto border-b border-gray-200">
        {items.map((item, index) => {
          const selected = item.value === value;
          return (
            <button
              key={item.value}
              ref={(element) => {
                if (element) refs.current.set(item.value, element);
                else refs.current.delete(item.value);
              }}
              type="button"
              role="tab"
              id={tabId(item.value)}
              aria-selected={selected}
              aria-controls={children !== undefined ? panelId : undefined}
              tabIndex={selected ? 0 : -1}
              onClick={() => onChange(item.value)}
              onKeyDown={(event) => move(event, index)}
              className={cx(
                '-mb-px inline-flex items-center gap-2 whitespace-nowrap border-b-2 px-3 py-2.5 text-sm',
                'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-brand',
                selected
                  ? 'border-brand font-semibold text-brand-text'
                  : 'border-transparent text-gray-600 hover:border-gray-300 hover:text-gray-900',
              )}
            >
              {item.label}
              {item.count !== undefined && (
                <span
                  className={cx(
                    'rounded-full px-2 py-0.5 text-xs font-medium',
                    selected ? 'bg-brand-soft text-brand-text' : 'bg-gray-100 text-gray-700',
                  )}
                >
                  {item.count}
                </span>
              )}
            </button>
          );
        })}
      </div>
      {children !== undefined && (
        <div role="tabpanel" id={panelId} aria-labelledby={tabId(value)} tabIndex={0} className="pt-4 focus:outline-none">
          {children}
        </div>
      )}
    </div>
  );
}
