import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { WeekStepper } from './WeekStepper';

describe('WeekStepper', () => {
  it('moves a week back and forth and names its buttons', async () => {
    const user = userEvent.setup();
    const onPrevious = vi.fn();
    const onNext = vi.fn();
    render(<WeekStepper text="26 oct – 1 nov" onPrevious={onPrevious} onNext={onNext} />);

    expect(screen.getByRole('group', { name: 'Semana' })).toHaveTextContent('26 oct – 1 nov');
    await user.click(screen.getByRole('button', { name: 'Semana anterior' }));
    await user.click(screen.getByRole('button', { name: 'Semana siguiente' }));
    expect(onPrevious).toHaveBeenCalledOnce();
    expect(onNext).toHaveBeenCalledOnce();
  });

  it('can stop at an edge', () => {
    render(<WeekStepper text="26 oct – 1 nov" onPrevious={() => {}} onNext={() => {}} disablePrevious />);
    expect(screen.getByRole('button', { name: 'Semana anterior' })).toBeDisabled();
  });
});
