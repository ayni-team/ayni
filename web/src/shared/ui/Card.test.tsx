import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Card } from './Card';

describe('Card', () => {
  it('is a region named by its title', () => {
    render(
      <Card title="Tus créditos" actions={<button type="button">Comprar</button>}>
        12 disponibles
      </Card>,
    );
    const region = screen.getByRole('region', { name: 'Tus créditos' });
    expect(region).toHaveTextContent('12 disponibles');
    expect(screen.getByRole('button', { name: 'Comprar' })).toBeInTheDocument();
  });

  it('is only a block without a title', () => {
    render(<Card>Contenido</Card>);
    expect(screen.queryByRole('region')).not.toBeInTheDocument();
  });
});
