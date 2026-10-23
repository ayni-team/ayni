import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { pageCount, Pagination } from './Pagination';

describe('Pagination', () => {
  it('moves a page at a time and says where the person is', async () => {
    const onPageChange = vi.fn();
    render(<Pagination page={0} totalPages={3} onPageChange={onPageChange} />);
    expect(screen.getByText('Página 1 de 3')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Anterior' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Siguiente' }));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it('cannot go past the last page', () => {
    render(<Pagination page={2} totalPages={3} onPageChange={() => undefined} />);
    expect(screen.getByRole('button', { name: 'Siguiente' })).toBeDisabled();
  });

  it('draws nothing when everything fits in a page', () => {
    const { container } = render(<Pagination page={0} totalPages={1} onPageChange={() => undefined} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('counts the pages of an answer that gives total and size', () => {
    expect(pageCount(41, 20)).toBe(3);
    expect(pageCount(0, 20)).toBe(0);
  });
});
