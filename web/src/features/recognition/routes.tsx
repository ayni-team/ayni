import { lazy } from 'react';
import type { FeatureRoutes } from '@/shared/frame';

const RecognitionProgressPage = lazy(() => import('./progress/RecognitionProgressPage'));
const RecognitionRequestsPage = lazy(() => import('./requests/RecognitionRequestsPage'));
const RecognitionCasePage = lazy(() => import('./case/RecognitionCasePage'));
const RecognitionRulePage = lazy(() => import('./rule/RecognitionRulePage'));

/**
 * The routes and navigation entries of recognition. Adding a screen of this module means a folder
 * here and a line below; App.tsx is not touched.
 */
export const recognitionRoutes: FeatureRoutes = {
  routes: [
    { path: '/reconocimiento', access: ['STUDENT'], screen: 'FE-34', element: <RecognitionProgressPage /> },
    { path: '/coordinador/reconocimiento', access: ['COORDINATOR'], screen: 'FE-50', element: <RecognitionRequestsPage /> },
    { path: '/coordinador/reconocimiento/:id', access: ['COORDINATOR'], screen: 'FE-51', element: <RecognitionCasePage /> },
    { path: '/coordinador/regla', access: ['COORDINATOR'], screen: 'FE-52', element: <RecognitionRulePage /> },
  ],
  navigation: [
    { to: '/reconocimiento', label: 'Reconocimiento', role: 'STUDENT', group: 'learn', order: 40 },
    { to: '/coordinador/reconocimiento', label: 'Reconocimiento', role: 'COORDINATOR', group: 'coordinate', order: 10 },
    { to: '/coordinador/regla', label: 'Regla', role: 'COORDINATOR', group: 'coordinate', order: 20 },
  ],
};
