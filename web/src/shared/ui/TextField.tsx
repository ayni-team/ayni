import { forwardRef, type InputHTMLAttributes } from 'react';
import { cx } from './cx';
import { CONTROL_CLASSES, useFieldControl } from './Field';

export type TextFieldProps = InputHTMLAttributes<HTMLInputElement>;

/** A one-line input. Put it inside a `Field` for its label. Works with react-hook-form's `register`. */
export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  { className, type = 'text', ...props },
  ref,
) {
  const control = useFieldControl(props);
  return <input ref={ref} type={type} className={cx(CONTROL_CLASSES, className)} {...control} />;
});
