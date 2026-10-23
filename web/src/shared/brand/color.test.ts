import { describe, expect, it } from 'vitest';
import { applyPalette, AYNI_COLORS, contrastRatio, derivePalette, MINIMUM_CONTRAST, normalizeHex, SURFACE } from './color';

describe('derivePalette', () => {
  it('derives UPC: its red does not read on grey, so its text is the pressed tone', () => {
    const upc = derivePalette({ primaryColor: '#E42322', secondaryColor: '#FFFFFF' });
    expect(upc).toEqual({
      primary: '#E42322',
      secondary: '#FFFFFF',
      primarySoft: '#FCE9E9',
      primaryPressed: '#B61C1B',
      text: '#B61C1B',
    });
    expect(contrastRatio('#E42322', SURFACE)).toBeLessThan(MINIMUM_CONTRAST);
    expect(contrastRatio(upc.text, SURFACE)).toBeGreaterThanOrEqual(MINIMUM_CONTRAST);
    expect(contrastRatio(upc.text, upc.primarySoft)).toBeGreaterThanOrEqual(MINIMUM_CONTRAST);
  });

  it('derives USIL: its blue reads as it is', () => {
    const usil = derivePalette({ primaryColor: '#07256D', secondaryColor: '#FFFFFF' });
    expect(usil.primarySoft).toBe('#E6E9F0');
    expect(usil.text).toBe('#07256D');
    expect(contrastRatio(usil.primaryPressed, '#000000')).toBeLessThan(contrastRatio(usil.primary, '#000000'));
  });

  it('darkens further than the pressed tone when even that does not read', () => {
    const yellow = derivePalette({ primaryColor: '#FFD500' });
    expect(contrastRatio(yellow.text, SURFACE)).toBeGreaterThanOrEqual(MINIMUM_CONTRAST);
    expect(contrastRatio(yellow.text, yellow.primarySoft)).toBeGreaterThanOrEqual(MINIMUM_CONTRAST);
  });

  it('gives Ayni colours to a university that sets none', () => {
    expect(derivePalette(null).primary).toBe(AYNI_COLORS.primaryColor);
    expect(derivePalette({ primaryColor: 'rojo' }).primary).toBe(AYNI_COLORS.primaryColor);
  });

  it('writes on the primary in white or black when the university sets no secondary', () => {
    expect(derivePalette({ primaryColor: '#07256D' }).secondary).toBe('#FFFFFF');
    expect(derivePalette({ primaryColor: '#FFD500' }).secondary).toBe('#000000');
  });
});

describe('colours', () => {
  it('normalizes what the university declares', () => {
    expect(normalizeHex('e42322')).toBe('#E42322');
    expect(normalizeHex('#abc')).toBe('#AABBCC');
    expect(normalizeHex('#12345')).toBeNull();
  });

  it('sets the CSS variables Tailwind reads', () => {
    applyPalette(derivePalette({ primaryColor: '#E42322' }));
    const style = document.documentElement.style;
    expect(style.getPropertyValue('--brand-primary')).toBe('#E42322');
    expect(style.getPropertyValue('--brand-secondary')).toBe('#FFFFFF');
    expect(style.getPropertyValue('--brand-primary-soft')).toBe('#FCE9E9');
    expect(style.getPropertyValue('--brand-primary-pressed')).toBe('#B61C1B');
    expect(style.getPropertyValue('--brand-text')).toBe('#B61C1B');
  });
});
