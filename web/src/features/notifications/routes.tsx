import { lazy } from 'react';
import type { FeatureRoutes } from '@/shared/frame';

const NoticesPage = lazy(() => import('./notices/NoticesPage'));

/**
 * The routes and navigation entries of notifications. Adding a screen of this module means a folder
 * here and a line below; App.tsx is not touched.
 */
export const notificationsRoutes: FeatureRoutes = {
  routes: [{ path: '/avisos', access: ['STUDENT', 'COORDINATOR'], screen: 'FE-60', element: <NoticesPage /> }],
};
