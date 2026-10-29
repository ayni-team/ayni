import { createContext, useContext, useId, type ReactNode } from 'react';
import { cx } from './cx';
import { IconAlert } from './icons';

/**
 * A label, a hint and an error around any input. The input inside (TextField, TextArea, Select)
 * takes its id and its descriptions from here, so the label is always tied to it and a screen
 * reader reads the hint and the error with it.
 */
interface FieldContextValue {
  id: string;
  describedBy?: string;
  invalid: boolean;
  required?: boolean;
}

const FieldContext = createContext<FieldContextValue | null>(null);

export interface FieldProps {
  label: ReactNode;
  hint?: ReactNode;
  /** The Spanish text of what is wrong. Its presence marks the input as invalid. */
  error?: ReactNode;
  required?: boolean;
  children: ReactNode;
  className?: string;
}

export function Field({ label, hint, error, required, children, className }: FieldProps) {
  const id = useId();
  const hintId = `${id}-hint`;
  const errorId = `${id}-error`;
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined;

  return (
    <FieldContext.Provider value={{ id, describedBy, invalid: Boolean(error), required }}>
      <div className={cx('flex flex-col gap-1.5', className)}>
        <label htmlFor={id} className="text-[13px] font-semibold text-ink">
          {label}
          {required && (
            <span className="text-red-700" aria-hidden="true">
              {' '}
              *
            </span>
          )}
        </label>
        {children}
        {hint && (
          <p id={hintId} className="text-xs text-ink-2">
            {hint}
          </p>
        )}
        {error && (
          <p id={errorId} className="flex items-start gap-1 text-xs font-medium text-red-700">
            <IconAlert className="mt-px h-3.5 w-3.5 shrink-0" />
            <span>{error}</span>
          </p>
        )}
      </div>
    </FieldContext.Provider>
  );
}

interface ControlProps {
  id?: string;
  required?: boolean;
  'aria-describedby'?: string;
  'aria-invalid'?: boolean | 'true' | 'false' | 'grammar' | 'spelling';
}

/** The attributes an input takes from the Field around it; its own props win. */
export function useFieldControl<P extends ControlProps>(props: P): P {
  const field = useContext(FieldContext);
  if (!field) return props;
  return {
    ...props,
    id: props.id ?? field.id,
    required: props.required ?? field.required,
    'aria-describedby': props['aria-describedby'] ?? field.describedBy,
    'aria-invalid': props['aria-invalid'] ?? (field.invalid || undefined),
  };
}

/**
 * The look every text input shares, as the design draws it: 44 px tall, 12 px corners, the line
 * colour as border and the university's colour, two pixels wide, on focus.
 */
export const CONTROL_CLASSES =
  'block min-h-11 w-full rounded-xl border border-line bg-white px-3.5 py-2.5 text-sm text-ink ' +
  'placeholder:text-gray-500 focus:border-brand focus:outline-none focus:ring-1 focus:ring-brand ' +
  'aria-[invalid=true]:border-red-700 aria-[invalid=true]:focus:ring-red-700 ' +
  'disabled:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-500';
