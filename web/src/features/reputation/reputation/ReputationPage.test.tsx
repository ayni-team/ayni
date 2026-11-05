import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import ReputationPage from './ReputationPage';

const REPUTATION = '/api/v1/tutor/reputation';

function setup(reply: Parameters<ReturnType<typeof fakeServer>['on']>[2]) {
  const server = fakeServer();
  serveFrame(server);
  signIn('STUDENT');
  server.on('GET', REPUTATION, reply);
}

function card(name: string) {
  return within(screen.getByRole('heading', { name }).closest('article') as HTMLElement);
}

describe('FE-43 ReputationPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the provisional average of a tutor below three ratings, and how many are missing', async () => {
    setup({
      body: {
        minimumAverage: 3,
        skills: [
          {
            catalogItemId: 'a',
            name: 'Algoritmos',
            sessionsTaught: 2,
            ratingsCount: 2,
            averageStars: null,
            provisionalAverage: 4.5,
            ratingsMissingForAverage: 1,
            topTags: [
              { tag: 'CLEAR', count: 2 },
              { tag: 'Una etiqueta vieja', count: 1 },
            ],
          },
        ],
      },
    });

    renderWithProviders(<ReputationPage />, { route: '/tutor/reputacion' });

    await screen.findByRole('heading', { name: 'Algoritmos' });
    const algorithms = card('Algoritmos');
    expect(algorithms.getByText('4.5')).toBeInTheDocument();
    expect(algorithms.getByText('Provisional')).toBeInTheDocument();
    expect(algorithms.getByText('Te falta 1 calificación para el promedio')).toBeInTheDocument();
    expect(algorithms.getByText('Explica con claridad · 2')).toBeInTheDocument();
    expect(algorithms.getByText('Una etiqueta vieja · 1')).toBeInTheDocument();
  });

  it('warns about a skill at risk and shows nothing as an average when nobody has rated', async () => {
    setup({
      body: {
        minimumAverage: 3,
        skills: [
          {
            catalogItemId: 'a',
            name: 'Algoritmos',
            sessionsTaught: 5,
            ratingsCount: 5,
            averageStars: 2.4,
            atRisk: true,
          },
          { catalogItemId: 'b', name: 'Base de Datos', sessionsTaught: 0, ratingsCount: 0 },
        ],
      },
    });

    renderWithProviders(<ReputationPage />, { route: '/tutor/reputacion' });

    await screen.findByRole('heading', { name: 'Algoritmos' });
    expect(card('Algoritmos').getByText('Tu promedio está por debajo de 3.0')).toBeInTheDocument();
    expect(card('Base de Datos').getByText('Aún no tienes calificaciones en esta habilidad.')).toBeInTheDocument();
  });

  it('says there is nothing yet, and leads to the skills', async () => {
    setup({ body: { skills: [] } });

    renderWithProviders(<ReputationPage />, { route: '/tutor/reputacion' });

    expect(await screen.findByText('Todavía no tienes reputación')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver mis habilidades' })).toHaveAttribute('href', '/tutor/habilidades');
  });

  it('offers to retry in Spanish when it fails', async () => {
    setup({ status: 500, body: { status: 500, message: 'boom' } });

    renderWithProviders(<ReputationPage />, { route: '/tutor/reputacion' });

    expect(await screen.findByText('No pudimos cargar tu reputación')).toBeInTheDocument();
    expect(screen.queryByText('boom')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
