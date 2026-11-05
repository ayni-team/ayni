import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { CARLOS, serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import IndicatorsPage from './IndicatorsPage';

const INDICATORS = '/api/v1/coordinator/indicators';
const COURSES = '/api/v1/coordinator/academic-catalog/courses';

const READY = {
  from: '2026-10-01',
  to: '2026-10-30',
  totals: { sessionsCompleted: 128, hoursExchanged: 141, activeStudents: 64, newStudents: 12, recognitionsGranted: 3 },
  days: [
    { day: '2026-10-08', figures: { hoursExchanged: 2, sessionsCompleted: 2 } },
    { day: '2026-10-09', figures: { hoursExchanged: 9, sessionsCompleted: 8 } },
  ],
  uncoveredDemand: [{ catalogItemId: 'c1', searches: 23 }],
};

function setup() {
  const server = fakeServer();
  serveFrame(server, CARLOS);
  signIn('COORDINATOR');
  server.on('GET', COURSES, { body: [{ id: 'c1', name: 'Física II', code: 'MA462' }] });
  return server;
}

describe('FE-53 IndicatorsPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the totals, the peak of the days and the courses by name', async () => {
    setup().on('GET', INDICATORS, { body: READY });

    renderWithProviders(<IndicatorsPage />, { route: '/coordinador/indicadores' });

    expect(await screen.findByText('128')).toBeInTheDocument();
    expect(screen.getByText('Días de la universidad · máximo 9 el 9 oct')).toBeInTheDocument();
    expect(await screen.findByText('Física II')).toBeInTheDocument();
    expect(screen.getByText('MA462')).toBeInTheDocument();
    expect(screen.getByText('23 búsquedas')).toBeInTheDocument();
  });

  it('shows the days as a table too', async () => {
    setup().on('GET', INDICATORS, { body: READY });

    const { user } = renderWithProviders(<IndicatorsPage />, { route: '/coordinador/indicadores' });
    await screen.findByText('128');

    await user.click(screen.getByRole('tab', { name: 'Tabla' }));

    expect(screen.getByRole('table', { name: 'Horas y sesiones por día' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'Vie 9 oct' })).toBeInTheDocument();
  });

  it('asks for a new period and never asks for one too long', async () => {
    const server = setup().on('GET', INDICATORS, { body: READY });

    renderWithProviders(<IndicatorsPage />, { route: '/coordinador/indicadores' });
    await screen.findByText('128');
    const before = server.requests('GET', INDICATORS).length;

    fireEvent.change(screen.getByLabelText('Desde'), { target: { value: '2020-01-01' } });

    expect(await screen.findByText('Elige un periodo de hasta 366 días')).toBeInTheDocument();
    expect(screen.getByText('Hasta 366 días')).toBeInTheDocument();
    expect(server.requests('GET', INDICATORS)).toHaveLength(before);
    for (const button of screen.getAllByRole('button', { name: 'Descargar CSV', hidden: true })) {
      expect(button).toBeDisabled();
    }
  });

  it('says there was no activity and does not offer the CSV', async () => {
    setup().on('GET', INDICATORS, {
      body: { totals: { sessionsCompleted: 0, hoursExchanged: 0 }, days: [{ day: '2026-10-01' }], uncoveredDemand: [] },
    });

    renderWithProviders(<IndicatorsPage />, { route: '/coordinador/indicadores' });

    expect(await screen.findByText('Sin actividad en este periodo')).toBeInTheDocument();
    await waitFor(() => {
      for (const button of screen.getAllByRole('button', { name: 'Descargar CSV', hidden: true })) {
        expect(button).toBeDisabled();
      }
    });
  });

  it('offers to retry in Spanish when it fails', async () => {
    setup().on('GET', INDICATORS, { status: 500, body: { status: 500, message: 'boom' } });

    renderWithProviders(<IndicatorsPage />, { route: '/coordinador/indicadores' });

    expect(await screen.findByText('No pudimos cargar los indicadores')).toBeInTheDocument();
    expect(screen.queryByText('boom')).not.toBeInTheDocument();
  });
});
