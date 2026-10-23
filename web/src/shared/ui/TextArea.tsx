import { forwardRef, type TextareaHTMLAttributes } from 'react';
import { cx } from './cx';
import { CONTROL_CLASSES, useFieldControl } from './Field';

export interface TextAreaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  /** Shows how many characters are left when `maxLength` is set. */
  showCount?: boolean;
}

export const TextArea = forwardRef<HTMLTextAreaElement, TextAreaProps>(function TextArea(
  { className, rows = 4, showCount = false, ...props },
  ref,
) {
  const control = useFieldControl(props);
  const length = typeof props.value === 'string' ? props.value.length : undefined;
  return (
    <>
      <textarea ref={ref} rows={rows} className={cx(CONTROL_CLASSES, 'resize-y', className)} {...control} />
      {showCount && props.maxLength !== undefined && length !== undefined && (
        <p className="self-end text-xs text-gray-600" aria-live="polite">
          {length} de {props.maxLength}
        </p>
      )}
    </>
  );
});
