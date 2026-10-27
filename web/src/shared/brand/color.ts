/**
 * The university's colours, derived from the two it declares (`primaryColor`, `secondaryColor`).
 *
 * - `primary`: as the university sets it. The logo bar, primary buttons, the active tab.
 * - `secondary`: the colour of text on the primary. White when the university sets none.
 * - `primarySoft`: the primary at 10 % over white, for selected badges and the selected day.
 * - `primaryPressed`: the primary darkened (80 % of each channel), for hover and press.
 * - `text`: the primary where it is text on the page's grey or on the soft tint. Pure UPC red reads
 *   4.27:1 on the grey and does not reach 4.5, so it is darkened (to the pressed tone first, then
 *   further) until it does on both. USIL's blue passes as it is.
 */

export interface BrandPalette {
  primary: string;
  secondary: string;
  primarySoft: string;
  primaryPressed: string;
  text: string;
}

/** The grey behind the cards (`bg-surface`). */
export const SURFACE = '#F6F6F9';
const WHITE = '#FFFFFF';
const BLACK = '#000000';

/** The minimum contrast of normal text (WCAG AA). */
export const MINIMUM_CONTRAST = 4.5;

/** Ayni's own colours, for a university that sets none and for whoever has no university. */
export const AYNI_COLORS = { primaryColor: '#0F766E', secondaryColor: WHITE } as const;

type Rgb = readonly [number, number, number];

/** `#abc`, `abc`, `#aabbcc` or `aabbcc` as `#AABBCC`; null when it is not a colour. */
export function normalizeHex(value: string | null | undefined): string | null {
  if (!value) return null;
  const hex = value.trim().replace(/^#/, '');
  if (/^[0-9a-f]{3}$/i.test(hex)) {
    return `#${hex
      .split('')
      .map((c) => c + c)
      .join('')
      .toUpperCase()}`;
  }
  return /^[0-9a-f]{6}$/i.test(hex) ? `#${hex.toUpperCase()}` : null;
}

function rgb(hex: string): Rgb {
  const normal = normalizeHex(hex);
  if (!normal) throw new RangeError(`Not a colour: ${hex}`);
  const n = Number.parseInt(normal.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function toHex([r, g, b]: Rgb): string {
  return `#${[r, g, b]
    .map((c) => Math.round(Math.min(255, Math.max(0, c))).toString(16).padStart(2, '0'))
    .join('')
    .toUpperCase()}`;
}

/** `weight` of `color` over `base`: `mix(red, white, 0.1)` is a 10 % tint of red. */
export function mix(color: string, base: string, weight: number): string {
  const a = rgb(color);
  const b = rgb(base);
  return toHex([0, 1, 2].map((i) => a[i] * weight + b[i] * (1 - weight)) as unknown as Rgb);
}

export function soften(primary: string): string {
  return mix(primary, WHITE, 0.1);
}

export function darken(color: string, amount = 0.2): string {
  return mix(color, BLACK, 1 - amount);
}

function channel(value: number): number {
  const c = value / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

export function relativeLuminance(hex: string): number {
  const [r, g, b] = rgb(hex);
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

/** WCAG contrast ratio between two colours, from 1 to 21. */
export function contrastRatio(a: string, b: string): number {
  const [light, dark] = [relativeLuminance(a), relativeLuminance(b)].sort((x, y) => y - x);
  return (light + 0.05) / (dark + 0.05);
}

function readsOn(color: string, backgrounds: readonly string[]): boolean {
  return backgrounds.every((background) => contrastRatio(color, background) >= MINIMUM_CONTRAST);
}

/** The primary, or the darkest it has to become, to read as text on every background. */
export function readableText(primary: string, backgrounds: readonly string[]): string {
  if (readsOn(primary, backgrounds)) return normalizeHex(primary) ?? primary;
  let candidate = darken(primary);
  while (!readsOn(candidate, backgrounds) && candidate !== BLACK) {
    candidate = darken(candidate, 0.1);
  }
  return candidate;
}

/** The text colour on the primary: the university's, or white or black, whichever reads better. */
export function onPrimary(primary: string, declared?: string | null): string {
  const secondary = normalizeHex(declared);
  if (secondary) return secondary;
  return contrastRatio(primary, WHITE) >= contrastRatio(primary, BLACK) ? WHITE : BLACK;
}

export function derivePalette(colors?: { primaryColor?: string | null; secondaryColor?: string | null } | null): BrandPalette {
  const declared = normalizeHex(colors?.primaryColor);
  const primary = declared ?? AYNI_COLORS.primaryColor;
  const secondary = declared ? onPrimary(primary, colors?.secondaryColor) : AYNI_COLORS.secondaryColor;
  const primarySoft = soften(primary);
  return {
    primary,
    secondary,
    primarySoft,
    primaryPressed: darken(primary),
    text: readableText(primary, [SURFACE, primarySoft]),
  };
}

/** The CSS variable of each colour; `tailwind.config.js` reads them as `brand.*`. */
export const CSS_VARIABLES: Record<keyof BrandPalette, string> = {
  primary: '--brand-primary',
  secondary: '--brand-secondary',
  primarySoft: '--brand-primary-soft',
  primaryPressed: '--brand-primary-pressed',
  text: '--brand-text',
};

export function applyPalette(palette: BrandPalette, root: HTMLElement = document.documentElement): void {
  for (const [name, variable] of Object.entries(CSS_VARIABLES) as [keyof BrandPalette, string][]) {
    root.style.setProperty(variable, palette[name]);
  }
}
