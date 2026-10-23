import type { ReactNode } from 'react';
import { cx } from './cx';

export type BadgeTone = 'neutral' | 'brand' | 'success' | 'warning' | 'danger' | 'info';

const TONES: Record<BadgeTone, string> = {
  neutral: 'bg-gray-100 text-gray-800',
  // Selected or the university's own: the soft tint with the readable primary.
  brand: 'bg-brand-soft text-brand-text',
  success: 'bg-green-50 text-green-800 ring-1 ring-inset ring-green-200',
  warning: 'bg-amber-50 text-amber-900 ring-1 ring-inset ring-amber-200',
  danger: 'bg-red-50 text-red-800 ring-1 ring-inset ring-red-200',
  info: 'bg-blue-50 text-blue-800 ring-1 ring-inset ring-blue-200',
};

export interface BadgeProps {
  tone?: BadgeTone;
  icon?: ReactNode;
  children: ReactNode;
  className?: string;
}

/** A short status ("Confirmada", "Tutor nuevo"). It always says it in words: colour is never alone. */
export function Badge({ tone = 'neutral', icon, children, className }: BadgeProps) {
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium',
        TONES[tone],
        className,
      )}
    >
      {icon}
      {children}
    </span>
  );
}
