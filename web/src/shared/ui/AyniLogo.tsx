import { cx } from './cx';

export interface AyniLogoProps {
  /** Only the mark, without the word. */
  markOnly?: boolean;
  className?: string;
}

/**
 * Ayni's mark: two linked rings, one hour given and one received. It is drawn in the current text
 * colour, so it takes the university's colour or a neutral one from where it is placed.
 */
export function AyniLogo({ markOnly = false, className }: AyniLogoProps) {
  return (
    <span className={cx('inline-flex items-center gap-2', className)}>
      <svg
        viewBox="0 0 32 32"
        className="h-7 w-7"
        fill="none"
        stroke="currentColor"
        strokeWidth={3}
        role="img"
        aria-label={markOnly ? 'Ayni' : undefined}
        aria-hidden={markOnly ? undefined : true}
      >
        <circle cx="12" cy="16" r="8" />
        <circle cx="20" cy="16" r="8" />
      </svg>
      {!markOnly && <span className="text-xl font-bold tracking-tight">Ayni</span>}
    </span>
  );
}
