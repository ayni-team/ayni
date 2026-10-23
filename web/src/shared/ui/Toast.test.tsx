import { act, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ToastProvider, useToast } from './Toast';

function Cancel() {
  const toast = useToast();
  return (
    <>
      <button type="button" onClick={() => toast.success('Te devolvimos 1 crédito')}>
        Cancelar
      </button>
      <button type="button" onClick={() => toast.error('No se pudo cancelar')}>
        Fallar
      </button>
    </>
  );
}

describe('Toast', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('announces what happened and leaves on its own', () => {
    render(
      <ToastProvider>
        <Cancel />
      </ToastProvider>,
    );
    act(() => screen.getByRole('button', { name: 'Cancelar' }).click());
    expect(screen.getByRole('status')).toHaveTextContent('Te devolvimos 1 crédito');

    act(() => vi.advanceTimersByTime(5000));
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('interrupts for an error, and can be closed by hand', () => {
    render(
      <ToastProvider>
        <Cancel />
      </ToastProvider>,
    );
    act(() => screen.getByRole('button', { name: 'Fallar' }).click());
    expect(screen.getByRole('alert')).toHaveTextContent('No se pudo cancelar');
    act(() => screen.getByRole('button', { name: 'Cerrar aviso' }).click());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('needs its provider', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    expect(() => render(<Cancel />)).toThrow(/ToastProvider/);
  });
});
