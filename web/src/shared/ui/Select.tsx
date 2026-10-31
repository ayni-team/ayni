import { forwardRef, type SelectHTMLAttributes } from 'react';
import { cx } from './cx';
import { CONTROL_CLASSES, useFieldControl } from './Field';

export interface SelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  options?: readonly SelectOption[];
  /** A first, empty option ("Elige un curso"). */
  placeholder?: string;
}

/**
 * The design's chevron (Lucide's chevron-down in ink-2), drawn as a background so the select keeps
 * the browser's own list, which is accessible and native on a phone.
 */
const CHEVRON =
  "bg-[url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' " +
  "stroke='%235A5F73' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E\")] " +
  'bg-[length:1.125rem] bg-[right_0.875rem_center] bg-no-repeat';

/**
 * The browser's own select with the look of a TextField: same height, border and focus, and the
 * design's chevron instead of the browser's arrow. Options as a list or as children.
 */
export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { className, options, placeholder, children, ...props },
  ref,
) {
  const control = useFieldControl(props);
  return (
    <select ref={ref} className={cx(CONTROL_CLASSES, CHEVRON, 'appearance-none pr-10', className)} {...control}>
      {placeholder !== undefined && <option value="">{placeholder}</option>}
      {options?.map((option) => (
        <option key={option.value} value={option.value} disabled={option.disabled}>
          {option.label}
        </option>
      ))}
      {children}
    </select>
  );
});
