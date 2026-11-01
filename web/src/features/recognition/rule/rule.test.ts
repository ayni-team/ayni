import { describe, expect, it } from 'vitest';
import { dayText, hoursError, hoursText, savedText, startError } from './rule';

const TODAY = '2026-10-31';

describe('the hours of a rule', () => {
  it('are a whole number of at least one', () => {
    expect(hoursError('')).toBe('Escribe cuántas horas pide tu universidad');
    expect(hoursError('  ')).toBe('Escribe cuántas horas pide tu universidad');
    expect(hoursError('0')).toBe('Pide al menos 1 hora');
    expect(hoursError('2.5')).toBe('Escribe un número entero de horas');
    expect(hoursError('-3')).toBe('Escribe un número entero de horas');
    expect(hoursError('veinte')).toBe('Escribe un número entero de horas');
    expect(hoursError('1')).toBeUndefined();
    expect(hoursError(' 20 ')).toBeUndefined();
  });

  it('are written in the singular when it is one', () => {
    expect(hoursText(1)).toBe('1 hora');
    expect(hoursText(20)).toBe('20 horas');
    expect(hoursText(undefined)).toBe('0 horas');
  });
});

describe('the start day of a rule', () => {
  it('is optional and never in the past of the university', () => {
    expect(startError('', TODAY)).toBeUndefined();
    expect(startError(TODAY, TODAY)).toBeUndefined();
    expect(startError('2026-11-15', TODAY)).toBeUndefined();
    expect(startError('2026-10-30', TODAY)).toBe('Elige hoy o un día que no haya pasado');
    expect(startError('31/10/2026', TODAY)).toBe('Elige un día válido');
  });

  it('is written as a day of the calendar, which no zone moves', () => {
    expect(dayText('2026-10-20')).toBe('20 de octubre de 2026');
    expect(dayText(undefined)).toBe('');
    expect(dayText('nonsense')).toBe('');
  });
});

describe('once a rule is saved', () => {
  it('says what the university asks now, or from when when it starts later', () => {
    expect(savedText(20, TODAY, TODAY)).toBe('Tu universidad pide ahora 20 horas.');
    expect(savedText(1, undefined, TODAY)).toBe('Tu universidad pide ahora 1 hora.');
    expect(savedText(30, '2026-11-15', TODAY)).toBe(
      'Desde el 15 de noviembre de 2026 tu universidad pedirá 30 horas. Hasta entonces sigue la regla actual.',
    );
  });
});
