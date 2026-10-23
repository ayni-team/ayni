import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryKey,
  type UseMutationResult,
  type UseQueryOptions,
  type UseQueryResult,
} from '@tanstack/react-query';
import { useCallback, useRef } from 'react';
import { requestUntyped } from './client';
import { ApiError } from './errors';
import { newIdempotencyKey } from './idempotency';
import type {
  HttpMethod,
  OptionsArgument,
  Operation,
  PathsWith,
  RequestOptions,
  RequiresIdempotencyKey,
  ResponseOf,
} from './types';

/**
 * React Query over the typed client. Screens read server data with `useApiQuery` and change it
 * with `useApiMutation`; neither calls `fetch` nor writes a response type by hand.
 *
 * Query keys are `[module, resource, params]`, for example `['booking', 'mine', { view, page }]`:
 * invalidating `['booking']` refreshes everything of the module.
 */
export type ApiQueryKey = readonly [module: string, resource: string, params?: unknown];

type QueryBehaviour<T> = Pick<
  UseQueryOptions<T, ApiError>,
  | 'enabled'
  | 'staleTime'
  | 'gcTime'
  | 'refetchInterval'
  | 'refetchIntervalInBackground'
  | 'refetchOnWindowFocus'
  | 'placeholderData'
  | 'retry'
>;

type QueryOptions<P extends PathsWith<'get'>> = Omit<RequestOptions<P, 'get'>, 'signal'> &
  QueryBehaviour<ResponseOf<P, 'get'>>;

export function useApiQuery<P extends PathsWith<'get'>>(
  key: ApiQueryKey,
  path: P,
  ...[options]: OptionsArgument<QueryOptions<P>>
): UseQueryResult<ResponseOf<P, 'get'>, ApiError> {
  const { path: pathParams, query, ...behaviour } = (options ?? {}) as {
    path?: Record<string, unknown>;
    query?: Record<string, unknown>;
  } & QueryBehaviour<ResponseOf<P, 'get'>>;
  return useQuery<ResponseOf<P, 'get'>, ApiError>({
    queryKey: key as QueryKey,
    queryFn: ({ signal }) =>
      requestUntyped('get', path, { path: pathParams, query, signal }) as Promise<ResponseOf<P, 'get'>>,
    ...behaviour,
  });
}

type MutationMethod = Exclude<HttpMethod, 'get'>;

/** What `mutate` takes: the request, where the idempotency key is optional (the hook makes one). */
export type MutationVariables<P extends PathsWith<M>, M extends MutationMethod> = Omit<
  RequestOptions<P, M>,
  'idempotencyKey' | 'signal'
> & { idempotencyKey?: string };

type MutationOptions<P extends PathsWith<M>, M extends MutationMethod> = (RequiresIdempotencyKey<
  Operation<P, M>
> extends true
  ? { idempotent: true }
  : { idempotent?: boolean }) & {
  /** Keys to invalidate once it succeeds, for example `[['wallet']]` after spending credits. */
  invalidates?: readonly (readonly unknown[])[];
  onSuccess?: (data: ResponseOf<P, M>, variables: MutationVariables<P, M>) => void;
  onError?: (error: ApiError, variables: MutationVariables<P, M>) => void;
};

export type ApiMutation<P extends PathsWith<M>, M extends MutationMethod> = UseMutationResult<
  ResponseOf<P, M>,
  ApiError,
  MutationVariables<P, M>
> & {
  /** Ends the current attempt, so the next send takes a new idempotency key. */
  renewIdempotencyKey: () => void;
};

/**
 * A call that changes something. While one is in flight a second `mutate` is ignored, so a double
 * click sends one request.
 *
 * With `idempotent` (required by the types when the operation declares `Idempotency-Key`), each
 * attempt carries a key: kept while the same attempt is retried after the platform failed, and
 * replaced once the server answered it, whether it accepted or refused it.
 */
export function useApiMutation<M extends MutationMethod, P extends PathsWith<M>>(
  method: M,
  path: P,
  ...[options]: OptionsArgument<MutationOptions<P, M>>
): ApiMutation<P, M> {
  const queryClient = useQueryClient();
  const { idempotent = false, invalidates = [], onSuccess, onError } = (options ?? {}) as {
    idempotent?: boolean;
  } & Omit<MutationOptions<P, M>, 'idempotent'>;
  const attemptKey = useRef<string | null>(null);
  const inFlight = useRef<Promise<ResponseOf<P, M>> | null>(null);

  const mutation = useMutation<ResponseOf<P, M>, ApiError, MutationVariables<P, M>>({
    mutationFn: (variables) => {
      const { path: pathParams, query, body, idempotencyKey } = variables as {
        path?: Record<string, unknown>;
        query?: Record<string, unknown>;
        body?: unknown;
        idempotencyKey?: string;
      };
      let key = idempotencyKey;
      if (!key && idempotent) {
        attemptKey.current ??= newIdempotencyKey();
        key = attemptKey.current;
      }
      return requestUntyped(method, path, { path: pathParams, query, body, idempotencyKey: key }) as Promise<
        ResponseOf<P, M>
      >;
    },
    onSuccess: async (data, variables) => {
      attemptKey.current = null;
      await Promise.all(invalidates.map((queryKey) => queryClient.invalidateQueries({ queryKey })));
      onSuccess?.(data, variables);
    },
    onError: (error, variables) => {
      if (!(error instanceof ApiError) || !error.retriable) {
        attemptKey.current = null;
      }
      onError?.(error, variables);
    },
  });

  const { mutateAsync: send } = mutation;

  const mutateAsync = useCallback(
    (variables: MutationVariables<P, M>) => {
      if (inFlight.current) return inFlight.current;
      const pending = send(variables).finally(() => {
        inFlight.current = null;
      });
      inFlight.current = pending;
      return pending;
    },
    [send],
  );

  const mutate = useCallback(
    (variables: MutationVariables<P, M>) => {
      // The error is already in the mutation's state; nobody is waiting for this promise.
      mutateAsync(variables).catch(() => undefined);
    },
    [mutateAsync],
  );

  const renewIdempotencyKey = useCallback(() => {
    attemptKey.current = null;
  }, []);

  return { ...mutation, mutate, mutateAsync, renewIdempotencyKey } as ApiMutation<P, M>;
}
