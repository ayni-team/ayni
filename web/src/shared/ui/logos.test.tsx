import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AyniLogo } from './AyniLogo';
import { IconBell } from './icons';
import { UniversityLogo } from './UniversityLogo';

describe('UniversityLogo', () => {
  const upc = { code: 'UPC', name: 'Universidad Peruana de Ciencias Aplicadas', logoUrl: '/logos/upc.png' };

  it('shows the logo the university declares, named', () => {
    render(<UniversityLogo university={upc} />);
    expect(screen.getByRole('img', { name: 'Logo de Universidad Peruana de Ciencias Aplicadas' })).toHaveAttribute(
      'src',
      '/logos/upc.png',
    );
  });

  it('shows its code on its colour when the logo fails or is missing', () => {
    render(<UniversityLogo university={upc} />);
    fireEvent.error(screen.getByRole('img'));
    expect(screen.getByRole('img', { name: /Logo de Universidad Peruana/ })).toHaveTextContent('UPC');
  });
});

describe('AyniLogo', () => {
  it('reads as Ayni once', () => {
    render(<AyniLogo />);
    expect(screen.getAllByRole('img', { name: 'Ayni' })).toHaveLength(1);
  });

  it('draws the white word on a dark background', () => {
    const { container } = render(<AyniLogo tone="light" />);
    expect(container.querySelectorAll('path')[1]).toHaveAttribute('fill', '#FFFFFF');
  });

  it('names the mark alone', () => {
    render(<AyniLogo markOnly />);
    expect(screen.getByRole('img', { name: 'Ayni' })).toBeInTheDocument();
  });
});

describe('icons', () => {
  it('are decorative unless labelled', () => {
    const { container } = render(<IconBell />);
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    render(<IconBell label="Avisos" />);
    expect(screen.getByRole('img', { name: 'Avisos' })).toBeInTheDocument();
  });
});
