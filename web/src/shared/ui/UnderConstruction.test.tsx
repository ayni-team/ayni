import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { UnderConstruction } from './UnderConstruction';

describe('UnderConstruction', () => {
  it('names the screen it stands for', () => {
    render(<UnderConstruction screen="FE-12" title="Mis tutorías" />);
    expect(screen.getByRole('heading', { level: 1, name: 'Mis tutorías' })).toBeInTheDocument();
    expect(screen.getByText('FE-12')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'En construcción' })).toBeInTheDocument();
  });
});
