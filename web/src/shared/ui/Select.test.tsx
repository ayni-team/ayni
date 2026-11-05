import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Select } from './Select';

describe('Select', () => {
  it('draws the chevron of the design instead of the arrow of the browser', () => {
    render(<Select aria-label="Motivo" options={[{ value: 'a', label: 'A' }]} />);

    const select = screen.getByRole('combobox', { name: 'Motivo' });
    expect(select).toHaveClass('appearance-none', 'select-chevron');
  });
});
