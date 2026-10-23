import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { IconChevronLeft } from './icons';

export interface PageHeaderProps {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  /** A way back to the screen this one hangs from ("Mis compras"). */
  back?: { to: string; label: string };
}

/** The title of the screen: its only `h1`. */
export function PageHeader({ title, description, actions, back }: PageHeaderProps) {
  return (
    <header className="mb-6 flex flex-col gap-3">
      {back && (
        <Link
          to={back.to}
          className="inline-flex w-fit items-center gap-1 text-sm font-medium text-brand-text hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-brand"
        >
          <IconChevronLeft className="h-4 w-4" />
          {back.label}
        </Link>
      )}
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-2xl font-semibold text-gray-900">{title}</h1>
          {description && <p className="mt-1 text-sm text-gray-600">{description}</p>}
        </div>
        {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
      </div>
    </header>
  );
}
