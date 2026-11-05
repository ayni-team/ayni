import { screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import BuyCreditsPage from './BuyCreditsPage';

const URL = '/api/v1/payments/purchases';
const ANSWER = {
  id: 'p1',
  credits: 3,
  amount: 15,
  currency: 'PEN',
  providerReference: 'SIM-1',
  createdAt: '2026-10-23T21:42:00Z',
};

function setup() {
  const server = fakeServer();
  serveFrame(server);
  signIn('STUDENT');
  return server;
}

async function buy(user: ReturnType<typeof renderWithProviders>['user'], amount = '3') {
  const field = screen.getByLabelText('¿Cuántos créditos quieres?');
  await user.clear(field);
  await user.type(field, amount);
  await user.click(screen.getByRole('button', { name: 'Comprar' }));
}

function open() {
  return renderWithProviders(<BuyCreditsPage />, { route: '/creditos/comprar' });
}

describe('FE-31 BuyCreditsPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows no price before the answer, and sends the amount with its key', async () => {
    const server = setup().on('POST', URL, { status: 201, body: { ...ANSWER, status: 'CONFIRMED' } });
    const { user } = open();

    expect(screen.queryByText(/S\//)).not.toBeInTheDocument();
    await buy(user);

    expect(await screen.findByText('Listo: se acreditaron 3 créditos')).toBeInTheDocument();
    expect(screen.getByText(/15\.00/)).toBeInTheDocument();
    const [request] = server.requests('POST', URL);
    expect(request.body).toEqual({ credits: 3 });
    expect(request.headers.get('Idempotency-Key')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Ver la compra' })).toHaveAttribute('href', '/creditos/compras/p1');
    expect(screen.getByRole('link', { name: 'Ver mis créditos' })).toHaveAttribute('href', '/creditos');
  });

  it('sends one request on a double click', async () => {
    const server = setup().on('POST', URL, { status: 201, body: { ...ANSWER, status: 'CONFIRMED' } });
    const { user } = open();

    await user.dblClick(screen.getByRole('button', { name: 'Comprar' }));

    await screen.findByRole('link', { name: 'Ver mis créditos' });
    expect(server.requests('POST', URL)).toHaveLength(1);
  });

  it('does not send an amount that is not valid', async () => {
    const server = setup();
    const { user } = open();

    await buy(user, '2.5');

    expect(await screen.findByText('Escribe un número entero de créditos')).toBeInTheDocument();
    expect(server.requests('POST', URL)).toHaveLength(0);
  });

  it('says a pending payment is in process, and offers no new attempt', async () => {
    setup().on('POST', URL, { status: 201, body: { ...ANSWER, status: 'PENDING', guidance: 'English' } });
    const { user } = open();

    await buy(user);

    expect(await screen.findByText('Tu pago está en proceso. No vuelvas a intentarlo')).toBeInTheDocument();
    expect(screen.queryByText('English')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Intentar de nuevo' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Ver mis compras' })).toHaveAttribute('href', '/creditos/compras');
  });

  it('takes a new key after a rejection', async () => {
    const server = setup().on('POST', URL, { status: 201, body: { ...ANSWER, status: 'FAILED' } });
    const { user } = open();

    await buy(user);
    await user.click(await screen.findByRole('button', { name: 'Intentar de nuevo' }));
    await buy(user);

    await waitFor(() => expect(server.requests('POST', URL)).toHaveLength(2));
    const [first, second] = server.requests('POST', URL);
    expect(first.headers.get('Idempotency-Key')).not.toBe(second.headers.get('Idempotency-Key'));
  });

  it('keeps the key when the platform failed, so a retry cannot charge twice', async () => {
    const server = setup().on('POST', URL, { status: 503, body: { status: 503, message: 'down' } });
    const { user } = open();

    await buy(user);
    await waitFor(() => expect(screen.getByRole('button', { name: 'Comprar' })).toBeEnabled());
    await user.click(screen.getByRole('button', { name: 'Comprar' }));

    await waitFor(() => expect(server.requests('POST', URL)).toHaveLength(2));
    const [first, second] = server.requests('POST', URL);
    expect(first.headers.get('Idempotency-Key')).toBe(second.headers.get('Idempotency-Key'));
    expect(screen.getByLabelText('¿Cuántos créditos quieres?')).toHaveValue('3');
    expect(screen.queryByText('down')).not.toBeInTheDocument();
  });

  it('says purchases are not available, and the rest of Ayni is', async () => {
    setup().on('POST', URL, { status: 201, body: { ...ANSWER, status: 'PENDING', providerUnavailable: true } });
    const { user } = open();

    await buy(user);

    expect(await screen.findByText('Las compras están temporalmente no disponibles')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Comprar' })).toBeDisabled();
    expect(screen.getByLabelText('¿Cuántos créditos quieres?')).toBeDisabled();
  });

  it('says the monthly limit in its own words', async () => {
    setup().on('POST', URL, { status: 409, body: { status: 409, message: 'Monthly purchase limit is 5 credits' } });
    const { user } = open();

    await buy(user);

    expect(await screen.findByText(/Llegaste al límite de créditos/)).toBeInTheDocument();
    expect(screen.queryByText(/Monthly/)).not.toBeInTheDocument();
    expect(screen.getByLabelText('¿Cuántos créditos quieres?')).toHaveValue('3');
  });
});
