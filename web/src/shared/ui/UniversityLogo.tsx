import { useState } from 'react';
import { cx } from './cx';

export interface UniversityLogoProps {
  university: { name?: string; code?: string; logoUrl?: string } | null | undefined;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

const SIZES = { sm: 'h-8', md: 'h-10', lg: 'h-14' };

/**
 * The logo the university declares (`logoUrl`; the demo ones are in `public/logos/`). Without one,
 * or while it fails, its code on the university's colour.
 */
export function UniversityLogo({ university, size = 'md', className }: UniversityLogoProps) {
  const [failed, setFailed] = useState<string | null>(null);
  const name = university?.name ?? university?.code ?? 'Universidad';
  const logo = university?.logoUrl;

  if (logo && failed !== logo) {
    return (
      <img
        src={logo}
        alt={`Logo de ${name}`}
        onError={() => setFailed(logo)}
        className={cx(SIZES[size], 'w-auto object-contain', className)}
      />
    );
  }
  return (
    <span
      role="img"
      aria-label={`Logo de ${name}`}
      className={cx(
        SIZES[size],
        'inline-flex aspect-square items-center justify-center rounded-lg bg-brand px-1 text-xs font-bold text-brand-secondary',
        className,
      )}
    >
      {university?.code ?? name.slice(0, 3).toUpperCase()}
    </span>
  );
}
