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

/** The browser's own select: accessible and native on a phone. Options as a list or as children. */
export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { className, options, placeholder, children, ...props },
  ref,
) {
  const control = useFieldControl(props);
  return (
    <select ref={ref} className={cx(CONTROL_CLASSES, 'pr-8', className)} {...control}>
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
