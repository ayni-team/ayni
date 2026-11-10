import { describe, expect, it } from 'vitest';
import {
  expiryLine,
  lastValidDay,
  expiryNotice,
  movementLink,
  nextExpiry,
  originViews,
  periodFrom,
  reasonText,
  signedAmount,
  startOfDay,
} from './wallet';

const LIMA = 'America/Lima';

describe('FE-30 wallet logic', () => {
  it('draws the four origins, and an absent one at zero', () => {
    const views = originViews({
      byType: [
        {
          type: 'SEED',
          available: 2,
          expires: true,
          countsTowardsRecognition: false,
          groups: [{ amount: 2, expiresAt: '2026-11-07T06:00:00Z' }],
        },
      ],
    });

    expect(views.map((view) => view.type)).toEqual(['ALLOCATED', 'EARNED', 'PURCHASED', 'SEED']);
    expect(views[3]).toMatchObject({ available: 2, expires: true, counts: false });
    expect(views[0]).toMatchObject({ available: 0, groups: [] });
    expect(views[1]).toMatchObject({ available: 0, counts: true });
  });

  it('finds what expires first, and nothing when no credit expires', () => {
    expect(
      nextExpiry({ byType: [{ type: 'EARNED', available: 3, expires: false, groups: [{ amount: 3 }] }] }),
    ).toBeNull();
    expect(nextExpiry({})).toBeNull();
    expect(
      nextExpiry({
        byType: [
          {
            type: 'ALLOCATED',
            available: 7,
            expires: true,
            groups: [{ amount: 4, expiresAt: '2026-12-15T05:00:00Z' }],
          },
          { type: 'SEED', available: 3, expires: true, groups: [{ amount: 3, expiresAt: '2026-10-31T05:00:00Z' }] },
        ],
      }),
    ).toEqual({ type: 'SEED', amount: 3, expiresAt: '2026-10-31T05:00:00Z' });
  });

  it('writes the last day the credits can be spent, in the zone of the university', () => {
    // Midnight in Lima is 05:00 UTC: the credits are good until the day before.
    expect(expiryLine({ amount: 3, expiresAt: '2026-10-31T05:00:00Z' }, LIMA)).toBe('3 vigentes hasta el 30/10');
    expect(expiryLine({ amount: 1, expiresAt: '2026-12-15T05:00:00Z' }, LIMA)).toBe('1 vigente hasta el 14/12');
    // Midnight in UTC is 19:00 of the day before in Lima: good until that evening.
    expect(expiryLine({ amount: 1, expiresAt: '2027-02-03T00:00:00Z' }, LIMA)).toBe('1 vigente hasta el 02/02');
    // Any other time of the day: good until that same day.
    expect(lastValidDay('2026-11-12T15:30:00Z', LIMA)).toBe('12/11');
    expect(expiryNotice({ type: 'ALLOCATED', amount: 3, expiresAt: '2026-10-31T05:00:00Z' }, LIMA, 'UPC')).toBe(
      '3 créditos asignados por la UPC están vigentes hasta el 30/10.',
    );
    expect(expiryNotice({ type: 'SEED', amount: 1, expiresAt: '2026-10-31T05:00:00Z' }, LIMA, 'UPC')).toBe(
      '1 crédito inicial está vigente hasta el 30/10.',
    );
  });

  it('turns a reason into Spanish and never shows the code', () => {
    expect(reasonText('BOOKING_CHARGE')).toBe('Reserva');
    expect(reasonText('SESSION_EARNING')).toBe('Ganaste enseñando');
    expect(reasonText(undefined)).toBe('Movimiento');
  });

  it('links a booking and a purchase, and nothing else', () => {
    expect(movementLink({ referenceType: 'BOOKING', referenceId: 'b1' })).toEqual({
      to: '/mis-tutorias',
      label: 'Ver tutoría',
    });
    expect(movementLink({ referenceType: 'PURCHASE', referenceId: 'p1' })).toEqual({
      to: '/creditos/compras/p1',
      label: 'Ver compra',
    });
    expect(movementLink({ referenceType: 'POLICY', referenceId: 'x' })).toBeNull();
    expect(movementLink({ referenceType: 'BOOKING', referenceId: null })).toBeNull();
  });

  it('signs a debit', () => {
    expect(signedAmount({ direction: 'DEBIT', amount: 2 })).toBe(-2);
    expect(signedAmount({ direction: 'CREDIT', amount: 2 })).toBe(2);
    expect(signedAmount({})).toBe(0);
  });

  it('filters by the first instant of the day of the university, as the API asks', () => {
    expect(startOfDay('2026-10-06', LIMA)).toBe('2026-10-06T05:00:00.000Z');
    expect(startOfDay('2026-10-06', 'UTC')).toBe('2026-10-06T00:00:00.000Z');
    expect(periodFrom('LAST_30', '2026-11-05', LIMA)).toBe('2026-10-06T05:00:00.000Z');
    expect(periodFrom('ALL', '2026-11-05', LIMA)).toBeUndefined();
  });
});
