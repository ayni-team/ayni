import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/errors';
import { EmptyState } from './EmptyState';
import { ErrorState } from './ErrorState';
import { Skeleton } from './Skeleton';
import { PageLoading, Spinner } from './Spinner';

describe('Spinner', () => {
  it('says it is loading to screen readers', () => {
    render(<Spinner label="Cargando tus tutorías" />);
    expect(screen.getByRole('status')).toHaveTextContent('Cargando tus tutorías');
  });

  it('stays quiet inside something that already says it is busy', () => {
    render(<Spinner decorative />);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('fills a page that is loading', () => {
    render(<PageLoading />);
    expect(screen.getByRole('status')).toHaveTextContent('Cargando');
  });
});

describe('Skeleton', () => {
  it('draws the shape of what is loading, announced once', () => {
    render(<Skeleton lines={3} label="Cargando movimientos" />);
    const status = screen.getByRole('status', { name: 'Cargando movimientos' });
    expect(status.children).toHaveLength(3);
  });
});

describe('EmptyState', () => {
  it('says what there is none of and what to do next', () => {
    render(<EmptyState title="Todavía no tienes tutorías" description="Busca una en Buscar" action={<a href="/buscar">Buscar</a>} />);
    expect(screen.getByRole('heading', { name: 'Todavía no tienes tutorías' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Buscar' })).toBeInTheDocument();
  });
});

describe('ErrorState', () => {
  it('says what failed in Spanish, never the server message, and retries', async () => {
    const onRetry = vi.fn();
    render(<ErrorState error={new ApiError(503, 'Upstream timed out', '/api/v1/wallet')} onRetry={onRetry} />);
    const alert = screen.getByRole('alert');
    expect(alert).toHaveTextContent('Algo falló de nuestro lado. Inténtalo de nuevo');
    expect(alert).not.toHaveTextContent('Upstream');
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });
});
