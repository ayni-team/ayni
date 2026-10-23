import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { Tabs } from './Tabs';

function MyTutorings() {
  const [view, setView] = useState<'UPCOMING' | 'HISTORY'>('UPCOMING');
  return (
    <Tabs
      label="Vista de tutorías"
      value={view}
      onChange={setView}
      items={[
        { value: 'UPCOMING', label: 'Próximas', count: 2 },
        { value: 'HISTORY', label: 'Historial' },
      ]}
    >
      {view === 'UPCOMING' ? 'Lista de próximas' : 'Lista del historial'}
    </Tabs>
  );
}

describe('Tabs', () => {
  it('shows the selected tab and switches on click', async () => {
    render(<MyTutorings />);
    expect(screen.getByRole('tab', { name: 'Próximas 2' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tabpanel', { name: 'Próximas 2' })).toHaveTextContent('Lista de próximas');

    await userEvent.click(screen.getByRole('tab', { name: 'Historial' }));
    expect(screen.getByRole('tabpanel')).toHaveTextContent('Lista del historial');
  });

  it('moves with the arrows and keeps a single tab stop', async () => {
    render(<MyTutorings />);
    screen.getByRole('tab', { name: 'Próximas 2' }).focus();
    await userEvent.keyboard('{ArrowRight}');
    const history = screen.getByRole('tab', { name: 'Historial' });
    expect(history).toHaveFocus();
    expect(history).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: 'Próximas 2' })).toHaveAttribute('tabindex', '-1');
    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: 'Próximas 2' })).toHaveFocus();
  });
});
