import type { ReactNode } from 'react';

export interface EmptyStateProps {
  /** What there is none of ("Todavía no tienes tutorías"). */
  title: ReactNode;
  /** What to do next ("Busca una en Buscar"). */
  description?: ReactNode;
  action?: ReactNode;
  icon?: ReactNode;
}

export function EmptyState({ title, description, action, icon }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-gray-300 bg-white px-6 py-10 text-center">
      {icon && <div className="text-gray-500">{icon}</div>}
      <h2 className="text-base font-semibold text-gray-900">{title}</h2>
      {description && <p className="max-w-md text-sm text-gray-600">{description}</p>}
      {action && <div className="mt-2">{action}</div>}
    </div>
  );
}
