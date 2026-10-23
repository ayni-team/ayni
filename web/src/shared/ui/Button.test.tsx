import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { Button, ButtonLink } from './Button';

describe('Button', () => {
  it('does what it says when clicked', async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Confirmar</Button>);
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar' }));
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('cannot be sent twice while it waits for the answer', async () => {
    const onClick = vi.fn();
    render(
      <Button loading onClick={onClick}>
        Confirmar
      </Button>,
    );
    const button = screen.getByRole('button', { name: 'Confirmar' });
    expect(button).toBeDisabled();
    expect(button).toHaveAttribute('aria-busy', 'true');
    await userEvent.click(button);
    expect(onClick).not.toHaveBeenCalled();
  });

  it('is a plain button, not a submit, unless asked', () => {
    render(<Button variant="danger">Cancelar tutoría</Button>);
    expect(screen.getByRole('button')).toHaveAttribute('type', 'button');
  });

  it('can look like a button and still be a link', () => {
    render(
      <MemoryRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <ButtonLink to="/buscar" variant="secondary">
          Buscar
        </ButtonLink>
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: 'Buscar' })).toHaveAttribute('href', '/buscar');
  });
});
