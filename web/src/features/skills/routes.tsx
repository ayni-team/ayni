import { lazy } from 'react';
import type { FeatureRoutes } from '@/shared/frame';

const MySkillsPage = lazy(() => import('./my-skills/MySkillsPage'));

/**
 * The routes and navigation entries of skills. Adding a screen of this module means a folder
 * here and a line below; App.tsx is not touched.
 */
export const skillsRoutes: FeatureRoutes = {
  routes: [{ path: '/tutor/habilidades', access: ['STUDENT'], screen: 'FE-42', element: <MySkillsPage /> }],
  navigation: [{ to: '/tutor/habilidades', label: 'Habilidades', role: 'STUDENT', group: 'teach', order: 30 }],
};
