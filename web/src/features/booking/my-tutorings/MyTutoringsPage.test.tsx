import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/shared/api';
import { fakeServer } from '@/test/fakeServer';
import { ANA, serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import MyTutoringsPage from './MyTutoringsPage';

const COURSE_1_ID = 'b0000000-0000-4000-8000-000000000101';
const COURSE_2_ID = 'b0000000-0000-4000-8000-000000000102';
const TUTOR_1_ID = '22222222-2222-4222-8222-222222222221';
const TUTOR_2_ID = '22222222-2222-4222-8222-222222222222';

const UPCOMING_BOOKING_JOINABLE: Schema<'StudentBooking'> = {
  bookingId: 'd1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e01',
  startsAt: '2026-10-23T21:00:00Z', // 16:00 Lima
  endsAt: '2026-10-23T22:00:00Z',
  hours: 1,
  status: 'CONFIRMED',
  joinable: true,
  catalogItem: { id: COURSE_1_ID, name: 'Estadística Aplicada I' },
  tutor: { id: TUTOR_1_ID, name: 'Julián Arce' },
};

const UPCOMING_BOOKING_STANDARD: Schema<'StudentBooking'> = {
  bookingId: 'd1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e02',
  startsAt: '2026-10-24T01:00:00Z', // 20:00 Lima
  endsAt: '2026-10-24T02:00:00Z',
  hours: 1,
  status: 'CONFIRMED',
  joinable: false,
  catalogItem: { id: COURSE_2_ID, name: 'Algoritmos' },
  tutor: { id: TUTOR_2_ID, name: 'Carla Lazo' },
};

const HISTORY_BOOKINGS: Schema<'StudentBooking'>[] = [
  {
    bookingId: 'd1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e03',
    startsAt: '2026-10-21T21:00:00Z',
    endsAt: '2026-10-21T22:00:00Z',
    hours: 1,
    status: 'COMPLETED',
    joinable: false,
    catalogItem: { id: COURSE_1_ID, name: 'Estadística Aplicada I' },
    tutor: { id: TUTOR_1_ID, name: 'Julián Arce' },
  },
  {
    bookingId: 'd1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e04',
    startsAt: '2026-10-19T23:00:00Z',
    endsAt: '2026-10-20T00:00:00Z',
    hours: 1,
    status: 'NO_SHOW',
    joinable: false,
    catalogItem: { id: COURSE_2_ID, name: 'Matemática Discreta' },
    tutor: { id: TUTOR_2_ID, name: 'Sofía Cornejo' },
  },
  {
    bookingId: 'd1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e05',
    startsAt: '2026-10-16T15:00:00Z',
    endsAt: '2026-10-16T16:00:00Z',
    hours: 1,
    status: 'CANCELLED',
    joinable: false,
    catalogItem: { id: COURSE_2_ID, name: 'Algoritmos' },
    tutor: { id: TUTOR_2_ID, name: 'Bruno Salas' },
  },
];

const UPCOMING_RESPONSE: Schema<'StudentBookings'> = {
  view: 'UPCOMING',
  timezone: 'America/Lima',
  page: 0,
  size: 5,
  total: 2,
  items: [UPCOMING_BOOKING_JOINABLE, UPCOMING_BOOKING_STANDARD],
};

const HISTORY_RESPONSE: Schema<'StudentBookings'> = {
  view: 'HISTORY',
  timezone: 'America/Lima',
  page: 0,
  size: 5,
  total: 3,
  items: HISTORY_BOOKINGS,
};

function setupServer() {
  const server = fakeServer();
  serveFrame(server, ANA);
  signIn('STUDENT');
  return server;
}

describe('FE-12 MyTutoringsPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the header and upcoming tutorings with correct badges and format', async () => {
    const server = setupServer();
    server.on('GET', '/api/v1/bookings/mine', (req) => {
      const view = req.query.get('view');
      if (view === 'HISTORY') return { body: HISTORY_RESPONSE };
      return { body: UPCOMING_RESPONSE };
    });

    renderWithProviders(<MyTutoringsPage />, { route: '/mis-tutorias' });

    expect(await screen.findByRole('heading', { level: 1, name: 'Mis tutorías' })).toBeInTheDocument();
    expect(screen.getByText('Las que recibes. Horas en hora de Lima.')).toBeInTheDocument();

    // Upcoming list
    expect(await screen.findByText('Estadística Aplicada I')).toBeInTheDocument();
    expect(screen.getByText('con Julián Arce')).toBeInTheDocument();
    expect(screen.getByText('En curso')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Entrar' })).toBeInTheDocument();

    expect(screen.getByText('Algoritmos')).toBeInTheDocument();
    expect(screen.getByText('con Carla Lazo')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeInTheDocument();
  });

  it('navigates to the session when entering a joinable tutoring', async () => {
    const server = setupServer();
    server
      .on('GET', '/api/v1/bookings/mine', { body: UPCOMING_RESPONSE })
      .on('GET', '/api/v1/sessions', {
        body: {
          sessionId: '55555555-5555-4555-8555-555555555555',
          bookingId: UPCOMING_BOOKING_JOINABLE.bookingId,
        },
      });

    const { user, location } = renderWithProviders(<MyTutoringsPage />, { route: '/mis-tutorias' });

    const enterBtn = await screen.findByRole('button', { name: 'Entrar' });
    await user.click(enterBtn);

    await waitFor(() => {
      expect(location()).toBe('/sesion/55555555-5555-4555-8555-555555555555');
    });
  });

  it('cancels immediately (204) when cancelling outside 12 hours', async () => {
    const server = setupServer();
    server
      .on('GET', '/api/v1/bookings/mine', { body: UPCOMING_RESPONSE })
      .on('DELETE', `/api/v1/bookings/${UPCOMING_BOOKING_STANDARD.bookingId}`, { status: 204 });

    const { user } = renderWithProviders(<MyTutoringsPage />, { route: '/mis-tutorias' });

    const cancelBtn = await screen.findByRole('button', { name: 'Cancelar' });
    await user.click(cancelBtn);

    expect(
      await screen.findByText('Cancelaste la tutoría y te devolvimos 1 crédito'),
    ).toBeInTheDocument();
  });

  it('handles late cancellation (409) with two-step confirmation dialog', async () => {
    const server = setupServer();
    const lateBookingId = UPCOMING_BOOKING_STANDARD.bookingId;

    let deleteAttempt = 0;
    server
      .on('GET', '/api/v1/bookings/mine', { body: UPCOMING_RESPONSE })
      .on('DELETE', `/api/v1/bookings/${lateBookingId}`, (req) => {
        deleteAttempt++;
        if (req.query.get('confirmLate') === 'true') {
          return { status: 204 };
        }
        return {
          status: 409,
          body: {
            bookingId: lateBookingId,
            late: true,
            refundWillBeIssued: false,
            confirmationRequired: true,
          },
        };
      });

    const { user } = renderWithProviders(<MyTutoringsPage />, { route: '/mis-tutorias' });

    const cancelBtn = await screen.findByRole('button', { name: 'Cancelar' });
    await user.click(cancelBtn);

    // Dialog opens with title and message
    expect(await screen.findByRole('heading', { level: 2, name: 'Faltan menos de 12 horas' })).toBeInTheDocument();
    expect(screen.getByText(/no se te devolverá el crédito/i)).toBeInTheDocument();

    // Click "Cancelar igual" to confirm late cancellation
    const confirmLateBtn = screen.getByRole('button', { name: 'Cancelar igual' });
    await user.click(confirmLateBtn);

    // Banner indicates successful cancellation without refund
    expect(await screen.findByText('Cancelaste la tutoría')).toBeInTheDocument();
    expect(deleteAttempt).toBe(2);
  });

  it('dismisses late cancellation dialog without cancelling when clicking "No, mantenerla"', async () => {
    const server = setupServer();
    const lateBookingId = UPCOMING_BOOKING_STANDARD.bookingId;

    server
      .on('GET', '/api/v1/bookings/mine', { body: UPCOMING_RESPONSE })
      .on('DELETE', `/api/v1/bookings/${lateBookingId}`, {
        status: 409,
        body: {
          bookingId: lateBookingId,
          late: true,
          refundWillBeIssued: false,
          confirmationRequired: true,
        },
      });

    const { user } = renderWithProviders(<MyTutoringsPage />, { route: '/mis-tutorias' });

    const cancelBtn = await screen.findByRole('button', { name: 'Cancelar' });
    await user.click(cancelBtn);

    expect(await screen.findByRole('heading', { level: 2, name: 'Faltan menos de 12 horas' })).toBeInTheDocument();

    const keepBtn = screen.getByRole('button', { name: 'No, mantenerla' });
    await user.click(keepBtn);

    await waitFor(() => {
      expect(screen.queryByRole('heading', { level: 2, name: 'Faltan menos de 12 horas' })).not.toBeInTheDocument();
    });
  });

  it('switches to History tab and displays past sessions with "Repasar" action', async () => {
    const server = setupServer();
    server
      .on('GET', '/api/v1/bookings/mine', (req) => {
        const view = req.query.get('view');
        if (view === 'HISTORY') return { body: HISTORY_RESPONSE };
        return { body: UPCOMING_RESPONSE };
      })
      .on('GET', '/api/v1/sessions', {
        body: {
          sessionId: '77777777-7777-4777-8777-777777777777',
          bookingId: HISTORY_BOOKINGS[0].bookingId,
        },
      });

    const { user, location } = renderWithProviders(<MyTutoringsPage />, { route: '/mis-tutorias' });

    const historyTab = await screen.findByRole('tab', { name: 'Historial' });
    await user.click(historyTab);

    // History badges
    expect(await screen.findByText('Completada')).toBeInTheDocument();
    expect(screen.getByText('No asistió')).toBeInTheDocument();
    expect(screen.getByText('Cancelada')).toBeInTheDocument();

    // "Repasar" button exists for completed tutoring
    const reviewBtn = screen.getByRole('button', { name: 'Repasar' });
    await user.click(reviewBtn);

    await waitFor(() => {
      expect(location()).toBe('/sesion/77777777-7777-4777-8777-777777777777/repaso');
    });
  });

  it('shows empty state when student has no tutorings', async () => {
    const server = setupServer();
    server.on('GET', '/api/v1/bookings/mine', {
      body: {
        view: 'UPCOMING',
        timezone: 'America/Lima',
        page: 0,
        size: 20,
        total: 0,
        items: [],
      },
    });

    const { user, location } = renderWithProviders(<MyTutoringsPage />, { route: '/mis-tutorias' });

    expect(await screen.findByText('Todavía no tienes tutorías')).toBeInTheDocument();
    expect(screen.getByText('Busca una en Buscar.')).toBeInTheDocument();

    const searchLink = screen.getByRole('link', { name: 'Buscar tutoría' });
    await user.click(searchLink);
    expect(location()).toBe('/buscar');
  });

  it('shows error state on network or server failure and allows retrying', async () => {
    const server = setupServer();
    let failed = true;
    server.on('GET', '/api/v1/bookings/mine', () => {
      if (failed) return { status: 500 };
      return { body: UPCOMING_RESPONSE };
    });

    const { user } = renderWithProviders(<MyTutoringsPage />, { route: '/mis-tutorias' });

    expect(await screen.findByRole('heading', { level: 2, name: 'No pudimos cargar esto' })).toBeInTheDocument();

    failed = false;
    const retryBtn = screen.getByRole('button', { name: 'Reintentar' });
    await user.click(retryBtn);

    expect(await screen.findByText('Estadística Aplicada I')).toBeInTheDocument();
  });

  it('renders pagination when total items exceed 5 and handles page navigation', async () => {
    const server = setupServer();
    let requestedPage = 0;

    server.on('GET', '/api/v1/bookings/mine', (req) => {
      requestedPage = Number(req.query.get('page') ?? '0');
      return {
        body: {
          view: 'UPCOMING',
          timezone: 'America/Lima',
          page: requestedPage,
          size: 5,
          total: 10,
          items: [UPCOMING_BOOKING_JOINABLE],
        },
      };
    });

    const { user } = renderWithProviders(<MyTutoringsPage />, { route: '/mis-tutorias' });

    // Shows 1 of 2
    expect(await screen.findByText('Página 1 de 2')).toBeInTheDocument();

    const nextBtn = screen.getByRole('button', { name: /Siguiente/i });
    expect(nextBtn).toBeEnabled();

    await user.click(nextBtn);

    expect(await screen.findByText('Página 2 de 2')).toBeInTheDocument();
    expect(requestedPage).toBe(1);
  });

  it('does not offer a late cancellation for a 409 that is not the late warning', async () => {
    const server = setupServer();
    const bookingId = UPCOMING_BOOKING_STANDARD.bookingId;
    server
      .on('GET', '/api/v1/bookings/mine', { body: UPCOMING_RESPONSE })
      .on('DELETE', `/api/v1/bookings/${bookingId}`, {
        status: 409,
        body: { status: 409, message: 'The session already started: the booking can no longer be cancelled' },
      });

    const { user } = renderWithProviders(<MyTutoringsPage />, { route: '/mis-tutorias' });
    await user.click(await screen.findByRole('button', { name: 'Cancelar' }));

    expect(await screen.findByText('Esta tutoría ya no se puede cancelar.')).toBeInTheDocument();
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByText(/already started/)).not.toBeInTheDocument();
    expect(server.requests('DELETE')).toHaveLength(1);
  });

  it('closes the late cancellation dialog with Escape, cancelling nothing', async () => {
    const server = setupServer();
    const bookingId = UPCOMING_BOOKING_STANDARD.bookingId;
    server
      .on('GET', '/api/v1/bookings/mine', { body: UPCOMING_RESPONSE })
      .on('DELETE', `/api/v1/bookings/${bookingId}`, {
        status: 409,
        body: { bookingId, late: true, refundWillBeIssued: false, confirmationRequired: true },
      });

    const { user } = renderWithProviders(<MyTutoringsPage />, { route: '/mis-tutorias' });
    await user.click(await screen.findByRole('button', { name: 'Cancelar' }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();

    await user.keyboard('{Escape}');

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(server.requests('DELETE')).toHaveLength(1);
  });

  it('stays on the page when the session of a past tutoring is not found', async () => {
    const server = setupServer();
    server
      .on('GET', '/api/v1/bookings/mine', (req) =>
        req.query.get('view') === 'HISTORY' ? { body: HISTORY_RESPONSE } : { body: UPCOMING_RESPONSE },
      )
      .on('GET', '/api/v1/sessions', { status: 404, body: { status: 404, message: 'No session' } });

    const { user, location } = renderWithProviders(<MyTutoringsPage />, { route: '/mis-tutorias' });
    await user.click(await screen.findByRole('tab', { name: 'Historial' }));
    await user.click(await screen.findByRole('button', { name: 'Repasar' }));

    expect(await screen.findByText('No encontramos lo que buscas')).toBeInTheDocument();
    expect(location()).toBe('/mis-tutorias');
  });
});
