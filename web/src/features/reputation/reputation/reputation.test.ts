import { describe, expect, it } from 'vitest';
import { averageView, bySignificance, countText, missingText, riskText, starsText } from './reputation';

describe('FE-43 reputation logic', () => {
  it('tells the official average, the provisional one and none at all apart', () => {
    expect(averageView({ averageStars: 4.5, ratingsCount: 8 })).toEqual({ kind: 'official', value: 4.5 });
    expect(
      averageView({ averageStars: null, provisionalAverage: 4, ratingsCount: 1, ratingsMissingForAverage: 2 }),
    ).toEqual({ kind: 'provisional', value: 4, missing: 2 });
    expect(averageView({ averageStars: null, provisionalAverage: null, ratingsCount: 0 })).toEqual({
      kind: 'none',
      missing: 3,
    });
  });

  it('writes what is missing in singular and plural', () => {
    expect(missingText(1)).toBe('Te falta 1 calificación para el promedio');
    expect(missingText(2)).toBe('Te faltan 2 calificaciones para el promedio');
    expect(countText(1, 'sesión', 'sesiones')).toBe('1 sesión');
    expect(countText(undefined, 'sesión', 'sesiones')).toBe('0 sesiones');
  });

  it('warns with the minimum of the answer, and without it too', () => {
    expect(starsText(4.46)).toBe('4.5');
    expect(riskText(3)).toBe('Tu promedio está por debajo de 3.0');
    expect(riskText(undefined)).toBe('Tu promedio está por debajo del mínimo');
  });

  it('puts what is at risk first, then what is taught the most, then by name', () => {
    const sorted = bySignificance([
      { name: 'B', sessionsTaught: 0 },
      { name: 'A', sessionsTaught: 0 },
      { name: 'C', sessionsTaught: 3 },
      { name: 'D', sessionsTaught: 1, atRisk: true },
    ]);
    expect(sorted.map((skill) => skill.name)).toEqual(['D', 'C', 'A', 'B']);
  });
});
