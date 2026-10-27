import { describe, expect, it } from 'vitest';
import { RATING_TAGS, ratingTagLabel } from './ratingTags';

describe('rating tags', () => {
  it('are the five the guide decided, in Spanish', () => {
    expect(RATING_TAGS.map((tag) => tag.value)).toEqual(['CLEAR', 'HELPFUL', 'PATIENT', 'PUNCTUAL', 'PREPARED']);
    expect(ratingTagLabel('CLEAR')).toBe('Explica con claridad');
  });

  it('shows a tag it does not know as it comes', () => {
    expect(ratingTagLabel('Muy buena onda')).toBe('Muy buena onda');
  });
});
