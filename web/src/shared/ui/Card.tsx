import { useId, type ReactNode } from 'react';
import { cx } from './cx';

export interface CardProps {
  title?: ReactNode;
  description?: ReactNode;
  /** Buttons or links on the right of the title. */
  actions?: ReactNode;
  children?: ReactNode;
  as?: 'section' | 'article' | 'div';
  className?: string;
}

/** A white block on the grey page. With a title it is a labelled region. */
export function Card({ title, description, actions, children, as: Tag = 'section', className }: CardProps) {
  const titleId = useId();
  return (
    <Tag
      aria-labelledby={title ? titleId : undefined}
      className={cx('rounded-xl border border-gray-200 bg-white p-4 shadow-sm sm:p-6', className)}
    >
      {(title || actions) && (
        <header className="mb-4 flex flex-wrap items-start justify-between gap-3">
          <div className="min-w-0">
            {title && (
              <h2 id={titleId} className="text-base font-semibold text-gray-900">
                {title}
              </h2>
            )}
            {description && <p className="mt-1 text-sm text-gray-600">{description}</p>}
          </div>
          {actions && <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>}
        </header>
      )}
      {children}
    </Tag>
  );
}
