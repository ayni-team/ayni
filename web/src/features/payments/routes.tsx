import { lazy } from 'react';
import type { FeatureRoutes } from '@/shared/frame';

const BuyCreditsPage = lazy(() => import('./buy/BuyCreditsPage'));
const PurchasesPage = lazy(() => import('./purchases/PurchasesPage'));
const PurchaseDetailPage = lazy(() => import('./purchases/PurchaseDetailPage'));

/**
 * The routes and navigation entries of payments. Adding a screen of this module means a folder
 * here and a line below; App.tsx is not touched.
 */
export const paymentsRoutes: FeatureRoutes = {
  routes: [
    { path: '/creditos/comprar', access: ['STUDENT'], screen: 'FE-31', element: <BuyCreditsPage /> },
    { path: '/creditos/compras', access: ['STUDENT'], screen: 'FE-32', element: <PurchasesPage /> },
    { path: '/creditos/compras/:id', access: ['STUDENT'], screen: 'FE-32', element: <PurchaseDetailPage /> },
  ],
};
