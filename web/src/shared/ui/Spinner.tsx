import { cx } from './cx';

export interface SpinnerProps {
  size?: 'sm' | 'md' | 'lg';
  /** What is loading, read by screen readers. */
  label?: string;
  /** Inside something that already says it is busy (a loading button): no announcement. */
  decorative?: boolean;
  className?: string;
}

const SIZES = { sm: 'h-4 w-4', md: 'h-6 w-6', lg: 'h-10 w-10' };

export function Spinner({ size = 'md', label = 'Cargando', decorative = false, className }: SpinnerProps) {
  const circle = (
    <svg className={cx('animate-spin', SIZES[size])} viewBox="0 0 24 24" fill="none" aria-hidden="true">
      <circle cx="12" cy="12" r="10" stroke="currentColor" strokeOpacity="0.25" strokeWidth="4" />
      <path d="M22 12a10 10 0 0 0-10-10" stroke="currentColor" strokeWidth="4" strokeLinecap="round" />
    </svg>
  );
  if (decorative) {
    return <span className={cx('inline-flex', className)}>{circle}</span>;
  }
  return (
    <span role="status" className={cx('inline-flex items-center', className)}>
      {circle}
      <span className="sr-only">{label}</span>
    </span>
  );
}

/** A page that is loading: never a blank page. */
export function PageLoading({ label }: { label?: string }) {
  return (
    <div className="flex min-h-[40vh] items-center justify-center text-brand-text">
      <Spinner size="lg" label={label} />
    </div>
  );
}
