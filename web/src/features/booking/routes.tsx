import { lazy } from 'react';
import type { FeatureRoutes } from '@/shared/frame';

const BookPage = lazy(() => import('./book/BookPage'));
const MyTutoringsPage = lazy(() => import('./my-tutorings/MyTutoringsPage'));
const AvailabilityPage = lazy(() => import('./availability/AvailabilityPage'));
const AgendaPage = lazy(() => import('./agenda/AgendaPage'));

/**
 * The routes and navigation entries of booking. Adding a screen of this module means a folder
 * here and a line below; App.tsx is not touched.
 */
export const bookingRoutes: FeatureRoutes = {
  routes: [
    { path: '/reservar', access: ['STUDENT'], screen: 'FE-11', element: <BookPage /> },
    { path: '/mis-tutorias', access: ['STUDENT'], screen: 'FE-12', element: <MyTutoringsPage /> },
    { path: '/tutor/disponibilidad', access: ['STUDENT'], screen: 'FE-40', element: <AvailabilityPage /> },
    { path: '/tutor/agenda', access: ['STUDENT'], screen: 'FE-41', element: <AgendaPage /> },
  ],
  navigation: [
    { to: '/mis-tutorias', label: 'Mis tutorías', role: 'STUDENT', group: 'learn', order: 20 },
    { to: '/tutor/disponibilidad', label: 'Disponibilidad', role: 'STUDENT', group: 'teach', order: 20 },
    { to: '/tutor/agenda', label: 'Agenda', role: 'STUDENT', group: 'teach', order: 10 },
  ],
};
