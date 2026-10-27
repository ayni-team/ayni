import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Credits, formatCredits } from './Credits';

describe('Credits', () => {
  it('says one credit and many credits', () => {
    expect(formatCredits(1)).toBe('1 crédito');
    expect(formatCredits(3)).toBe('3 créditos');
    expect(formatCredits(0)).toBe('0 créditos');
    expect(formatCredits(1200)).toBe('1,200 créditos');
  });

  it('writes the direction of a movement when signed', () => {
    expect(formatCredits(3, { signed: true })).toBe('+3 créditos');
    expect(formatCredits(-1, { signed: true })).toBe('−1 crédito');
  });

  it('draws the amount', () => {
    render(<Credits amount={12} />);
    expect(screen.getByText('12 créditos')).toBeInTheDocument();
  });
});
