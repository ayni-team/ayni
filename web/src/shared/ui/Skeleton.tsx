import { cx } from './cx';

export interface SkeletonProps {
  /** Bars of text to draw; a single block (sized by `className`) when absent. */
  lines?: number;
  className?: string;
  label?: string;
}

/** The shape of what is loading, so the page does not jump when it arrives. */
export function Skeleton({ lines, className, label = 'Cargando' }: SkeletonProps) {
  const bars = lines ?? 1;
  return (
    <div role="status" aria-label={label} className={cx('space-y-2', className)}>
      {Array.from({ length: bars }, (_, index) => (
        <div
          key={index}
          aria-hidden="true"
          className={cx(
            'animate-pulse rounded-md bg-gray-200',
            lines ? 'h-4' : 'h-full min-h-4',
            lines && lines > 1 && index === lines - 1 ? 'w-2/3' : 'w-full',
          )}
        />
      ))}
    </div>
  );
}
