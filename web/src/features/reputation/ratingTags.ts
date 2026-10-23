/**
 * The tags a student can give a session (FE-23) and that the tutor's reputation counts (FE-43).
 * The server takes any text of up to 40 characters and publishes no list, so the web owns it
 * (docs/frontend-guide.md, "Rating tags"). A tag it does not know, from an older rating, is shown
 * as it comes.
 */
export const RATING_TAGS = [
  { value: 'CLEAR', label: 'Explica con claridad' },
  { value: 'HELPFUL', label: 'Me ayudó' },
  { value: 'PATIENT', label: 'Paciente' },
  { value: 'PUNCTUAL', label: 'Puntual' },
  { value: 'PREPARED', label: 'Preparado' },
] as const;

export type RatingTag = (typeof RATING_TAGS)[number]['value'];

export function ratingTagLabel(tag: string): string {
  return RATING_TAGS.find((known) => known.value === tag)?.label ?? tag;
}
