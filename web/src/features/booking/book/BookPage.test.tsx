import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/shared/api';
import { fakeServer } from '@/test/fakeServer';
import { ANA, serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import BookPage from './BookPage';

const COURSE_ID = 'b0000000-0000-4000-8000-000000000102';
const BRUNO_ID = '22222222-2222-4222-8222-222222222222';
const START_INSTANT = '2026-10-27T20:00:00Z';
const END_INSTANT = '2026-10-27T21:00:00Z';

const DEFAULT_ROUTE = `/reservar?tutorId=${BRUNO_ID}&catalogItemId=${COURSE_ID}&start=${START_INSTANT}&hours=1`;

const WALLET: Schema<'WalletBalance'> = {
  userId: ANA.id,
  available: 12,
  earnedTotal: 0,
};

const HELD_HOURS: Schema<'HeldHours'> = {
  tutorId: BRUNO_ID,
  tutorName: 'Bruno Salas',
  startsAt: START_INSTANT,
  endsAt: END_INSTANT,
  hours: 1,
  // 5 minutes in the future from 2026-10-27T19:55:00Z
  serverNow: '2026-10-27T19:55:00Z',
  heldUntil: '2026-10-27T20:00:00Z',
  blockIds: ['c0000000-0000-4000-8000-000000000001'],
};

const CONFIRMED_BOOKING: Schema<'Booking'> = {
  id: 'd1f0c2a4-5b6e-4c7d-8e9f-0a1b2c3d4e5f',
  studentId: ANA.id,
  tutorId: BRUNO_ID,
  catalogItemId: COURSE_ID,
  startsAt: START_INSTANT,
  endsAt: END_INSTANT,
  hours: 1,
  creditsCharged: 1,
  needDescription: 'Dudas con Boyce-Codd',
  status: 'CONFIRMED',
};

const CATALOG: Schema<'CatalogPage'> = {
  items: [
    {
      id: COURSE_ID,
      name: 'Base de Datos II',
      courseCode: 'CC201',
      scope: 'UNIVERSITY',
    },
  ],
  totalElements: 1,
  totalPages: 1,
};

function setupServer() {
  const server = fakeServer();
  serveFrame(server, ANA);
  signIn('STUDENT');

  server
    .on('GET', '/api/v1/wallet', { body: WALLET })
    .on('GET', '/api/v1/catalog', { body: CATALOG })
    .on('POST', '/api/v1/bookings/holds', { body: HELD_HOURS, status: 201 })
    .on('DELETE', '/api/v1/bookings/holds', { status: 204 });

  return server;
}

describe('FE-11 BookPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders empty state when no tutor or start are provided', async () => {
    setupServer();
    const { user, location } = renderWithProviders(<BookPage />, {
      route: '/reservar',
    });

    expect(await screen.findByRole('heading', { level: 1, name: 'Reservar' })).toBeInTheDocument();
    expect(screen.getByText('No seleccionaste ninguna tutoría')).toBeInTheDocument();

    const goSearchBtn = screen.getByRole('button', { name: 'Ir a Buscar' });
    await user.click(goSearchBtn);
    expect(location()).toBe('/buscar');
  });

  it('renders initial hold countdown and summary card', async () => {
    setupServer();
    renderWithProviders(<BookPage />, {
      route: DEFAULT_ROUTE,
    });

    expect(await screen.findByRole('heading', { level: 1, name: 'Reservar' })).toBeInTheDocument();
    expect(await screen.findByText('Te guardamos esta hora')).toBeInTheDocument();

    // Summary Card elements
    expect(screen.getByText('Base de Datos II')).toBeInTheDocument();
    expect(screen.getByText(/CC201/)).toBeInTheDocument();
    expect(screen.getByText('Bruno Salas')).toBeInTheDocument();
    expect(screen.getByText(/Costo/)).toBeInTheDocument();
    expect(screen.getByText(/12 créditos/)).toBeInTheDocument();

    // Need form
    expect(screen.getByLabelText(/¿En qué necesitas ayuda\?/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Confirmar reserva' })).toBeInTheDocument();
  });

  it('validates required need description and confirms booking (201)', async () => {
    const server = setupServer();
    server.on('POST', '/api/v1/bookings', { body: CONFIRMED_BOOKING, status: 201 });

    const { user } = renderWithProviders(<BookPage />, {
      route: DEFAULT_ROUTE,
    });

    await screen.findByText('Te guardamos esta hora');

    const confirmButton = screen.getByRole('button', { name: 'Confirmar reserva' });

    // Submit empty shows validation error
    await user.click(confirmButton);
    expect(
      await screen.findByText('Por favor cuéntale al tutor en qué necesitas ayuda.'),
    ).toBeInTheDocument();

    // Fill in textarea
    const textarea = screen.getByLabelText(/¿En qué necesitas ayuda\?/);
    await user.type(textarea, 'Dudas con Boyce-Codd');

    // Confirm booking
    await user.click(confirmButton);

    // Shows success screen
    expect(await screen.findByRole('heading', { level: 1, name: 'Reserva confirmada' })).toBeInTheDocument();
    expect(screen.getByText('Listo, Bruno Salas te espera')).toBeInTheDocument();
    expect(screen.getByText(/Se descontó 1 crédito\. La sala se abre 15 minutos antes\./)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver mis tutorías' })).toHaveAttribute('href', '/mis-tutorias');
    expect(screen.getByRole('link', { name: 'Buscar otra' })).toHaveAttribute('href', '/buscar');

    // Booking request payload check
    const bookingCalls = server.requests('POST', '/api/v1/bookings');
    expect(bookingCalls).toHaveLength(1);
    expect(bookingCalls[0].body).toMatchObject({
      tutorId: BRUNO_ID,
      catalogItemId: COURSE_ID,
      start: START_INSTANT,
      hours: 1,
      needDescription: 'Dudas con Boyce-Codd',
    });
  });

  it('handles expired hold state and allows holding again', async () => {
    const server = setupServer();
    // Return a hold that has already expired
    server.on('POST', '/api/v1/bookings/holds', {
      body: {
        ...HELD_HOURS,
        serverNow: '2026-10-27T20:00:00Z',
        heldUntil: '2026-10-27T19:59:00Z',
      },
      status: 201,
    });

    const { user } = renderWithProviders(<BookPage />, {
      route: DEFAULT_ROUTE,
    });

    expect(await screen.findByText('Se acabó el tiempo')).toBeInTheDocument();
    expect(screen.getByText('La hora volvió a estar libre para otros estudiantes.')).toBeInTheDocument();

    const confirmBtn = screen.getByRole('button', { name: 'Confirmar reserva' });
    expect(confirmBtn).toBeDisabled();

    // Re-hold
    server.on('POST', '/api/v1/bookings/holds', { body: HELD_HOURS, status: 201 });
    const holdAgainBtn = screen.getByRole('button', { name: 'Apartar de nuevo' });
    await user.click(holdAgainBtn);

    expect(await screen.findByText('Te guardamos esta hora')).toBeInTheDocument();
  });

  it('handles 409 conflict: hour taken', async () => {
    const server = setupServer();
    server.on('POST', '/api/v1/bookings', {
      status: 409,
      body: {
        message: 'This hour was just taken by another student. Nothing was charged; choose another one',
      },
    });

    const { user, location } = renderWithProviders(<BookPage />, {
      route: DEFAULT_ROUTE,
    });

    await screen.findByText('Te guardamos esta hora');
    const textarea = screen.getByLabelText(/¿En qué necesitas ayuda\?/);
    await user.type(textarea, 'Normalización');
    await user.click(screen.getByRole('button', { name: 'Confirmar reserva' }));

    expect(await screen.findByText('Alguien tomó esa hora. Elige otra')).toBeInTheDocument();
    expect(screen.getByText('No se te cobró nada.')).toBeInTheDocument();

    const backBtn = screen.getByRole('button', { name: 'Volver a Buscar' });
    await user.click(backBtn);
    await waitFor(() => {
      expect(location()).toBe(`/buscar?catalogItemId=${COURSE_ID}`);
    });
  });

  it('handles 409 conflict: tutor not enabled', async () => {
    const server = setupServer();
    server.on('POST', '/api/v1/bookings', {
      status: 409,
      body: {
        message: 'This tutor is not enabled to teach this subject',
      },
    });

    const { user } = renderWithProviders(<BookPage />, {
      route: DEFAULT_ROUTE,
    });

    await screen.findByText('Te guardamos esta hora');
    const textarea = screen.getByLabelText(/¿En qué necesitas ayuda\?/);
    await user.type(textarea, 'Normalización');
    await user.click(screen.getByRole('button', { name: 'Confirmar reserva' }));

    expect(await screen.findByText('Bruno Salas ya no enseña este curso')).toBeInTheDocument();
    expect(screen.getByText('Elige otra hora u otro tutor. No se te cobró nada.')).toBeInTheDocument();
  });

  it('handles 409 conflict: insufficient credits', async () => {
    const server = setupServer();
    server.on('GET', '/api/v1/wallet', { body: { userId: ANA.id, available: 0 } });
    server.on('POST', '/api/v1/bookings', {
      status: 409,
      body: {
        message: 'Not enough credits: 1 more is needed to book these hours',
      },
    });

    const { user, location } = renderWithProviders(<BookPage />, {
      route: DEFAULT_ROUTE,
    });

    await screen.findByText('Te guardamos esta hora');
    const textarea = screen.getByLabelText(/¿En qué necesitas ayuda\?/);
    await user.type(textarea, 'Normalización');
    await user.click(screen.getByRole('button', { name: 'Confirmar reserva' }));

    expect(await screen.findByText('Te falta 1 crédito')).toBeInTheDocument();
    expect(
      screen.getByText(/La reserva cuesta 1 crédito y tu saldo es 0\. Compra créditos y vuelve a confirmar\./),
    ).toBeInTheDocument();
    const buyBtn = screen.getByRole('button', { name: 'Comprar créditos' });
    await user.click(buyBtn);
    expect(location()).toBe('/creditos/comprar');
  });

  it('handles unexpected error and allows retry', async () => {
    const server = setupServer();
    server.on('POST', '/api/v1/bookings', {
      status: 500,
      body: { message: 'Internal Server Error' },
    });

    const { user } = renderWithProviders(<BookPage />, {
      route: DEFAULT_ROUTE,
    });

    await screen.findByText('Te guardamos esta hora');
    const textarea = screen.getByLabelText(/¿En qué necesitas ayuda\?/);
    await user.type(textarea, 'Duda de arquitectura');
    await user.click(screen.getByRole('button', { name: 'Confirmar reserva' }));

    expect(await screen.findByText('No pudimos confirmar la reserva')).toBeInTheDocument();
    expect(
      screen.getByText(/No se te cobró nada\. Lo que escribiste sigue aquí: vuelve a intentarlo\./),
    ).toBeInTheDocument();

    // Text in textarea is preserved
    expect(screen.getByLabelText(/¿En qué necesitas ayuda\?/)).toHaveValue('Duda de arquitectura');

    // Retry succeeds
    server.on('POST', '/api/v1/bookings', { body: CONFIRMED_BOOKING, status: 201 });
    await user.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('heading', { level: 1, name: 'Reserva confirmada' })).toBeInTheDocument();
  });

  it('releases hold when clicking "Elegir otra hora"', async () => {
    const server = setupServer();
    const { user, location } = renderWithProviders(<BookPage />, {
      route: DEFAULT_ROUTE,
    });

    await screen.findByText('Te guardamos esta hora');

    const chooseOtherBtn = screen.getByRole('button', { name: 'Elegir otra hora' });
    await user.click(chooseOtherBtn);

    await waitFor(() => {
      expect(server.requests('DELETE', '/api/v1/bookings/holds')).toHaveLength(1);
    });
    expect(location()).toBe(`/buscar?catalogItemId=${COURSE_ID}`);
  });
});
