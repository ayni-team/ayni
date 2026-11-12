import { lazy } from 'react';
import type { FeatureRoutes } from '@/shared/frame';

const SessionPage = lazy(() => import('./session/SessionPage'));
const SessionReviewPage = lazy(() => import('./review/SessionReviewPage'));
const PracticePage = lazy(() => import('./practice/PracticePage'));

/**
 * The routes and navigation entries of sessions. Adding a screen of this module means a folder
 * here and a line below; App.tsx is not touched.
 */
export const sessionsRoutes: FeatureRoutes = {
  routes: [
    { path: '/sesion/:id', access: ['STUDENT'], screen: 'FE-20 a FE-23', element: <SessionPage /> },
    { path: '/sesion/:id/repaso', access: ['STUDENT'], screen: 'FE-24', element: <SessionReviewPage /> },
    // A tool of the team, not a screen of the design: no entry in any menu.
    { path: '/practica', access: ['STUDENT'], screen: 'Sala de práctica', element: <PracticePage /> },
  ],
};
