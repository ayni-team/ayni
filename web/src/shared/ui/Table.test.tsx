import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Table } from './Table';

describe('Table', () => {
  it('draws a row per item with its column headers and a caption', () => {
    render(
      <Table
        caption="Mis compras"
        rows={[
          { id: 'p1', credits: 3, status: 'Confirmada' },
          { id: 'p2', credits: 1, status: 'Pendiente' },
        ]}
        rowKey={(row) => row.id}
        columns={[
          { key: 'credits', header: 'Créditos', cell: (row) => row.credits, align: 'right' },
          { key: 'status', header: 'Estado', cell: (row) => row.status },
        ]}
      />,
    );
    const table = screen.getByRole('table', { name: 'Mis compras' });
    expect(within(table).getAllByRole('columnheader').map((th) => th.textContent)).toEqual(['Créditos', 'Estado']);
    const rows = within(table).getAllByRole('row').slice(1);
    expect(rows).toHaveLength(2);
    expect(within(rows[1]).getByText('Pendiente')).toBeInTheDocument();
  });
});
