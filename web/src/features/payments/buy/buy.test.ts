import { describe, expect, it } from 'vitest';
import { creditsError, outcomeOf, receiptLine } from './buy';

describe('FE-31 buy logic', () => {
  it('asks for a whole number of at least one', () => {
    expect(creditsError('3')).toBeUndefined();
    expect(creditsError(' 3 ')).toBeUndefined();
    expect(creditsError('')).toBe('Escribe cuántos créditos quieres');
    expect(creditsError('2.5')).toBe('Escribe un número entero de créditos');
    expect(creditsError('-1')).toBe('Escribe un número entero de créditos');
    expect(creditsError('0')).toBe('Compra al menos 1 crédito');
  });

  it('says what each status means, and which can be tried again', () => {
    expect(outcomeOf({ status: 'CONFIRMED', credits: 3 })).toMatchObject({
      title: 'Listo: se acreditaron 3 créditos',
      canRetry: false,
    });
    expect(outcomeOf({ status: 'CONFIRMED', credits: 1 }).title).toBe('Listo: se acreditó 1 crédito');
    expect(outcomeOf({ status: 'PENDING' })).toMatchObject({
      title: 'Tu pago está en proceso. No vuelvas a intentarlo',
      canRetry: false,
    });
    expect(outcomeOf({ status: 'FAILED' })).toMatchObject({ title: 'El pago fue rechazado', canRetry: true });
    expect(outcomeOf({ status: 'EXPIRED' })).toMatchObject({ title: 'El pago venció', canRetry: true });
  });

  it('never trusts an unknown status with a retry', () => {
    expect(outcomeOf({}).canRetry).toBe(false);
  });

  it('writes the reference and the date in the zone of the university', () => {
    expect(receiptLine({ providerReference: 'SIM-7F3A2C', createdAt: '2026-10-23T21:42:00Z' }, 'America/Lima')).toBe(
      'Referencia SIM-7F3A2C · 23 oct, 16:42',
    );
    expect(receiptLine({}, 'America/Lima')).toBe('');
  });
});
