import { describe, expect, it } from 'vitest';
import { ApiError, errorBody, errorText, kindOf } from './errors';

describe('errors', () => {
  it('names the kind of each status of the guide', () => {
    expect([0, 400, 401, 403, 404, 409, 422, 500, 503, 418].map(kindOf)).toEqual([
      'network',
      'badRequest',
      'unauthorized',
      'forbidden',
      'notFound',
      'conflict',
      'unprocessable',
      'server',
      'server',
      'unknown',
    ]);
  });

  it('writes the Spanish of the guide, never the server message', () => {
    const error = new ApiError(403, 'Only COORDINATOR may call this', '/api/v1/coordinator/x');
    expect(errorText(error)).toBe('No tienes permiso para esto');
    expect(errorText(new ApiError(404, 'Not found', '/x'))).toBe('No encontramos lo que buscas');
    expect(errorText(new ApiError(400, 'Bad', '/x'))).toBe('Revisa los datos e inténtalo de nuevo');
  });

  it('lets a screen say its own text for the kinds it knows', () => {
    const error = new ApiError(422, 'Wrong code', '/x');
    expect(errorText(error, { unprocessable: 'El código no es correcto' })).toBe('El código no es correcto');
  });

  it('retries only what the platform failed', () => {
    expect(new ApiError(503, '', '/x').retriable).toBe(true);
    expect(ApiError.network('/x').retriable).toBe(true);
    expect(new ApiError(409, '', '/x').retriable).toBe(false);
  });

  it('hands the body of a refusal to whoever reads it', () => {
    const error = new ApiError(409, '', '/x', { missingHours: 4 });
    expect(errorBody<{ missingHours: number }>(error)?.missingHours).toBe(4);
    expect(errorBody(new Error('x'))).toBeUndefined();
  });
});
