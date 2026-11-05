import { describe, expect, it } from 'vitest';
import { commentOf, factsSummary, starsLabel, studentSummary, toggled } from './rating';

describe('FE-23 rating logic', () => {
  it('selects and takes back a tag', () => {
    expect(toggled(['CLEAR'], 'PATIENT')).toEqual(['CLEAR', 'PATIENT']);
    expect(toggled(['CLEAR', 'PATIENT'], 'CLEAR')).toEqual(['PATIENT']);
  });

  it('does not send an empty comment', () => {
    expect(commentOf('   ')).toBeUndefined();
    expect(commentOf(' bien ')).toBe('bien');
  });

  it('says what the student registered, with the tags in Spanish and an old one as it comes', () => {
    expect(starsLabel(1)).toBe('1 estrella');
    expect(studentSummary({ stars: 5, tags: ['CLEAR', 'Una vieja'] })).toBe(
      'Calificaste con 5 estrellas · Explica con claridad, Una vieja.',
    );
    expect(studentSummary({ stars: 4 })).toBe('Calificaste con 4 estrellas.');
  });

  it('says what the tutor answered, each fact either way', () => {
    expect(factsSummary({ wasPunctual: true, connectionOk: false, sessionFlowed: true })).toEqual([
      'Fue puntual',
      'La conexión falló',
      'La sesión fluyó',
    ]);
  });
});
