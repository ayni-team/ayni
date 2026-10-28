import { afterEach, describe, expect, it, vi } from 'vitest';
import { fakeServer } from '@/test/fakeServer';
import { signIn } from '@/test/fixtures';
import { currentSession } from '../session/session';
import { api, apiUrl, buildUrl, downloadFile, uploadFiles } from './client';
import { ApiError } from './errors';

describe('buildUrl', () => {
  it('fills the path parameters and skips the empty query values', () => {
    expect(buildUrl('/api/v1/bookings/{id}', { id: 'a b' }, { confirmLate: true, page: undefined, q: '' })).toBe(
      '/api/v1/bookings/a%20b?confirmLate=true',
    );
  });

  it('repeats a parameter for every value of a list', () => {
    expect(buildUrl('/api/v1/x', undefined, { tag: ['A', 'B'] })).toBe('/api/v1/x?tag=A&tag=B');
  });

  it('refuses a path parameter that is missing', () => {
    expect(() => buildUrl('/api/v1/bookings/{id}')).toThrow(/id/);
  });
});

describe('apiUrl', () => {
  afterEach(() => {
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
  });

  it('keeps the path relative when no origin of the API is set, as in development', () => {
    vi.stubEnv('VITE_API_BASE_URL', '');
    expect(apiUrl('/api/v1/me')).toBe('/api/v1/me');
  });

  it('puts the origin of the API in front, without doubling the slash', () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.org/');
    expect(apiUrl('/api/v1/me')).toBe('https://api.example.org/api/v1/me');
  });

  it('sends every call to that origin when the web is served from another host', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'https://api.example.org');
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetch);

    await api.get('/api/v1/me');

    expect(fetch.mock.calls[0][0]).toBe('https://api.example.org/api/v1/me');
  });
});

describe('api', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('sends the session token and nothing else that identifies the person', async () => {
    const server = fakeServer().on('GET', '/api/v1/wallet', { body: { available: 3 } });
    signIn('STUDENT');

    await expect(api.get('/api/v1/wallet')).resolves.toEqual({ available: 3 });

    const [request] = server.requests();
    expect(request.headers.get('Authorization')).toBe('Bearer token-student');
    expect(request.headers.get('X-Tenant-Id')).toBeNull();
  });

  it('sends the body as JSON and the idempotency key as a header', async () => {
    const server = fakeServer().on('POST', '/api/v1/payments/purchases', { status: 201, body: { status: 'CONFIRMED' } });

    await api.post('/api/v1/payments/purchases', { body: { credits: 2 }, idempotencyKey: 'key-1' });

    const [request] = server.requests();
    expect(request.body).toEqual({ credits: 2 });
    expect(request.headers.get('Content-Type')).toBe('application/json');
    expect(request.headers.get('Idempotency-Key')).toBe('key-1');
  });

  it('answers nothing for a 204', async () => {
    fakeServer().on('DELETE', '/api/v1/bookings/b1', { status: 204 });
    await expect(api.delete('/api/v1/bookings/{id}', { path: { id: 'b1' } })).resolves.toBeUndefined();
  });

  it('turns a refusal into an ApiError that keeps the status, the kind and the body', async () => {
    const body = { status: 409, message: 'Late cancellation', path: '/api/v1/bookings/b1', late: true };
    fakeServer().on('DELETE', '/api/v1/bookings/b1', { status: 409, body });

    const error = await api.delete('/api/v1/bookings/{id}', { path: { id: 'b1' } }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ status: 409, kind: 'conflict', message: 'Late cancellation', body });
  });

  it('forgets the session on a 401, so the guards send the person to sign in', async () => {
    fakeServer().on('GET', '/api/v1/me', { status: 401, body: { message: 'Sign in first' } });
    signIn('STUDENT');

    await expect(api.get('/api/v1/me')).rejects.toMatchObject({ kind: 'unauthorized' });
    expect(currentSession()).toBeNull();
  });

  it('says the network failed when the server cannot be reached', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await expect(api.get('/api/v1/wallet')).rejects.toMatchObject({ status: 0, kind: 'network', retriable: true });
  });
});

describe('files', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('uploads files and fields as multipart, with the path filled', async () => {
    const server = fakeServer().on('POST', '/api/v1/tutor/skills/s1/validation', { status: 201, body: { id: 'e1' } });
    const file = new File(['%PDF'], 'certificado.pdf', { type: 'application/pdf' });

    await uploadFiles('/api/v1/tutor/skills/{id}/validation', [file], { note: 'Curso de Coursera' }, { path: { id: 's1' } });

    const form = server.requests()[0].body as FormData;
    expect(form).toBeInstanceOf(FormData);
    expect((form.getAll('files')[0] as File).name).toBe('certificado.pdf');
    expect(form.get('note')).toBe('Curso de Coursera');
  });

  it('downloads an attachment with the name the server gives it', async () => {
    fakeServer().on('GET', '/api/v1/coordinator/indicators/export', {
      body: 'day,hours',
      headers: { 'Content-Disposition': 'attachment; filename="indicadores.csv"' },
    });
    const createObjectURL = vi.fn(() => 'blob:csv');
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL: vi.fn() }));
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.download).toBe('indicadores.csv');
    });

    await downloadFile('/api/v1/coordinator/indicators/export', 'export.csv', { query: { from: '2026-10-01', to: '2026-10-31' } });

    expect(click).toHaveBeenCalledOnce();
    expect(createObjectURL).toHaveBeenCalledOnce();
  });
});
