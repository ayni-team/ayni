import type { ReactNode } from 'react';
import type { Role } from '../session/roles';

/**
 * What a feature contributes to the application: its routes and its entries in the navigation.
 * Each feature declares them in its own `routes.tsx`; `App.tsx` composes them once, so adding a
 * screen never touches `App.tsx` and two people never conflict there.
 */
export interface AppRoute {
  path: string;
  /** `public` needs no session; otherwise the roles that may enter. */
  access: 'public' | readonly Role[];
  element: ReactNode;
  /** The screen id of the guide ("FE-12"), for whoever reads the routes. */
  screen?: string;
}

/** The groups of the navigation: a student learns and teaches with the same account. */
export type NavGroup = 'learn' | 'teach' | 'coordinate';

export interface NavEntry {
  to: string;
  label: string;
  role: Role;
  group: NavGroup;
  /** Position inside its group, lowest first. */
  order: number;
}

export interface FeatureRoutes {
  routes: readonly AppRoute[];
  navigation?: readonly NavEntry[];
}

export const GROUP_LABELS: Record<NavGroup, string> = {
  learn: 'Aprender',
  teach: 'Como tutor',
  coordinate: 'Coordinación',
};

const GROUP_ORDER: readonly NavGroup[] = ['learn', 'teach', 'coordinate'];

/** The entries of a role, grouped and in order. Empty groups are left out. */
export function navigationFor(
  role: Role,
  entries: readonly NavEntry[],
): { group: NavGroup; label: string; entries: NavEntry[] }[] {
  return GROUP_ORDER.map((group) => ({
    group,
    label: GROUP_LABELS[group],
    entries: entries.filter((entry) => entry.role === role && entry.group === group).sort((a, b) => a.order - b.order),
  })).filter((section) => section.entries.length > 0);
}
