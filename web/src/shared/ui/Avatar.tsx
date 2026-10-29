import { useState } from 'react';
import { cx } from './cx';

/** "Ana Torres" → "AT", "Bruno" → "B": the first letters of the first two words. */
export function initialsOf(name: string | null | undefined): string {
  const words = (name ?? '').trim().split(/\s+/).filter(Boolean);
  return words
    .slice(0, 2)
    .map((word) => word[0].toLocaleUpperCase('es'))
    .join('');
}

export interface AvatarProps {
  name: string;
  photoUrl?: string | null;
  size?: 'sm' | 'md' | 'lg';
  /** Beside the name already written: hidden from screen readers so it is not read twice. */
  decorative?: boolean;
  className?: string;
}

const SIZES = { sm: 'h-8 w-8 text-xs', md: 'h-10 w-10 text-sm', lg: 'h-16 w-16 text-lg' };

/**
 * The tints of the design for initials. Always the same one for the same name, so a tutor keeps
 * their colour from the grid to the panel; none is the university's, which is kept for actions.
 */
const TONES = ['bg-info-soft text-info-text', 'bg-pink-50 text-pink-700', 'bg-credit-soft text-credit-text'];

export function toneOf(name: string): string {
  let hash = 0;
  for (const char of name) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return TONES[hash % TONES.length];
}

/** The person's photo or, without one (or if it fails to load), their initials. */
export function Avatar({ name, photoUrl, size = 'md', decorative = false, className }: AvatarProps) {
  const [failed, setFailed] = useState(false);
  const classes = cx(
    'inline-flex shrink-0 items-center justify-center overflow-hidden rounded-full',
    SIZES[size],
    className,
  );
  if (photoUrl && !failed) {
    return (
      <img
        src={photoUrl}
        alt={decorative ? '' : name}
        onError={() => setFailed(true)}
        className={cx(classes, 'object-cover')}
      />
    );
  }
  return (
    <span
      role={decorative ? undefined : 'img'}
      aria-label={decorative ? undefined : name}
      aria-hidden={decorative || undefined}
      className={cx(classes, 'font-semibold', toneOf(name))}
    >
      {initialsOf(name)}
    </span>
  );
}
