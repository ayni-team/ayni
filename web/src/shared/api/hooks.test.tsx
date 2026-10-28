import { QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { testQueryClient } from '@/test/render';
import { useApiMutation, useApiQuery } from './hooks';

function wrapper() {
  const client = testQueryClient();
  return {
    client,
    Wrapper: ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    ),
  };
}

describe('useApiQuery', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('reads the path with its query under the key', async () => {
    const server = fakeServer().on('GET', '/api/v1/bookings/mine', { body: { items: [], total: 0 } });
    const { Wrapper, client } = wrapper();

    const { result } = renderHook(
      () =>
        useApiQuery(['booking', 'mine', { view: 'HISTORY' }], '/api/v1/bookings/mine', {
          query: { view: 'HISTORY', page: 1 },
        }),
      { wrapper: Wrapper },
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(server.requests()[0].query.toString()).toBe('view=HISTORY&page=1');
    expect(client.getQueryData(['booking', 'mine', { view: 'HISTORY' }])).toEqual({ items: [], total: 0 });
  });
});

describe('useApiMutation', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('sends one request for a double click', async () => {
    const server = fakeServer().on('POST', '/api/v1/payments/purchases', async () => {
      await new Promise((resolve) => setTimeout(resolve, 20));
      return { status: 201, body: { status: 'CONFIRMED' } };
    });
    const { Wrapper } = wrapper();
    const { result } = renderHook(() => useApiMutation('post', '/api/v1/payments/purchases', { idempotent: true }), {
      wrapper: Wrapper,
    });

    act(() => {
      result.current.mutate({ body: { credits: 1 } });
      result.current.mutate({ body: { credits: 1 } });
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(server.requests('POST')).toHaveLength(1);
  });

  it('keeps the key while the same attempt is retried and takes a new one after an answer', async () => {
    let status = 503;
    const server = fakeServer().on('POST', '/api/v1/payments/purchases', () => ({
      status,
      body: { status: 'FAILED' },
    }));
    const { Wrapper } = wrapper();
    const { result } = renderHook(() => useApiMutation('post', '/api/v1/payments/purchases', { idempotent: true }), {
      wrapper: Wrapper,
    });
    const send = () => act(() => result.current.mutateAsync({ body: { credits: 1 } }).catch(() => undefined));

    await send(); // the platform failed: the same attempt
    await send();
    status = 201;
    await send(); // answered: the attempt is over
    await send();

    const keys = server.requests('POST').map((r) => r.headers.get('Idempotency-Key'));
    expect(keys[0]).toBeTruthy();
    expect(keys[1]).toBe(keys[0]);
    expect(keys[2]).toBe(keys[0]);
    expect(keys[3]).not.toBe(keys[2]);
  });

  it('takes a new key after a refusal', async () => {
    const server = fakeServer().on('POST', '/api/v1/payments/purchases', {
      status: 409,
      body: { message: 'Monthly limit' },
    });
    const { Wrapper } = wrapper();
    const { result } = renderHook(() => useApiMutation('post', '/api/v1/payments/purchases', { idempotent: true }), {
      wrapper: Wrapper,
    });

    await act(() => result.current.mutateAsync({ body: { credits: 9 } }).catch(() => undefined));
    await act(() => result.current.mutateAsync({ body: { credits: 1 } }).catch(() => undefined));

    const [first, second] = server.requests('POST').map((r) => r.headers.get('Idempotency-Key'));
    expect(second).not.toBe(first);
  });

  it('refreshes the keys it invalidates once it succeeds', async () => {
    fakeServer()
      .on('DELETE', '/api/v1/bookings/b1', { status: 204 })
      .on('GET', '/api/v1/wallet', { body: { available: 5 } });
    const { Wrapper, client } = wrapper();
    client.setQueryData(['wallet', 'balance'], { available: 4 });
    const { result } = renderHook(
      () => useApiMutation('delete', '/api/v1/bookings/{id}', { invalidates: [['wallet']] }),
      {
        wrapper: Wrapper,
      },
    );

    await act(() => result.current.mutateAsync({ path: { id: 'b1' } }));

    expect(client.getQueryState(['wallet', 'balance'])?.isInvalidated).toBe(true);
  });
});
