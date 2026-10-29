import { lazy } from 'react';
import type { FeatureRoutes } from '@/shared/frame';

const IndicatorsPage = lazy(() => import('./indicators/IndicatorsPage'));

/**
 * The routes and navigation entries of analytics. Adding a screen of this module means a folder
 * here and a line below; App.tsx is not touched.
 */
export const analyticsRoutes: FeatureRoutes = {
  routes: [{ path: '/coordinador/indicadores', access: ['COORDINATOR'], screen: 'FE-53', element: <IndicatorsPage /> }],
  navigation: [
    { to: '/coordinador/indicadores', label: 'Indicadores', role: 'COORDINATOR', group: 'coordinate', order: 30 },
  ],
};
