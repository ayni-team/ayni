import { lazy } from 'react';
import type { FeatureRoutes } from '@/shared/frame';

const SearchPage = lazy(() => import('./search/SearchPage'));

/**
 * The routes and navigation entries of matching. Adding a screen of this module means a folder
 * here and a line below; App.tsx is not touched.
 */
export const matchingRoutes: FeatureRoutes = {
  routes: [
    { path: '/buscar', access: ['STUDENT'], screen: 'FE-10', element: <SearchPage /> },
  ],
  navigation: [
    { to: '/buscar', label: 'Buscar', role: 'STUDENT', group: 'learn', order: 10 },
  ],
};
