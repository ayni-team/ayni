import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { PageHeader } from './PageHeader';

describe('PageHeader', () => {
  it('is the title of the screen, with a way back', () => {
    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <PageHeader title="Comprobante" description="Compra del 23 de octubre" back={{ to: '/creditos/compras', label: 'Mis compras' }} />
      </MemoryRouter>,
    );
    expect(screen.getByRole('heading', { level: 1, name: 'Comprobante' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Mis compras' })).toHaveAttribute('href', '/creditos/compras');
  });
});
