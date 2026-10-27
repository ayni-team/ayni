import { lazy } from 'react';
import type { FeatureRoutes } from '@/shared/frame';

const ReputationPage = lazy(() => import('./reputation/ReputationPage'));

/**
 * The routes and navigation entries of reputation. Adding a screen of this module means a folder
 * here and a line below; App.tsx is not touched.
 */
export const reputationRoutes: FeatureRoutes = {
  routes: [
    { path: '/tutor/reputacion', access: ['STUDENT'], screen: 'FE-43', element: <ReputationPage /> },
  ],
  navigation: [
    { to: '/tutor/reputacion', label: 'Reputación', role: 'STUDENT', group: 'teach', order: 40 },
  ],
};
