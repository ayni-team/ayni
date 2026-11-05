import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import PurchaseDetailPage from './PurchaseDetailPage';
import PurchasesPage from './PurchasesPage';

const LIST = '/api/v1/payments/purchases';
const CONFIRMED = {
  id: 'p1',
  credits: 3,
  amount: 15,
  currency: 'PEN',
  status: 'CONFIRMED',
  createdAt: '2026-10-20T15:12:00Z',
};

function setup() {
  const server = fakeServer();
  serveFrame(server);
  signIn('STUDENT');
  return server;
}

function detail() {
  return renderWithProviders(<PurchaseDetailPage />, {
    route: '/creditos/compras/p1',
    path: '/creditos/compras/:id',
  });
}

describe('FE-32 pages', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('lists each purchase with its date in Lima, its status and its receipt', async () => {
    setup().on('GET', LIST, { body: [CONFIRMED] });

    renderWithProviders(<PurchasesPage />, { route: '/creditos/compras' });

    const table = await screen.findByRole('table', { name: 'Tus compras de créditos' });
    expect(within(table).getByText('3 créditos')).toBeInTheDocument();
    expect(within(table).getByText('Confirmada')).toBeInTheDocument();
    expect(within(table).getByText(/20 oct.*10:12/)).toBeInTheDocument();
    expect(within(table).getByRole('link', { name: 'Ver comprobante' })).toHaveAttribute(
      'href',
      '/creditos/compras/p1',
    );
  });

  it('says there are no purchases yet, and offers to retry in Spanish when the list fails', async () => {
    const server = setup().on('GET', LIST, { body: [] });

    const first = renderWithProviders(<PurchasesPage />, { route: '/creditos/compras' });
    expect(await screen.findByText('Todavía no tienes compras')).toBeInTheDocument();
    first.unmount();

    server.on('GET', LIST, { status: 500, body: { status: 500, message: 'boom' } });
    renderWithProviders(<PurchasesPage />, { route: '/creditos/compras' });
    expect(await screen.findByText('No pudimos cargar tus compras')).toBeInTheDocument();
    expect(screen.queryByText('boom')).not.toBeInTheDocument();
  });

  it('shows the receipt of a confirmed purchase with its reference and a link to the movement', async () => {
    setup().on('GET', `${LIST}/p1`, {
      body: {
        ...CONFIRMED,
        purchasedAt: CONFIRMED.createdAt,
        confirmedAt: CONFIRMED.createdAt,
        providerReference: 'simulated-p1',
      },
    });

    detail();

    expect(await screen.findByText('simulated-p1')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver el movimiento en mis créditos' })).toHaveAttribute(
      'href',
      '/creditos?motivo=PURCHASE',
    );
  });

  it('says a pending purchase is in process, without a link to a movement it did not cause', async () => {
    setup().on('GET', `${LIST}/p1`, { body: { ...CONFIRMED, status: 'PENDING' } });

    detail();

    expect(await screen.findByText('Tu pago está en proceso. No vuelvas a intentarlo.')).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Ver el movimiento en mis créditos' })).not.toBeInTheDocument();
  });

  it('does not offer to retry a purchase that does not exist', async () => {
    setup().on('GET', `${LIST}/p1`, { status: 404, body: { status: 404, message: 'nope' } });

    detail();

    expect(await screen.findByText('No encontramos esa compra')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
    expect(screen.queryByText('nope')).not.toBeInTheDocument();
  });
});
