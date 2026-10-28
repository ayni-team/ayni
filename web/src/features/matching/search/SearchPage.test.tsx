import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/shared/api';
import { fakeServer } from '@/test/fakeServer';
import { ANA, serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import SearchPage from './SearchPage';

const COURSE_ID = 'b0000000-0000-4000-8000-000000000102';
const BRUNO_ID = '22222222-2222-4222-8222-222222222222';

const CATALOG: Schema<'CatalogPage'> = {
  items: [
    {
      id: COURSE_ID,
      name: 'Algoritmos',
      courseCode: 'CC215',
      scope: 'UNIVERSITY',
    },
    {
      id: 'b0000000-0000-4000-8000-000000000103',
      name: 'Matemática Discreta',
      courseCode: 'MA265',
      scope: 'UNIVERSITY',
    },
  ],
  totalElements: 2,
  totalPages: 1,
};

const BRUNO_OFFER: Schema<'Offer'> = {
  blockId: 'c0000000-0000-4000-8000-000000000001',
  tutorId: BRUNO_ID,
  tutorName: 'Bruno Salas',
  catalogItemId: COURSE_ID,
  // 20:00 UTC is 15:00 in Lima time (UTC-5)
  startsAt: '2026-10-27T20:00:00Z',
  endsAt: '2026-10-27T21:00:00Z',
  averageStars: 4.8,
  ratingsCount: 11,
  sessionsTaught: 14,
  newTutor: false,
};

function setupServer() {
  const server = fakeServer();
  serveFrame(server, ANA);
  signIn('STUDENT');

  server
    .on('GET', '/api/v1/catalog', { body: CATALOG })
    .on('GET', '/api/v1/bookings/mine', { body: { items: [], total: 0 } });

  return server;
}

describe('FE-10 SearchPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders initial empty state prompting to choose a course', async () => {
    setupServer();
    renderWithProviders(<SearchPage />, { route: '/buscar' });

    expect(await screen.findByRole('heading', { level: 1, name: 'Buscar tutoría' })).toBeInTheDocument();
    expect(screen.getByText('Elige un curso para ver sus horas libres')).toBeInTheDocument();
    expect(screen.getByText('Te mostramos los próximos 7 días; puedes cambiar el rango.')).toBeInTheDocument();
  });

  it('shows offers in Lima time and allows choosing an offer to go to /reservar', async () => {
    const server = setupServer();
    server.on('GET', '/api/v1/search/offers', {
      body: {
        offers: [BRUNO_OFFER],
        exactMatch: true,
        timezone: 'America/Lima',
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
    });

    const { user, location } = renderWithProviders(<SearchPage />, {
      route: `/buscar?catalogItemId=${COURSE_ID}`,
    });

    // Should load the course and display Bruno's hours in Lima time
    expect(await screen.findByText('Horas libres · Algoritmos')).toBeInTheDocument();
    // In desktop grid and mobile list, Bruno Salas is listed
    expect(await screen.findAllByText('Bruno Salas')).not.toHaveLength(0);
    expect(screen.getAllByText(/4\.8 · 11 calificaciones/).length).toBeGreaterThan(0);

    // In detail drawer, there is an "Elegir" button
    const elegirButtons = screen.getAllByRole('button', { name: 'Elegir' });
    expect(elegirButtons.length).toBeGreaterThan(0);

    // Clicking elegir navigates to /reservar
    await user.click(elegirButtons[0]);
    await waitFor(() => {
      expect(location()).toBe('/reservar');
    });
  });

  it('shows warning notice when exactMatch is false (closest hours outside window)', async () => {
    const server = setupServer();
    server.on('GET', '/api/v1/search/offers', {
      body: {
        offers: [BRUNO_OFFER],
        exactMatch: false,
        timezone: 'America/Lima',
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
    });

    renderWithProviders(<SearchPage />, {
      route: `/buscar?catalogItemId=${COURSE_ID}`,
    });

    expect(
      await screen.findByText('No hay horas en esa franja. Estas son las más cercanas'),
    ).toBeInTheDocument();
    expect(screen.getByText('Bruno Salas')).toBeInTheDocument();
  });

  it('shows empty state when no offers exist for that course in that week', async () => {
    const server = setupServer();
    server.on('GET', '/api/v1/search/offers', {
      body: {
        offers: [],
        exactMatch: true,
        timezone: 'America/Lima',
        page: 0,
        size: 20,
        totalElements: 0,
        totalPages: 0,
      },
    });

    renderWithProviders(<SearchPage />, {
      route: `/buscar?catalogItemId=${COURSE_ID}`,
    });

    expect(await screen.findByText('No hay horas libres para este curso')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Ver la semana siguiente' })).toBeInTheDocument();
  });

  it('shows error state when search request fails and allows retry', async () => {
    const server = setupServer();
    let attempts = 0;
    server.on('GET', '/api/v1/search/offers', () => {
      attempts++;
      if (attempts === 1) {
        return { status: 500, body: { status: 500, message: 'Internal Server Error' } };
      }
      return {
        body: {
          offers: [BRUNO_OFFER],
          exactMatch: true,
          timezone: 'America/Lima',
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        },
      };
    });

    const { user } = renderWithProviders(<SearchPage />, {
      route: `/buscar?catalogItemId=${COURSE_ID}`,
    });

    expect(await screen.findByText('No pudimos cargar esto')).toBeInTheDocument();
    const retryButton = screen.getByRole('button', { name: 'Reintentar' });
    expect(retryButton).toBeInTheDocument();

    await user.click(retryButton);
    expect(await screen.findByText('Horas libres · Algoritmos')).toBeInTheDocument();
  });

  it("asks for the week between the university's midnights, not UTC's", async () => {
    const server = setupServer();
    server.on('GET', '/api/v1/search/offers', {
      body: { offers: [], exactMatch: true, timezone: 'America/Lima', page: 0, size: 100, totalElements: 0, totalPages: 0 },
    });

    renderWithProviders(<SearchPage />, { route: `/buscar?catalogItemId=${COURSE_ID}` });

    await waitFor(() => expect(server.requests('GET', '/api/v1/search/offers').length).toBeGreaterThan(0));
    const { query } = server.requests('GET', '/api/v1/search/offers').at(-1)!;
    // Lima is UTC-5 all year: its midnight is 05:00 UTC.
    expect(query.get('from')).toMatch(/T05:00:00\.000Z$/);
    expect(query.get('to')).toMatch(/T05:00:00\.000Z$/);
  });

  it('says the courses could not be loaded and lets the person retry', async () => {
    const server = fakeServer();
    serveFrame(server, ANA);
    signIn('STUDENT');
    server
      .on('GET', '/api/v1/catalog', { status: 503, body: { message: 'Service unavailable' } })
      .on('GET', '/api/v1/bookings/mine', { body: { items: [], total: 0 } });

    const { user } = renderWithProviders(<SearchPage />, { route: '/buscar' });

    expect(await screen.findByRole('heading', { name: 'No pudimos cargar los cursos' })).toBeInTheDocument();
    expect(screen.queryByText(/Service unavailable/)).not.toBeInTheDocument();

    server.on('GET', '/api/v1/catalog', { body: CATALOG });
    await user.click(screen.getByRole('button', { name: /Reintentar/ }));
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'No pudimos cargar los cursos' })).not.toBeInTheDocument());
  });
});
