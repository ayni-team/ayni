import type { paths } from './schema';

/**
 * Types that read the generated `schema.d.ts`, so that a call names a path of the OpenAPI document
 * and gets back exactly the parameters it takes and the body it answers. Nothing here is written by
 * hand about a particular endpoint: if a type is wrong, the backend's document is.
 */

export type HttpMethod = 'get' | 'post' | 'put' | 'delete';

/** Every path of the API that answers the method. */
export type PathsWith<M extends HttpMethod> = {
  [P in keyof paths]: [NonNullable<paths[P][M]>] extends [never] ? never : P;
}[keyof paths];

export type Operation<P extends keyof paths, M extends HttpMethod> = NonNullable<paths[P][M]>;

type ParametersOf<Op> = Op extends { parameters: infer Ps } ? Ps : never;

type PathPart<Ps> = Ps extends { path: infer X } ? { path: X } : { path?: undefined };

type QueryPart<Ps> = Ps extends { query: infer X }
  ? { query: X }
  : Ps extends { query?: infer X }
    ? [NonNullable<X>] extends [never]
      ? { query?: undefined }
      : { query?: NonNullable<X> }
    : { query?: undefined };

type BodyPart<Op> = Op extends { requestBody?: never }
  ? { body?: undefined }
  : Op extends { requestBody: { content: infer C } }
    ? { body: C[keyof C] }
    : Op extends { requestBody?: { content: infer C } }
      ? { body?: C[keyof C] }
      : { body?: undefined };

/** Whether the operation declares the `Idempotency-Key` header as required. */
export type RequiresIdempotencyKey<Op> =
  ParametersOf<Op> extends { header: { 'Idempotency-Key': string } } ? true : false;

type KeyPart<Op> = RequiresIdempotencyKey<Op> extends true ? { idempotencyKey: string } : { idempotencyKey?: string };

/** What a call to the path takes: path and query parameters, body and idempotency key. */
export type RequestOptions<P extends keyof paths, M extends HttpMethod> = PathPart<ParametersOf<Operation<P, M>>> &
  QueryPart<ParametersOf<Operation<P, M>>> &
  BodyPart<Operation<P, M>> &
  KeyPart<Operation<P, M>> & { signal?: AbortSignal };

type SuccessStatus = 200 | 201 | 202 | 203 | 204;

type ContentOf<R> = R extends { content: infer C } ? C[keyof C] : undefined;

type SuccessBody<Rs> = {
  [S in keyof Rs]: S extends 204 ? undefined : S extends SuccessStatus ? ContentOf<Rs[S]> : never;
}[keyof Rs];

/** What the path answers when it succeeds; `undefined` for a 204 or a 202 without body. */
export type ResponseOf<P extends keyof paths, M extends HttpMethod> =
  Operation<P, M> extends { responses: infer Rs } ? SuccessBody<Rs> : never;

/** Makes the options argument optional when nothing in it is required. */
export type OptionsArgument<T> = object extends T ? [options?: T] : [options: T];
