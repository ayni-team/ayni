import { describe, expect, it } from 'vitest';
import { amountText, movementLink, newestFirst, statusBadge, statusNote } from './purchases';

describe('FE-32 purchases logic', () => {
  it('writes each status in words, and an absent one too', () => {
    expect(statusBadge('CONFIRMED')).toEqual({ label: 'Confirmada', tone: 'success' });
    expect(statusBadge('FAILED').label).toBe('Rechazada');
    expect(statusBadge(undefined).label).toBe('Sin estado');
  });

  it('tells a pending purchase not to try again, and has nothing to add to a confirmed one', () => {
    expect(statusNote('PENDING')).toBe('Tu pago está en proceso. No vuelvas a intentarlo.');
    expect(statusNote('CONFIRMED')).toBeNull();
  });

  it('formats the amount, with a dash when absent', () => {
    expect(amountText(5, 'PEN')).toMatch(/5\.00/);
    expect(amountText(undefined, 'PEN')).toBe('—');
    expect(amountText(5, 'not a currency')).toBe('5.00 not a currency');
  });

  it('puts the newest first and an undated one last', () => {
    const sorted = newestFirst([
      { id: 'a', createdAt: '2026-10-01T00:00:00Z' },
      { id: 'b' },
      { id: 'c', createdAt: '2026-11-01T00:00:00Z' },
    ]);
    expect(sorted.map((purchase) => purchase.id)).toEqual(['c', 'a', 'b']);
  });

  it('links to the movement only when the purchase caused one', () => {
    expect(movementLink({ status: 'CONFIRMED' })).toBe('/creditos?motivo=PURCHASE');
    expect(movementLink({ status: 'PENDING' })).toBeNull();
    expect(movementLink({ status: 'FAILED' })).toBeNull();
  });
});
