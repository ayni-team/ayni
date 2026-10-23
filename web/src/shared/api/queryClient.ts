import { QueryClient } from '@tanstack/react-query';
import { isApiError } from './errors';

/**
 * The cache of server data. A query is retried only when the platform failed (no network, a 5xx):
 * a 4xx will answer the same again. Mutations are never retried by the cache; the person decides.
 */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: false,
        retry: (failures, error) => failures < 2 && isApiError(error) && error.retriable,
      },
      mutations: { retry: false },
    },
  });
}
