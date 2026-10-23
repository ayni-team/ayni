import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Badge } from './Badge';

describe('Badge', () => {
  it('says the status in words, whatever its colour', () => {
    render(<Badge tone="success">Confirmada</Badge>);
    expect(screen.getByText('Confirmada')).toHaveClass('bg-green-50');
  });

  it('wears the university soft tint when it is the brand one', () => {
    render(<Badge tone="brand">Tutor nuevo</Badge>);
    expect(screen.getByText('Tutor nuevo')).toHaveClass('bg-brand-soft', 'text-brand-text');
  });
});
