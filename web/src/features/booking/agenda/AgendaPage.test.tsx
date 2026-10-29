import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/shared/api';
import { UPC, signIn } from '@/test/fixtures';
import { fakeServer } from '@/test/fakeServer';
import { renderWithProviders } from '@/test/render';
import AgendaPage from './AgendaPage';

type TutorAgenda = Schema<'TutorAgenda'>;

type TutorBooking = Schema<'TutorBooking'>;

const BOOKING_NOW = 'd1000000-0000-4000-8000-000000000001';

const BOOKING_LATER = 'd1000000-0000-4000-8000-000000000002';

const BOOKING_TUESDAY = 'd1000000-0000-4000-8000-000000000003';

const SESSION_ID = '5b1f0c2a-4d6e-4c7d-8e9f-0a1b2c3d4e5f';

function booked(overrides: Partial<TutorBooking> = {}): TutorBooking {
  return {
    bookingId: BOOKING_LATER,

    startsAt: '2026-10-26T21:00:00Z',

    endsAt: '2026-10-26T22:00:00Z',

    hours: 1,

    status: 'CONFIRMED',

    student: {
      id: '4b0c9d9e-1f55-4d0e-9a5e-2f3a7c1b8d10',

      name: 'Ana Torres',

      absenceCount: 1,
    },

    catalogItem: {
      id: 'b0000000-0000-4000-8000-000000000101',

      name: 'Diseño y Patrones de Software',
    },

    needDescription: 'Repasar los patrones Strategy y Observer con un ejercicio para la PC2.',

    joinable: false,

    ...overrides,
  };
}

function agendaFixture(): TutorAgenda {
  return {
    from: '2026-10-23',

    to: '2026-10-29',

    timezone: 'America/Lima',

    lines: [
      {
        kind: 'BOOKED',

        startsAt: '2026-10-23T23:00:00Z',

        endsAt: '2026-10-24T00:00:00Z',

        date: '2026-10-23',

        startTime: '18:00:00',

        endTime: '19:00:00',

        booking: booked({
          bookingId: BOOKING_NOW,

          startsAt: '2026-10-23T23:00:00Z',

          endsAt: '2026-10-24T00:00:00Z',

          student: {
            id: '4b0c9d9e-1f55-4d0e-9a5e-2f3a7c1b8d11',

            name: 'Carla Mendoza',

            absenceCount: 0,
          },

          catalogItem: {
            id: 'b0000000-0000-4000-8000-000000000102',

            name: 'Introducción a los Algoritmos',
          },

          needDescription: 'No entiendo cómo armar el pseudocódigo de un bucle anidado.',

          joinable: true,
        }),
      },

      {
        kind: 'BOOKED',

        startsAt: '2026-10-26T21:00:00Z',

        endsAt: '2026-10-26T22:00:00Z',

        date: '2026-10-26',

        startTime: '16:00:00',

        endTime: '17:00:00',

        booking: booked(),
      },

      {
        kind: 'FREE',

        startsAt: '2026-10-26T22:00:00Z',

        endsAt: '2026-10-26T23:00:00Z',

        date: '2026-10-26',

        startTime: '17:00:00',

        endTime: '18:00:00',
      },

      {
        kind: 'FREE',

        startsAt: '2026-10-26T23:00:00Z',

        endsAt: '2026-10-27T00:00:00Z',

        date: '2026-10-26',

        startTime: '18:00:00',

        endTime: '19:00:00',
      },

      {
        kind: 'BOOKED',

        startsAt: '2026-10-27T22:00:00Z',

        endsAt: '2026-10-27T23:00:00Z',

        date: '2026-10-27',

        startTime: '17:00:00',

        endTime: '18:00:00',

        booking: booked({
          bookingId: BOOKING_TUESDAY,

          startsAt: '2026-10-27T22:00:00Z',

          endsAt: '2026-10-27T23:00:00Z',

          catalogItem: {
            id: 'b0000000-0000-4000-8000-000000000103',

            name: 'Algoritmos',
          },
        }),
      },

      {
        kind: 'FREE',

        startsAt: '2026-10-27T23:00:00Z',

        endsAt: '2026-10-28T00:00:00Z',

        date: '2026-10-27',

        startTime: '18:00:00',

        endTime: '19:00:00',
      },
    ],
  };
}

function serveAgenda(initial = agendaFixture()) {
  let agenda = initial;

  const server = fakeServer()
    .on('GET', '/api/v1/university', {
      body: UPC,
    })
    .on('GET', '/api/v1/tutor/agenda', () => ({
      body: agenda,
    }));

  return {
    server,

    getAgenda() {
      return agenda;
    },

    setAgenda(next: TutorAgenda) {
      agenda = next;
    },
  };
}

describe('FE-41 tutor agenda', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('distinguishes booked and free hours and shows the student context', async () => {
    signIn('STUDENT');

    serveAgenda();

    renderWithProviders(<AgendaPage />, {
      route: '/tutor/agenda',
    });

    expect(
      await screen.findByRole('heading', {
        name: 'Agenda',
      }),
    ).toBeInTheDocument();

    expect(await screen.findByText('Carla Mendoza')).toBeInTheDocument();

    expect(screen.getByText('Introducción a los Algoritmos')).toBeInTheDocument();

    expect(screen.getByText(/No entiendo cómo armar el pseudocódigo/)).toBeInTheDocument();

    expect(screen.getAllByText('1 inasistencia').length).toBeGreaterThan(0);

    expect(screen.getAllByText('Hora libre · visible en Buscar')).toHaveLength(3);

    expect(
      screen.getByRole('link', {
        name: 'Disponibilidad',
      }),
    ).toHaveAttribute('href', '/tutor/disponibilidad');
  });

  it('resolves the session id before entering a joinable booking', async () => {
    signIn('STUDENT');

    const state = serveAgenda();

    state.server.on('GET', '/api/v1/sessions', {
      body: {
        sessionId: SESSION_ID,

        bookingId: BOOKING_NOW,

        status: 'SCHEDULED',

        joinOpensAt: '2026-10-23T22:45:00Z',

        scheduledEnd: '2026-10-24T00:00:00Z',
      },
    });

    const { user, location } = renderWithProviders(<AgendaPage />, {
      route: '/tutor/agenda',
    });

    const row = await screen.findByRole('article', {
      name: 'Tutoría con Carla Mendoza a las 18:00',
    });

    expect(within(row).getByText('Ahora')).toBeInTheDocument();

    await user.click(
      within(row).getByRole('button', {
        name: 'Entrar',
      }),
    );

    await waitFor(() => expect(location()).toBe(`/sesion/${SESSION_ID}`));

    expect(state.server.requests('GET', '/api/v1/sessions')).toHaveLength(1);

    expect(state.server.requests('GET', '/api/v1/sessions')[0].query.get('bookingId')).toBe(BOOKING_NOW);
  });

  it('warns before a late tutor cancellation and only cancels after confirmLate=true', async () => {
    signIn('STUDENT');

    const state = serveAgenda();

    state.server.on('DELETE', `/api/v1/bookings/${BOOKING_LATER}`, (request) => {
      if (request.query.get('confirmLate') !== 'true') {
        return {
          status: 409,

          body: {
            bookingId: BOOKING_LATER,

            startsAt: '2026-10-26T21:00:00Z',

            late: true,

            refundWillBeIssued: true,

            incidentWillBeRecorded: true,

            confirmationRequired: true,

            confirmationRequest: `DELETE /api/v1/bookings/${BOOKING_LATER}?confirmLate=true`,
          },
        };
      }

      state.setAgenda({
        ...state.getAgenda(),

        lines: (state.getAgenda().lines ?? []).filter((line) => line.booking?.bookingId !== BOOKING_LATER),
      });

      return {
        status: 204,
      };
    });

    const { user } = renderWithProviders(<AgendaPage />, {
      route: '/tutor/agenda',
    });

    const row = await screen.findByRole('article', {
      name: 'Tutoría con Ana Torres a las 16:00',
    });

    await user.click(
      within(row).getByRole('button', {
        name: 'Cancelar',
      }),
    );

    const dialog = await screen.findByRole('dialog', {
      name: 'Faltan menos de 12 horas',
    });

    expect(within(dialog).getByText(/quedará registrada como una incidencia tuya/)).toBeInTheDocument();

    expect(within(dialog).getByText(/Ana Torres recupera su crédito/)).toBeInTheDocument();

    expect(state.server.requests('DELETE', `/api/v1/bookings/${BOOKING_LATER}`)).toHaveLength(1);

    expect(state.server.requests('DELETE', `/api/v1/bookings/${BOOKING_LATER}`)[0].query.get('confirmLate')).toBeNull();

    expect(
      screen.getByRole('article', {
        name: 'Tutoría con Ana Torres a las 16:00',
      }),
    ).toBeInTheDocument();

    await user.click(
      within(dialog).getByRole('button', {
        name: 'Cancelar igual',
      }),
    );

    expect(await screen.findByText('Tutoría cancelada.')).toBeInTheDocument();

    expect(state.server.requests('DELETE', `/api/v1/bookings/${BOOKING_LATER}`)).toHaveLength(2);

    expect(state.server.requests('DELETE', `/api/v1/bookings/${BOOKING_LATER}`)[1].query.get('confirmLate')).toBe(
      'true',
    );

    await waitFor(() =>
      expect(
        screen.queryByRole('article', {
          name: 'Tutoría con Ana Torres a las 16:00',
        }),
      ).not.toBeInTheDocument(),
    );
  });

  it('pages through the tutor history', async () => {
    signIn('STUDENT');

    const state = serveAgenda();

    state.server.on('GET', '/api/v1/tutor/agenda/history', (request) => {
      const page = Number(request.query.get('page') ?? '0');

      const firstPage: TutorBooking[] = [
        booked({
          bookingId: 'h0000000-0000-4000-8000-000000000001',

          status: 'COMPLETED',

          startsAt: '2026-10-21T21:00:00Z',

          endsAt: '2026-10-21T22:00:00Z',

          student: {
            id: 'h1000000-0000-4000-8000-000000000001',

            name: 'Joaquín Palomino',

            absenceCount: 0,
          },

          catalogItem: {
            id: 'h2000000-0000-4000-8000-000000000001',

            name: 'Algoritmos',
          },
        }),

        booked({
          bookingId: 'h0000000-0000-4000-8000-000000000002',

          status: 'NO_SHOW',

          startsAt: '2026-10-19T23:00:00Z',

          endsAt: '2026-10-20T00:00:00Z',
        }),

        booked({
          bookingId: 'h0000000-0000-4000-8000-000000000003',

          status: 'CANCELLED',

          startsAt: '2026-10-16T15:00:00Z',

          endsAt: '2026-10-16T16:00:00Z',
        }),

        booked({
          bookingId: 'h0000000-0000-4000-8000-000000000004',

          status: 'COMPLETED',

          startsAt: '2026-10-13T21:00:00Z',

          endsAt: '2026-10-13T23:00:00Z',
        }),
      ];

      const secondPage: TutorBooking[] = [
        booked({
          bookingId: 'h0000000-0000-4000-8000-000000000005',

          status: 'COMPLETED',

          startsAt: '2026-10-10T20:00:00Z',

          endsAt: '2026-10-10T21:00:00Z',

          student: {
            id: 'h1000000-0000-4000-8000-000000000005',

            name: 'Lucía Paredes',

            absenceCount: 0,
          },
        }),
      ];

      return {
        body: {
          view: 'HISTORY',

          timezone: 'America/Lima',

          page,

          size: 4,

          total: 8,

          items: page === 0 ? firstPage : secondPage,
        },
      };
    });

    const { user } = renderWithProviders(<AgendaPage />, {
      route: '/tutor/agenda',
    });

    await user.click(
      screen.getByRole('tab', {
        name: 'Historial',
      }),
    );

    expect(await screen.findByText(/Joaquín Palomino · Algoritmos/)).toBeInTheDocument();

    /*
     * There are two COMPLETED bookings on page 1,
     * so both correctly render the "Completada" badge.
     */
    expect(screen.getAllByText('Completada')).toHaveLength(2);

    expect(screen.getByText('No asistió')).toBeInTheDocument();

    expect(screen.getByText('Cancelada')).toBeInTheDocument();

    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {
        name: 'Siguiente',
      }),
    );

    expect(await screen.findByText(/Lucía Paredes/)).toBeInTheDocument();

    expect(screen.getByText('Página 2 de 2')).toBeInTheDocument();

    const requests = state.server.requests('GET', '/api/v1/tutor/agenda/history');

    expect(requests.length).toBeGreaterThanOrEqual(2);

    expect(requests.at(-1)?.query.get('page')).toBe('1');

    expect(requests.at(-1)?.query.get('size')).toBe('4');
  });

  it('moves the weekly range seven days at a time', async () => {
    signIn('STUDENT');

    const state = serveAgenda();

    const { user } = renderWithProviders(<AgendaPage />, {
      route: '/tutor/agenda',
    });

    expect(await screen.findByText(/23 oct – 29 oct/)).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', {
        name: 'Semana siguiente',
      }),
    );

    await waitFor(() => {
      const requests = state.server.requests('GET', '/api/v1/tutor/agenda');

      expect(requests.length).toBeGreaterThan(1);

      const last = requests.at(-1);

      expect(last?.query.get('from')).toBe('2026-10-30');

      expect(last?.query.get('to')).toBe('2026-11-05');
    });
  });

  it('shows the empty state with a way to declare availability', async () => {
    signIn('STUDENT');

    serveAgenda({
      from: '2026-10-23',

      to: '2026-10-29',

      timezone: 'America/Lima',

      lines: [],
    });

    renderWithProviders(<AgendaPage />, {
      route: '/tutor/agenda',
    });

    expect(
      await screen.findByRole('heading', {
        name: 'No tienes horas en estas fechas',
      }),
    ).toBeInTheDocument();

    expect(
      screen.getByRole('link', {
        name: 'Declarar disponibilidad',
      }),
    ).toHaveAttribute('href', '/tutor/disponibilidad');
  });

  it('shows its own error without exposing the backend message', async () => {
    signIn('STUDENT');

    const server = fakeServer()
      .on('GET', '/api/v1/university', {
        body: UPC,
      })
      .on('GET', '/api/v1/tutor/agenda', {
        status: 503,

        body: {
          message: 'Database connection refused',
        },
      });

    renderWithProviders(<AgendaPage />, {
      route: '/tutor/agenda',
    });

    expect(
      await screen.findByRole('heading', {
        name: 'No pudimos cargar esto',
      }),
    ).toBeInTheDocument();

    expect(screen.queryByText('Database connection refused')).not.toBeInTheDocument();

    expect(
      screen.getByRole('button', {
        name: 'Reintentar',
      }),
    ).toBeInTheDocument();

    expect(server.requests('GET', '/api/v1/tutor/agenda')).toHaveLength(1);
  });

  it('closes the late warning when the confirmed cancellation is refused too', async () => {
    signIn('STUDENT');
    const state = serveAgenda();
    state.server.on('DELETE', `/api/v1/bookings/${BOOKING_LATER}`, (request) =>
      request.query.get('confirmLate') !== 'true'
        ? {
            status: 409,
            body: {
              bookingId: BOOKING_LATER,
              late: true,
              refundWillBeIssued: true,
              incidentWillBeRecorded: true,
              confirmationRequired: true,
            },
          }
        : { status: 409, body: { status: 409, message: 'The session already started' } },
    );

    const { user } = renderWithProviders(<AgendaPage />, { route: '/tutor/agenda' });
    const row = await screen.findByRole('article', { name: 'Tutoría con Ana Torres a las 16:00' });
    await user.click(within(row).getByRole('button', { name: 'Cancelar' }));
    const dialog = await screen.findByRole('dialog', { name: 'Faltan menos de 12 horas' });
    await user.click(within(dialog).getByRole('button', { name: 'Cancelar igual' }));

    expect(await screen.findByText('Esta tutoría ya empezó o ya no puede cancelarse.')).toBeInTheDocument();
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.queryByText(/already started/)).not.toBeInTheDocument();
    expect(state.server.requests('DELETE', `/api/v1/bookings/${BOOKING_LATER}`)).toHaveLength(2);
  });
});
