import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/shared/api';
import { fakeServer } from '@/test/fakeServer';
import { serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import WalletPage from './WalletPage';

const WALLET = '/api/v1/wallet';
const MOVEMENTS = '/api/v1/wallet/movements';

const BALANCE: Schema<'WalletBalance'> = {
  available: 8,
  earnedTotal: 1,
  byType: [
    {
      type: 'ALLOCATED',
      available: 7,
      expires: true,
      countsTowardsRecognition: false,
      groups: [{ amount: 7, expiresAt: '2026-12-15T05:00:00Z' }],
    },
    { type: 'EARNED', available: 1, expires: false, countsTowardsRecognition: true, groups: [{ amount: 1 }] },
  ],
  guidance: 'English text the screen never shows',
};

const PAGE: Schema<'MovementsPage'> = {
  page: 0,
  totalPages: 2,
  items: [
    {
      sequenceNumber: 2,
      occurredAt: '2026-10-23T20:40:00Z',
      direction: 'DEBIT',
      amount: 1,
      reason: 'BOOKING_CHARGE',
      creditType: 'ALLOCATED',
      referenceType: 'BOOKING',
      referenceId: 'b1',
    },
    {
      sequenceNumber: 1,
      occurredAt: '2026-10-20T15:12:00Z',
      direction: 'CREDIT',
      amount: 3,
      reason: 'PURCHASE',
      creditType: 'PURCHASED',
      referenceType: 'PURCHASE',
      referenceId: 'p1',
    },
  ],
};

function setup(balance: Schema<'WalletBalance'> = BALANCE, movements: Schema<'MovementsPage'> = PAGE) {
  const server = fakeServer();
  serveFrame(server);
  signIn('STUDENT');
  server.on('GET', WALLET, { body: balance }).on('GET', MOVEMENTS, { body: movements });
  return server;
}

describe('FE-30 WalletPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the balance, each origin with its expiry, and the badge of what counts', async () => {
    setup();

    renderWithProviders(<WalletPage />, { route: '/creditos' });

    expect(await screen.findByText(/1 ganado en total/)).toBeInTheDocument();
    const allocated = screen.getByRole('heading', { name: 'Asignados por la UPC' }).closest('article');
    expect(within(allocated as HTMLElement).getByText('7 vigentes hasta el 14/12')).toBeInTheDocument();
    const earned = screen.getByRole('heading', { name: 'Ganados enseñando' }).closest('article');
    expect(within(earned as HTMLElement).getByText('No vencen')).toBeInTheDocument();
    expect(within(earned as HTMLElement).getByText('Cuenta para el reconocimiento')).toBeInTheDocument();
    expect(screen.getByText('7 créditos asignados por la UPC están vigentes hasta el 14/12.')).toBeInTheDocument();
    expect(screen.queryByText(/English text/)).not.toBeInTheDocument();
  });

  it('says a zero balance in its own words, and still draws the origins', async () => {
    setup({ available: 0, earnedTotal: 0, byType: [], guidance: 'You have no credits' }, { items: [], totalPages: 0 });

    renderWithProviders(<WalletPage />, { route: '/creditos' });

    expect(await screen.findByText('Aún no tienes créditos')).toBeInTheDocument();
    expect(screen.queryByText('You have no credits')).not.toBeInTheDocument();
    expect(screen.getAllByText('Sin créditos de este origen')).toHaveLength(4);
    expect(screen.queryByText('Por vencer')).not.toBeInTheDocument();
  });

  it('asks for the movements with an instant, and filters by reason from the first page', async () => {
    const server = setup();

    const { user } = renderWithProviders(<WalletPage />, { route: '/creditos' });
    await screen.findAllByText('Reserva');

    await waitFor(() =>
      expect(server.requests('GET', MOVEMENTS).at(-1)?.query.get('from')).toMatch(/T05:00:00\.000Z$/),
    );
    expect(server.requests('GET', MOVEMENTS).at(-1)?.query.get('reason')).toBeNull();

    await user.click(screen.getByRole('button', { name: 'Siguiente' }));
    await waitFor(() => expect(server.requests('GET', MOVEMENTS).at(-1)?.query.get('page')).toBe('1'));

    await user.selectOptions(screen.getByLabelText('Motivo'), 'PURCHASE');
    await waitFor(() => {
      const last = server.requests('GET', MOVEMENTS).at(-1);
      expect(last?.query.get('reason')).toBe('PURCHASE');
      expect(last?.query.get('page')).toBe('0');
    });

    await user.selectOptions(screen.getByLabelText('Periodo'), 'ALL');
    await waitFor(() => expect(server.requests('GET', MOVEMENTS).at(-1)?.query.get('from')).toBeNull());
  });

  it('opens on the whole history of a reason when a receipt links to it', async () => {
    const server = setup();

    renderWithProviders(<WalletPage />, { route: '/creditos?motivo=PURCHASE' });

    await screen.findAllByText('Reserva');
    const last = server.requests('GET', MOVEMENTS).at(-1);
    expect(last?.query.get('reason')).toBe('PURCHASE');
    expect(last?.query.get('from')).toBeNull();
    expect(screen.getByLabelText('Motivo')).toHaveValue('PURCHASE');
  });

  it('shows each movement with its amount, its time in Lima and its link', async () => {
    setup();

    renderWithProviders(<WalletPage />, { route: '/creditos' });

    const table = await screen.findByRole('table', { name: 'Movimientos de tus créditos' });
    expect(within(table).getByText('23 oct · 15:40')).toBeInTheDocument();
    expect(within(table).getByText('−1 crédito')).toBeInTheDocument();
    expect(within(table).getByText('+3 créditos')).toBeInTheDocument();
    expect(within(table).getByRole('link', { name: 'Ver tutoría' })).toHaveAttribute('href', '/mis-tutorias');
    expect(within(table).getByRole('link', { name: 'Ver compra' })).toHaveAttribute('href', '/creditos/compras/p1');
  });

  it('tells an empty filter from an empty history', async () => {
    setup(BALANCE, { items: [], totalPages: 0 });

    const { user } = renderWithProviders(<WalletPage />, { route: '/creditos' });

    expect(await screen.findByText('No hay movimientos con esos filtros. Prueba otro periodo.')).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Periodo'), 'ALL');
    expect(await screen.findByText('Cuando reserves, enseñes o compres, aparecerán aquí.')).toBeInTheDocument();
  });

  it('offers to retry when the balance fails, in Spanish', async () => {
    const server = setup();
    server.on('GET', WALLET, { status: 500, body: { status: 500, message: 'boom' } });

    renderWithProviders(<WalletPage />, { route: '/creditos' });

    expect(await screen.findByText('No pudimos cargar tus créditos')).toBeInTheDocument();
    expect(screen.queryByText('boom')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reintentar' })).toBeInTheDocument();
  });
});
