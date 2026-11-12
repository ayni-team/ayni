import { screen, waitFor, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { serveFrame, signIn } from '@/test/fixtures';
import { renderWithProviders } from '@/test/render';
import BenefitsPage from './BenefitsPage';

const BENEFITS = '/api/v1/wallet/benefits';
const REDEEM = `${BENEFITS}/b1/redemptions`;
const DONATIONS = '/api/v1/wallet/donations';
const CHALLENGE = { confirmationId: 'c1', confirmationRequired: true, warning: 'This operation is final' };

function setup(benefits: unknown = [{ id: 'b1', name: 'Impresiones', description: 'Cien hojas', creditsCost: 2 }]) {
  const server = fakeServer();
  serveFrame(server);
  signIn('STUDENT');
  server
    .on('GET', '/api/v1/wallet', {
      body: {
        available: 8,
        byType: [
          { type: 'EARNED', available: 3 },
          { type: 'PURCHASED', available: 5 },
        ],
      },
    })
    .on('GET', BENEFITS, { body: benefits })
    .on('GET', '/api/v1/wallet/donation-pool', { body: { credits: 12, contributions: 5 } })
    .on('GET', DONATIONS, { body: [] });
  return server;
}

function open() {
  return renderWithProviders(<BenefitsPage />, { route: '/creditos/beneficios' });
}

describe('FE-33 BenefitsPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the earned credits alone, the benefits and the fund', async () => {
    setup();
    open();

    expect(await screen.findByText('Impresiones')).toBeInTheDocument();
    expect(await screen.findByText('3')).toBeInTheDocument();
    expect(await screen.findByText(/12 créditos de 5 aportes/)).toBeInTheDocument();
  });

  it('redeems in two steps with the same key, and spends nothing until confirmed', async () => {
    const server = setup();
    server.on('POST', REDEEM, (request) =>
      (request.body as { confirmed: boolean }).confirmed
        ? { status: 201, body: { id: 'r1', credits: 2 } }
        : { body: CHALLENGE },
    );
    const { user } = open();

    await user.click(await screen.findByRole('button', { name: 'Canjear Impresiones' }));

    const dialog = within(await screen.findByRole('dialog'));
    expect(dialog.getByText(/Vas a canjear 2 créditos ganados por "Impresiones"/)).toBeInTheDocument();
    expect(screen.queryByText('This operation is final')).not.toBeInTheDocument();
    expect(server.requests('POST', REDEEM)).toHaveLength(1);

    await user.dblClick(dialog.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByText('Canjeaste "Impresiones"')).toBeInTheDocument();
    const [first, second] = server.requests('POST', REDEEM);
    expect(server.requests('POST', REDEEM)).toHaveLength(2);
    expect(first.body).toEqual({ confirmed: false });
    expect(second.body).toEqual({ confirmed: true, confirmationId: 'c1' });
    expect(first.headers.get('Idempotency-Key')).toBeTruthy();
    expect(second.headers.get('Idempotency-Key')).toBe(first.headers.get('Idempotency-Key'));
  });

  it('takes a new key for a new attempt after cancelling', async () => {
    const server = setup();
    server.on('POST', REDEEM, { body: CHALLENGE });
    const { user } = open();

    await user.click(await screen.findByRole('button', { name: 'Canjear Impresiones' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Cancelar' }));
    await user.click(screen.getByRole('button', { name: 'Canjear Impresiones' }));

    await waitFor(() => expect(server.requests('POST', REDEEM)).toHaveLength(2));
    const [first, second] = server.requests('POST', REDEEM);
    expect(first.headers.get('Idempotency-Key')).not.toBe(second.headers.get('Idempotency-Key'));
  });

  it('says how many earned credits are missing when refused', async () => {
    const server = setup([{ id: 'b1', name: 'Impresiones', creditsCost: 5 }]);
    server.on('POST', REDEEM, (request) =>
      (request.body as { confirmed: boolean }).confirmed
        ? {
            status: 409,
            body: { status: 409, message: 'Not enough', code: 'INSUFFICIENT_EARNED_CREDITS', missingCredits: 2 },
          }
        : { body: CHALLENGE },
    );
    const { user } = open();
    await screen.findByText('3');

    await user.click(await screen.findByRole('button', { name: 'Canjear Impresiones' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmar' }));

    expect(
      await screen.findByText('Te faltan 2 créditos ganados. Los comprados o asignados no cuentan.'),
    ).toBeInTheDocument();
    expect(screen.queryByText('Not enough')).not.toBeInTheDocument();
  });

  it('tells an expired confirmation apart from missing credits, whatever the balance says', async () => {
    // The benefit costs more than the balance the screen read: only the answer decides.
    const server = setup([{ id: 'b1', name: 'Impresiones', creditsCost: 5 }]);
    server.on('POST', REDEEM, (request) =>
      (request.body as { confirmed: boolean }).confirmed
        ? { status: 409, body: { status: 409, message: 'Expired', code: 'CONFIRMATION_EXPIRED' } }
        : { body: CHALLENGE },
    );
    const { user } = open();
    await screen.findByText('3');

    await user.click(await screen.findByRole('button', { name: 'Canjear Impresiones' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByText('La confirmación venció. Vuelve a intentarlo.')).toBeInTheDocument();
    expect(screen.queryByText(/Te faltan/)).not.toBeInTheDocument();
  });

  it('keeps the same key when the platform fails, so a retry spends once', async () => {
    const server = setup();
    let confirmations = 0;
    server.on('POST', REDEEM, (request) => {
      if (!(request.body as { confirmed: boolean }).confirmed) return { body: CHALLENGE };
      confirmations += 1;
      return confirmations === 1 ? { status: 503, body: { status: 503 } } : { status: 201, body: { id: 'r1' } };
    });
    const { user } = open();

    await user.click(await screen.findByRole('button', { name: 'Canjear Impresiones' }));
    const dialog = within(await screen.findByRole('dialog'));
    await user.click(dialog.getByRole('button', { name: 'Confirmar' }));
    expect(await dialog.findByRole('alert')).toBeInTheDocument();
    await user.click(dialog.getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByText('Canjeaste "Impresiones"')).toBeInTheDocument();
    const keys = server.requests('POST', REDEEM).map((request) => request.headers.get('Idempotency-Key'));
    expect(new Set(keys).size).toBe(1);
  });

  it('donates in two steps, with the amount in both', async () => {
    const server = setup();
    server.on('POST', DONATIONS, (request) =>
      (request.body as { confirmed: boolean }).confirmed
        ? { status: 201, body: { id: 'd1', credits: 2 } }
        : { body: CHALLENGE },
    );
    const { user } = open();

    const field = await screen.findByLabelText('¿Cuántos créditos quieres donar?');
    await user.clear(field);
    await user.type(field, '2');
    await user.click(screen.getByRole('button', { name: 'Donar' }));
    await user.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Confirmar' }));

    expect(await screen.findByText('Gracias: donaste 2 créditos a los estudiantes nuevos')).toBeInTheDocument();
    expect(server.requests('POST', DONATIONS)[1].body).toEqual({ credits: 2, confirmed: true, confirmationId: 'c1' });
  });

  it('does not send an amount that is not valid', async () => {
    const server = setup();
    const { user } = open();

    const field = await screen.findByLabelText('¿Cuántos créditos quieres donar?');
    await user.clear(field);
    await user.click(screen.getByRole('button', { name: 'Donar' }));

    expect(await screen.findByText('Escribe cuántos créditos quieres donar')).toBeInTheDocument();
    expect(server.requests('POST', DONATIONS)).toHaveLength(0);
  });

  it('says there are no benefits, and offers to retry when they fail', async () => {
    const server = setup([]);
    const first = open();
    expect(await screen.findByText('Tu universidad todavía no ofrece beneficios')).toBeInTheDocument();
    first.unmount();

    server.on('GET', BENEFITS, { status: 500, body: { status: 500, message: 'boom' } });
    open();
    expect(await screen.findByText('No pudimos cargar los beneficios')).toBeInTheDocument();
    expect(screen.queryByText('boom')).not.toBeInTheDocument();
  });
});
