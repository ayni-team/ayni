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

export const IconWallet = createIcon(
  'IconWallet',
  <>
    <path d="M19 7V4a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v4h-3a2 2 0 0 0 0 4h3a1 1 0 0 0 1-1v-2a1 1 0 0 0-1-1" />
    <path d="M3 5v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1v-4" />
  </>,
);

export const IconAward = createIcon(
  'IconAward',
  <>
    <path d="m15.477 12.89 1.515 8.526a.5.5 0 0 1-.81.47l-3.58-2.687a1 1 0 0 0-1.197 0l-3.586 2.686a.5.5 0 0 1-.81-.469l1.514-8.526" />
    <circle cx="12" cy="8" r="6" />
  </>,
);

export const IconBook = createIcon(
  'IconBook',
  <path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H19a1 1 0 0 1 1 1v18a1 1 0 0 1-1 1H6.5a1 1 0 0 1 0-5H20" />,
);

export const IconStar = createIcon(
  'IconStar',
  <path d="M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z" />,
);

export const IconUser = createIcon(
  'IconUser',
  <>
    <path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" />
    <circle cx="12" cy="7" r="4" />
  </>,
);

export const IconPencil = createIcon(
  'IconPencil',
  <path d="M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z" />,
);

export const IconChart = createIcon(
  'IconChart',
  <>
    <path d="M3 3v16a2 2 0 0 0 2 2h16" />
    <path d="M18 17V9" />
    <path d="M13 17V5" />
    <path d="M8 17v-3" />
  </>,
);

export const IconChevronDown = createIcon('IconChevronDown', <path d="m6 9 6 6 6-6" />);
