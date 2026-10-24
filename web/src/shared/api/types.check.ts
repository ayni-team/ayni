/**
 * Compile-time checks of the typed client: `npm run build` fails if the types stop catching these.
 * Nothing here runs; the functions are never called.
 */
import { api } from './client';
import { useApiMutation, useApiQuery } from './hooks';
import type { Schema } from './index';

type Equal<A, B> = (<T>() => T extends A ? 1 : 2) extends <T>() => T extends B ? 1 : 2 ? true : false;
function expectType<T extends true>(): T | undefined {
  return undefined;
}

export async function clientChecks() {
  const balance = await api.get('/api/v1/wallet');
  expectType<Equal<typeof balance, Schema<'WalletBalance'>>>();

  // A path parameter is required where the path has one.
  await api.delete('/api/v1/bookings/{id}', { path: { id: 'b' }, query: { confirmLate: true } });
  // @ts-expect-error the id is missing
  await api.delete('/api/v1/bookings/{id}');

  // A required query parameter is required.
  // @ts-expect-error name is missing
  await api.get('/api/v1/catalog/similar', { query: {} });

  // A path that does not exist, or does not answer the method, is refused.
  // @ts-expect-error not a path of the API
  await api.get('/api/v1/nothing');
  // @ts-expect-error the wallet is read, not posted
  await api.post('/api/v1/wallet');

  // An operation that declares Idempotency-Key needs one.
  // @ts-expect-error the key is missing
  await api.post('/api/v1/payments/purchases', { body: { credits: 3 } });
  await api.post('/api/v1/payments/purchases', { body: { credits: 3 }, idempotencyKey: 'k' });

  // 204 answers nothing.
  const signedOut = await api.post('/api/v1/access/logout');
  expectType<Equal<typeof signedOut, undefined>>();
}

export function hookChecks() {
  const mine = useApiQuery(['booking', 'mine', { view: 'UPCOMING' }], '/api/v1/bookings/mine', {
    query: { view: 'UPCOMING' },
  });
  expectType<Equal<typeof mine.data, Schema<'StudentBookings'> | undefined>>();

  // @ts-expect-error a purchase moves credits: the mutation must be idempotent
  useApiMutation('post', '/api/v1/payments/purchases');
  const purchase = useApiMutation('post', '/api/v1/payments/purchases', { idempotent: true });
  purchase.mutate({ body: { credits: 2 } });
}
