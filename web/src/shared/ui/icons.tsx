import type { ReactNode, SVGProps } from 'react';

/**
 * The few icons the frame and the components need, drawn on a 24 × 24 grid with a 2 px stroke in
 * the style of Lucide (ISC), which the design uses. They are decorative unless given a `label`:
 * a button with an icon names itself with its own text or `aria-label`.
 */
export interface IconProps extends Omit<SVGProps<SVGSVGElement>, 'children'> {
  label?: string;
}

function createIcon(name: string, shapes: ReactNode) {
  function Icon({ label, className = 'h-5 w-5', ...props }: IconProps) {
    return (
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        className={className}
        role={label ? 'img' : undefined}
        aria-label={label}
        aria-hidden={label ? undefined : true}
        focusable="false"
        {...props}
      >
        {shapes}
      </svg>
    );
  }
  Icon.displayName = name;
  return Icon;
}

export const IconBell = createIcon(
  'IconBell',
  <>
    <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
    <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
  </>,
);
export const IconMenu = createIcon(
  'IconMenu',
  <>
    <path d="M4 6h16" />
    <path d="M4 12h16" />
    <path d="M4 18h16" />
  </>,
);
export const IconClose = createIcon(
  'IconClose',
  <>
    <path d="M18 6 6 18" />
    <path d="m6 6 12 12" />
  </>,
);
export const IconChevronLeft = createIcon('IconChevronLeft', <path d="m15 18-6-6 6-6" />);
export const IconChevronRight = createIcon('IconChevronRight', <path d="m9 18 6-6-6-6" />);
export const IconLogOut = createIcon(
  'IconLogOut',
  <>
    <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
    <path d="m16 17 5-5-5-5" />
    <path d="M21 12H9" />
  </>,
);
export const IconAlert = createIcon(
  'IconAlert',
  <>
    <circle cx="12" cy="12" r="10" />
    <path d="M12 8v4" />
    <path d="M12 16h.01" />
  </>,
);
export const IconCheck = createIcon('IconCheck', <path d="M20 6 9 17l-5-5" />);
export const IconInfo = createIcon(
  'IconInfo',
  <>
    <circle cx="12" cy="12" r="10" />
    <path d="M12 16v-4" />
    <path d="M12 8h.01" />
  </>,
);
export const IconSearch = createIcon(
  'IconSearch',
  <>
    <circle cx="11" cy="11" r="7" />
    <path d="m21 21-4.3-4.3" />
  </>,
);
export const IconCalendar = createIcon(
  'IconCalendar',
  <>
    <rect x="3" y="4" width="18" height="18" rx="2" />
    <path d="M16 2v4" />
    <path d="M8 2v4" />
    <path d="M3 10h18" />
  </>,
);
export const IconClock = createIcon(
  'IconClock',
  <>
    <circle cx="12" cy="12" r="10" />
    <path d="M12 6v6l4 2" />
  </>,
);
export const IconCoins = createIcon(
  'IconCoins',
  <>
    <circle cx="8" cy="8" r="6" />
    <path d="M18.09 10.37A6 6 0 1 1 10.34 18" />
    <path d="M7 6h1v4" />
  </>,
);
export const IconConstruction = createIcon(
  'IconConstruction',
  <>
    <rect x="2" y="6" width="20" height="8" rx="1" />
    <path d="M17 14v7" />
    <path d="M7 14v7" />
    <path d="M17 3v3" />
    <path d="M7 3v3" />
    <path d="M10 14 2.3 6.3" />
    <path d="m14 6 7.7 7.7" />
  </>,
);
