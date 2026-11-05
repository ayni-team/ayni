import { describe, expect, it } from 'vitest';
import {
  confirmationText,
  donationError,
  doneText,
  earnedAvailable,
  missingText,
  poolText,
  refusalText,
} from './benefits';

describe('FE-33 benefits logic', () => {
  it('counts only earned credits', () => {
    expect(
      earnedAvailable({
        byType: [
          { type: 'PURCHASED', available: 9 },
          { type: 'EARNED', available: 2 },
        ],
      }),
    ).toBe(2);
    expect(earnedAvailable({ byType: [{ type: 'SEED', available: 5 }] })).toBe(0);
    expect(earnedAvailable(undefined)).toBe(0);
  });

  it('asks for a whole number of at least one', () => {
    expect(donationError('2')).toBeUndefined();
    expect(donationError('')).toBe('Escribe cuántos créditos quieres donar');
    expect(donationError('1.5')).toBe('Escribe un número entero de créditos');
    expect(donationError('0')).toBe('Dona al menos 1 crédito');
  });

  it('says how many are missing and that bought ones do not count', () => {
    expect(missingText(3, 1)).toBe('Te faltan 2 créditos ganados. Los comprados o asignados no cuentan.');
    expect(missingText(2, 1)).toBe('Te falta 1 crédito ganado. Los comprados o asignados no cuentan.');
  });

  it('tells being short of credits from a confirmation that ran out', () => {
    expect(refusalText(3, 1)).toContain('Te faltan 2');
    expect(refusalText(1, 3)).toBe('La confirmación venció. Vuelve a intentarlo.');
  });

  it('asks and thanks in its own words', () => {
    const benefit = { kind: 'REDEMPTION', benefitId: 'b', name: 'Impresiones', credits: 2 } as const;
    expect(confirmationText(benefit)).toBe(
      'Vas a canjear 2 créditos ganados por "Impresiones". Esto no se puede deshacer.',
    );
    expect(doneText(benefit)).toBe('Canjeaste "Impresiones"');
    expect(confirmationText({ kind: 'DONATION', credits: 1 })).toBe(
      'Vas a donar 1 crédito ganado a los estudiantes nuevos. Esto no se puede deshacer.',
    );
  });

  it('writes what the fund has', () => {
    expect(poolText({ credits: 12, contributions: 5 })).toBe('12 créditos de 5 aportes');
    expect(poolText({ credits: 1, contributions: 1 })).toBe('1 crédito de 1 aporte');
    expect(poolText(undefined)).toBe('0 créditos de 0 aportes');
  });
});
