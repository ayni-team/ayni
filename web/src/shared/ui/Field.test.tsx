import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { Checkbox } from './Checkbox';
import { Field } from './Field';
import { Select } from './Select';
import { TextArea } from './TextArea';
import { TextField } from './TextField';

describe('Field', () => {
  it('ties its label, hint and error to the input inside', () => {
    render(
      <Field
        label="Correo institucional"
        hint="El de tu universidad"
        error="Tu universidad todavía no está afiliada a Ayni"
        required
      >
        <TextField type="email" />
      </Field>,
    );
    const input = screen.getByRole('textbox', { name: /Correo institucional/ });
    expect(input).toBeRequired();
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('El de tu universidad Tu universidad todavía no está afiliada a Ayni');
  });

  it('is valid while it has no error', () => {
    render(
      <Field label="Presentación">
        <TextField />
      </Field>,
    );
    expect(screen.getByRole('textbox', { name: 'Presentación' })).not.toHaveAttribute('aria-invalid');
  });
});

describe('inputs', () => {
  it('a text area counts what is left when it has a maximum', () => {
    render(
      <Field label="Nota">
        <TextArea maxLength={1000} value="Hola" onChange={() => undefined} showCount />
      </Field>,
    );
    expect(screen.getByRole('textbox', { name: 'Nota' })).toHaveValue('Hola');
    expect(screen.getByText('4 de 1000')).toBeInTheDocument();
  });

  it('a select offers its options under its label', async () => {
    render(
      <Field label="Motivo">
        <Select placeholder="Todos" options={[{ value: 'BOOKING', label: 'Tutoría' }]} defaultValue="" />
      </Field>,
    );
    const select = screen.getByRole('combobox', { name: 'Motivo' });
    await userEvent.selectOptions(select, 'Tutoría');
    expect(select).toHaveValue('BOOKING');
  });

  it('a checkbox toggles from its label', async () => {
    render(<Checkbox label="Confirmo que se distinguen" hint="No es ninguno de la lista" />);
    const box = screen.getByRole('checkbox', { name: 'Confirmo que se distinguen' });
    await userEvent.click(screen.getByText('Confirmo que se distinguen'));
    expect(box).toBeChecked();
    expect(box).toHaveAccessibleDescription('No es ninguno de la lista');
  });
});
