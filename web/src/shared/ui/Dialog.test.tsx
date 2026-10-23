import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { Button } from './Button';
import { Dialog } from './Dialog';

function LateCancellation({ dismissible = true }: { dismissible?: boolean }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button onClick={() => setOpen(true)}>Cancelar tutoría</Button>
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        dismissible={dismissible}
        title="Faltan menos de 12 horas"
        description="No se te devolverán los créditos."
        footer={
          <>
            <Button variant="danger">Sí, cancelar</Button>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              No, mantener
            </Button>
          </>
        }
      />
    </>
  );
}

describe('Dialog', () => {
  it('opens named by its title and described by its text', async () => {
    render(<LateCancellation />);
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar tutoría' }));
    const dialog = screen.getByRole('dialog', { name: 'Faltan menos de 12 horas' });
    expect(dialog).toHaveAttribute('aria-modal', 'true');
    expect(dialog).toHaveAccessibleDescription('No se te devolverán los créditos.');
  });

  it('keeps the focus inside while it is open', async () => {
    render(<LateCancellation />);
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar tutoría' }));
    expect(screen.getByRole('button', { name: 'Cerrar' })).toHaveFocus();

    await userEvent.tab();
    expect(screen.getByRole('button', { name: 'Sí, cancelar' })).toHaveFocus();
    await userEvent.tab();
    expect(screen.getByRole('button', { name: 'No, mantener' })).toHaveFocus();
    await userEvent.tab();
    expect(screen.getByRole('button', { name: 'Cerrar' })).toHaveFocus();
    await userEvent.tab({ shift: true });
    expect(screen.getByRole('button', { name: 'No, mantener' })).toHaveFocus();
  });

  it('closes with Escape and gives the focus back to what opened it', async () => {
    render(<LateCancellation />);
    const opener = screen.getByRole('button', { name: 'Cancelar tutoría' });
    await userEvent.click(opener);
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(opener).toHaveFocus();
  });

  it('cannot be closed halfway while something is being sent', async () => {
    render(<LateCancellation dismissible={false} />);
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar tutoría' }));
    await userEvent.keyboard('{Escape}');
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cerrar' })).not.toBeInTheDocument();
  });
});
