import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Avatar, initialsOf } from './Avatar';

describe('Avatar', () => {
  it('derives the initials from the name', () => {
    expect(initialsOf('Ana Torres')).toBe('AT');
    expect(initialsOf('  lucía   paredes ríos ')).toBe('LP');
    expect(initialsOf('Bruno')).toBe('B');
    expect(initialsOf(undefined)).toBe('');
  });

  it('shows the initials, named, when there is no photo', () => {
    render(<Avatar name="Bruno Salas" />);
    expect(screen.getByRole('img', { name: 'Bruno Salas' })).toHaveTextContent('BS');
  });

  it('falls back to the initials when the photo does not load', () => {
    render(<Avatar name="Ana Torres" photoUrl="https://example.org/ana.png" />);
    fireEvent.error(screen.getByRole('img', { name: 'Ana Torres' }));
    expect(screen.getByRole('img', { name: 'Ana Torres' })).toHaveTextContent('AT');
  });

  it('is not read twice beside the written name', () => {
    render(<Avatar name="Ana Torres" decorative />);
    expect(screen.queryByRole('img')).not.toBeInTheDocument();
  });
});
