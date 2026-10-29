import { useId, useMemo, useRef, useState, type KeyboardEvent } from 'react';
import { cx } from './cx';
import { CONTROL_CLASSES, useFieldControl } from './Field';
import { IconChevronDown, IconSearch } from './icons';

export interface ComboboxOption {
  value: string;
  label: string;
  /** A second line in grey ("CC215 · curso UPC"); it is searched too. */
  description?: string;
}

export interface ComboboxProps {
  options: readonly ComboboxOption[];
  /** The value of the chosen option, or '' for none. */
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  /** What the list says when nothing matches what was typed. */
  emptyText?: string;
  id?: string;
  disabled?: boolean;
  className?: string;
}

/** Lower case and without accents, so "algebra" finds "Álgebra". */
function normalize(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLocaleLowerCase('es');
}

/**
 * A field to type in and choose one option from a list that narrows as one types, by its label or
 * its description and ignoring accents (the course picker of FE-10). Arrows move, Enter chooses,
 * Escape closes and keeps the choice there was. Once chosen it shows the label in bold and the
 * description in grey, as the design does. Put it inside a `Field` for its label.
 */
export function Combobox({
  options,
  value,
  onChange,
  placeholder,
  emptyText = 'Nada coincide con lo que escribiste',
  id,
  disabled,
  className,
}: ComboboxProps) {
  const control = useFieldControl({ id });
  const listId = useId();
  const optionId = (index: number) => `${listId}-option-${index}`;
  const input = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState<string | null>(null);
  const [active, setActive] = useState(0);

  const selected = options.find((option) => option.value === value);
  const editing = query !== null;
  const matches = useMemo(() => {
    if (!editing || query.trim() === '') return options;
    const wanted = normalize(query.trim());
    return options.filter((option) => normalize(`${option.label} ${option.description ?? ''}`).includes(wanted));
  }, [editing, options, query]);

  function show() {
    if (disabled) return;
    setOpen(true);
    const index = matches.findIndex((option) => option.value === value);
    setActive(index >= 0 ? index : 0);
  }

  function close() {
    setOpen(false);
    setQuery(null);
  }

  function choose(option: ComboboxOption) {
    onChange(option.value);
    close();
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (!open) return show();
      const last = matches.length - 1;
      if (last < 0) return;
      setActive((index) =>
        event.key === 'ArrowDown' ? (index >= last ? 0 : index + 1) : index <= 0 ? last : index - 1,
      );
    } else if (event.key === 'Enter') {
      if (open && matches[active]) {
        event.preventDefault();
        choose(matches[active]);
      }
    } else if (event.key === 'Escape') {
      if (open) {
        event.preventDefault();
        close();
      }
    }
  }

  const showChosen = !editing && selected;

  return (
    <div className={cx('relative', className)}>
      <IconSearch className="pointer-events-none absolute left-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-ink-2" />
      <input
        ref={input}
        {...control}
        type="text"
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={open && matches[active] ? optionId(active) : undefined}
        autoComplete="off"
        disabled={disabled}
        placeholder={placeholder}
        value={editing ? query : (selected?.label ?? '')}
        onChange={(event) => {
          setQuery(event.target.value);
          setActive(0);
          setOpen(true);
        }}
        onFocus={show}
        onClick={show}
        onBlur={close}
        onKeyDown={onKeyDown}
        className={cx(CONTROL_CLASSES, 'pl-11 pr-10', showChosen && 'text-transparent')}
      />
      {showChosen && (
        <span
          aria-hidden="true"
          className="pointer-events-none absolute inset-y-0 left-11 right-10 flex items-center gap-3 overflow-hidden whitespace-nowrap"
        >
          <span className="truncate text-[15px] font-semibold text-ink">{selected.label}</span>
          {selected.description && <span className="truncate text-xs text-ink-2">{selected.description}</span>}
        </span>
      )}
      <IconChevronDown
        className={cx(
          'pointer-events-none absolute right-3.5 top-1/2 h-5 w-5 -translate-y-1/2 text-ink-2 transition-transform',
          open && 'rotate-180',
        )}
      />
      <ul
        id={listId}
        role="listbox"
        hidden={!open}
        className="absolute inset-x-0 top-full z-30 mt-1.5 max-h-72 overflow-y-auto rounded-xl border border-line bg-white p-1.5 shadow-lg"
      >
        {matches.length === 0 ? (
          <li className="px-3 py-2.5 text-sm text-ink-2">{emptyText}</li>
        ) : (
          matches.map((option, index) => (
            // The keyboard stays in the input, which points at the active option
            // (aria-activedescendant); the click is the mouse's way to choose.
            // eslint-disable-next-line jsx-a11y/click-events-have-key-events
            <li
              key={option.value}
              id={optionId(index)}
              role="option"
              aria-selected={option.value === value}
              onMouseDown={(event) => event.preventDefault()}
              onMouseEnter={() => setActive(index)}
              onClick={() => choose(option)}
              className={cx(
                'flex cursor-pointer items-baseline justify-between gap-3 rounded-lg px-3 py-2.5',
                index === active && 'bg-surface',
                option.value === value && 'font-semibold',
              )}
            >
              <span className="text-sm text-ink">{option.label}</span>
              {option.description && <span className="shrink-0 text-xs text-ink-2">{option.description}</span>}
            </li>
          ))
        )}
      </ul>
    </div>
  );
}
