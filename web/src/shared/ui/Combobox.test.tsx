import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { Combobox, type ComboboxOption } from './Combobox';
import { Field } from './Field';

const COURSES: ComboboxOption[] = [
  { value: 'c1', label: 'Álgebra Lineal', description: 'MA264 · curso UPC' },
  { value: 'c2', label: 'Algoritmos', description: 'CC215 · curso UPC' },
  { value: 'c3', label: 'Diseño de Base de Datos', description: 'SI400 · curso UPC' },
];

function Picker({ initial = '' }: { initial?: string }) {
  const [value, setValue] = useState(initial);
  return (
    <>
      <Field label="Curso o habilidad">
        <Combobox options={COURSES} value={value} onChange={setValue} placeholder="Escribe un curso" />
      </Field>
      <output>{value}</output>
    </>
  );
}

describe('Combobox', () => {
  it('takes its label from the Field and narrows the list as one types, ignoring accents', async () => {
    const user = userEvent.setup();
    render(<Picker />);
    const input = screen.getByRole('combobox', { name: 'Curso o habilidad' });
    await user.type(input, 'algebra');
    expect(input).toHaveAttribute('aria-expanded', 'true');
    expect(screen.getAllByRole('option').map((o) => o.textContent)).toEqual(['Álgebra LinealMA264 · curso UPC']);
  });

  it('searches the description too', async () => {
    const user = userEvent.setup();
    render(<Picker />);
    await user.type(screen.getByRole('combobox'), 'si400');
    expect(screen.getByRole('option', { name: /Diseño de Base de Datos/ })).toBeInTheDocument();
  });

  it('chooses with the arrows and Enter, and shows the chosen label', async () => {
    const user = userEvent.setup();
    render(<Picker />);
    const input = screen.getByRole('combobox');
    await user.click(input);
    await user.keyboard('{ArrowDown}{Enter}');
    expect(screen.getByText('c2', { selector: 'output' })).toBeInTheDocument();
    expect(input).toHaveValue('Algoritmos');
    expect(input).toHaveAttribute('aria-expanded', 'false');
  });

  it('chooses with a click', async () => {
    const user = userEvent.setup();
    render(<Picker />);
    await user.click(screen.getByRole('combobox'));
    await user.click(screen.getByRole('option', { name: /Diseño de Base de Datos/ }));
    expect(screen.getByText('c3', { selector: 'output' })).toBeInTheDocument();
  });

  it('keeps the choice there was when Escape closes it', async () => {
    const user = userEvent.setup();
    render(<Picker initial="c2" />);
    const input = screen.getByRole('combobox');
    await user.click(input);
    await user.type(input, 'xyz');
    expect(screen.getByText('Nada coincide con lo que escribiste')).toBeInTheDocument();
    await user.keyboard('{Escape}');
    expect(input).toHaveValue('Algoritmos');
    expect(screen.getByText('c2', { selector: 'output' })).toBeInTheDocument();
  });
});
