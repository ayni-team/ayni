import { lazy } from 'react';
import type { FeatureRoutes } from '@/shared/frame';

const SignInPage = lazy(() => import('./sign-in/SignInPage'));
const ConfirmAccessPage = lazy(() => import('./confirm-access/ConfirmAccessPage'));
const OnboardingPage = lazy(() => import('./onboarding/OnboardingPage'));
const ProfilePage = lazy(() => import('./profile/ProfilePage'));
const AdminPage = lazy(() => import('./admin/AdminPage'));

/**
 * The routes and navigation entries of identity. Adding a screen of this module means a folder
 * here and a line below; App.tsx is not touched.
 */
export const identityRoutes: FeatureRoutes = {
  routes: [
    { path: '/ingresar', access: 'public', screen: 'FE-01', element: <SignInPage /> },
    { path: '/access/confirm', access: 'public', screen: 'FE-02', element: <ConfirmAccessPage /> },
    { path: '/onboarding', access: ['STUDENT'], screen: 'FE-03', element: <OnboardingPage /> },
    { path: '/perfil', access: ['STUDENT'], screen: 'FE-04', element: <ProfilePage /> },
    { path: '/admin', access: ['PLATFORM_ADMIN'], screen: 'Administración', element: <AdminPage /> },
  ],
};
