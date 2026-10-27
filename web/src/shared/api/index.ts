export { api, apiUrl, buildUrl, downloadFile, uploadFiles } from './client';
export { ApiError, errorBody, errorText, isApiError, kindOf } from './errors';
export type { ApiErrorKind, ServerError } from './errors';
export { useApiMutation, useApiQuery } from './hooks';
export type { ApiMutation, ApiQueryKey, MutationVariables } from './hooks';
export { newIdempotencyKey, useIdempotencyKey } from './idempotency';
export type { IdempotencyKey } from './idempotency';
export { createQueryClient } from './queryClient';
export type { components, paths } from './schema';
export type { PathsWith, RequestOptions, ResponseOf } from './types';

import type { components } from './schema';

/** A schema of the OpenAPI document by name: `Schema<'WalletBalance'>`. */
export type Schema<Name extends keyof components['schemas']> = components['schemas'][Name];
