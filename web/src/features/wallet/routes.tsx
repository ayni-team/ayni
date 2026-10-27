import { lazy } from 'react';
import type { FeatureRoutes } from '@/shared/frame';

const WalletPage = lazy(() => import('./wallet/WalletPage'));
const BenefitsPage = lazy(() => import('./benefits/BenefitsPage'));

/**
 * The routes and navigation entries of wallet. Adding a screen of this module means a folder
 * here and a line below; App.tsx is not touched.
 */
export const walletRoutes: FeatureRoutes = {
  routes: [
    { path: '/creditos', access: ['STUDENT'], screen: 'FE-30', element: <WalletPage /> },
    { path: '/creditos/beneficios', access: ['STUDENT'], screen: 'FE-33', element: <BenefitsPage /> },
  ],
  navigation: [
    { to: '/creditos', label: 'Créditos', role: 'STUDENT', group: 'learn', order: 30 },
  ],
};
